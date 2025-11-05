/**
 * ELF Patcher 实现
 * 
 * 核心功能：
 * 1. 解析 ELF 文件结构
 * 2. 在文件末尾添加新的 PT_LOAD 段存储新路径
 * 3. 扫描代码段中的 adr/adrp+add 指令
 * 4. 修复指令使其指向新路径
 */

#include "elf-patcher.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <android/log.h>

#define TAG "ELFPatcher"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ARM64 指令掩码
#define ARM64_ADRP_MASK  0x9F000000
#define ARM64_ADRP_INST  0x90000000
#define ARM64_ADD_MASK   0xFFC00000
#define ARM64_ADD_INST   0x91000000

/**
 * 检查是否为 ADRP 指令
 */
static inline int is_adrp(uint32_t inst) {
    return (inst & ARM64_ADRP_MASK) == ARM64_ADRP_INST;
}

/**
 * 检查是否为 ADD 指令（立即数）
 */
static inline int is_add_imm(uint32_t inst) {
    return (inst & ARM64_ADD_MASK) == ARM64_ADD_INST;
}

/**
 * 从 ADRP 指令中提取目标寄存器
 */
static inline int get_adrp_rd(uint32_t inst) {
    return inst & 0x1F;
}

/**
 * 从 ADD 指令中提取寄存器
 */
static inline int get_add_rd(uint32_t inst) {
    return inst & 0x1F;
}

static inline int get_add_rn(uint32_t inst) {
    return (inst >> 5) & 0x1F;
}

/**
 * 计算 ADRP 指令的目标地址
 */
static uint64_t calc_adrp_target(uint64_t pc, uint32_t inst) {
    // ADRP: immhi[23:5] | immlo[30:29]
    int64_t immhi = (inst >> 5) & 0x7FFFF;
    int64_t immlo = (inst >> 29) & 0x3;
    int64_t imm = (immhi << 2) | immlo;
    
    // 符号扩展
    if (imm & 0x100000) {
        imm |= ~0x1FFFFFLL;
    }
    
    // ADRP 计算页地址（4KB 对齐）
    uint64_t page_base = pc & ~0xFFFULL;
    return page_base + (imm << 12);
}

/**
 * 从 ADD 指令中提取立即数
 */
static uint32_t get_add_imm(uint32_t inst) {
    return (inst >> 10) & 0xFFF;
}

/**
 * 构造新的 ADRP 指令
 */
static uint32_t make_adrp(int rd, int64_t offset) {
    // offset 应该是页偏移（12位对齐）
    offset >>= 12;
    uint32_t immlo = offset & 0x3;
    uint32_t immhi = (offset >> 2) & 0x7FFFF;
    return ARM64_ADRP_INST | (immlo << 29) | (immhi << 5) | rd;
}

/**
 * 构造新的 ADD 指令
 */
static uint32_t make_add_imm(int rd, int rn, uint32_t imm) {
    return ARM64_ADD_INST | (imm << 10) | (rn << 5) | rd;
}

/**
 * 修复 ARM64 字符串引用
 */
int fix_arm64_string_refs(uint8_t* code_base, size_t code_size,
                          uint64_t old_str_addr, uint64_t new_str_addr) {
    int fixed_count = 0;
    uint32_t* code = (uint32_t*)code_base;
    size_t inst_count = code_size / 4;
    
    for (size_t i = 0; i < inst_count - 1; i++) {
        uint32_t inst1 = code[i];
        uint32_t inst2 = code[i + 1];
        
        // 查找 ADRP + ADD 序列
        if (!is_adrp(inst1) || !is_add_imm(inst2)) {
            continue;
        }
        
        int rd1 = get_adrp_rd(inst1);
        int rd2 = get_add_rd(inst2);
        int rn2 = get_add_rn(inst2);
        
        // 检查寄存器是否匹配
        if (rd1 != rn2 || rd1 != rd2) {
            continue;
        }
        
        // 计算当前指向的地址
        uint64_t pc = (uint64_t)&code[i];
        uint64_t page_addr = calc_adrp_target(pc, inst1);
        uint32_t page_off = get_add_imm(inst2);
        uint64_t target_addr = page_addr + page_off;
        
        // 检查是否指向旧字符串
        if (target_addr != old_str_addr) {
            continue;
        }
        
        LOGD("Found string ref at offset 0x%zx -> 0x%llx", 
             i * 4, (unsigned long long)target_addr);
        
        // 计算新的偏移
        int64_t new_offset = (int64_t)new_str_addr - (int64_t)pc;
        uint64_t new_page_addr = new_str_addr & ~0xFFFULL;
        uint32_t new_page_off = new_str_addr & 0xFFF;
        
        // 构造新指令
        code[i] = make_adrp(rd1, new_offset);
        code[i + 1] = make_add_imm(rd2, rn2, new_page_off);
        
        fixed_count++;
        LOGD("Fixed instruction pair at 0x%zx", i * 4);
    }
    
    return fixed_count;
}

/**
 * 添加 PT_LOAD 段（简化版本）
 * 注意：完整实现需要重新组织 ELF 文件结构
 */
int add_pt_load_segment(int fd, const char* new_string, uint64_t* new_addr) {
    // 获取文件大小
    struct stat st;
    if (fstat(fd, &st) < 0) {
        LOGE("fstat failed");
        return -1;
    }
    
    size_t file_size = st.st_size;
    
    // 读取 ELF 头
    Elf64_Ehdr ehdr;
    if (pread(fd, &ehdr, sizeof(ehdr), 0) != sizeof(ehdr)) {
        LOGE("Failed to read ELF header");
        return -1;
    }
    
    // 检查是否为 64 位 ELF
    if (ehdr.e_ident[EI_CLASS] != ELFCLASS64) {
        LOGE("Only 64-bit ELF supported");
        return -1;
    }
    
    // 读取程序头表
    size_t phdr_size = ehdr.e_phnum * sizeof(Elf64_Phdr);
    Elf64_Phdr* phdrs = malloc(phdr_size);
    if (!phdrs) {
        LOGE("malloc failed");
        return -1;
    }
    
    if (pread(fd, phdrs, phdr_size, ehdr.e_phoff) != phdr_size) {
        LOGE("Failed to read program headers");
        free(phdrs);
        return -1;
    }
    
    // 找到最后一个 PT_LOAD 段
    Elf64_Phdr* last_load = NULL;
    for (int i = 0; i < ehdr.e_phnum; i++) {
        if (phdrs[i].p_type == PT_LOAD) {
            last_load = &phdrs[i];
        }
    }
    
    if (!last_load) {
        LOGE("No PT_LOAD segment found");
        free(phdrs);
        return -1;
    }
    
    // 计算新段的地址（页对齐）
    uint64_t new_vaddr = (last_load->p_vaddr + last_load->p_memsz + 0xFFF) & ~0xFFFULL;
    size_t str_len = strlen(new_string) + 1;
    size_t aligned_size = (str_len + 0xFFF) & ~0xFFF;
    
    // 在文件末尾写入新字符串
    if (lseek(fd, file_size, SEEK_SET) < 0) {
        LOGE("lseek failed");
        free(phdrs);
        return -1;
    }
    
    // 写入字符串（填充到页大小）
    char* buf = calloc(1, aligned_size);
    if (!buf) {
        LOGE("calloc failed");
        free(phdrs);
        return -1;
    }
    
    strcpy(buf, new_string);
    if (write(fd, buf, aligned_size) != aligned_size) {
        LOGE("write failed");
        free(buf);
        free(phdrs);
        return -1;
    }
    free(buf);
    
    // TODO: 完整实现需要：
    // 1. 扩展程序头表
    // 2. 添加新的 PT_LOAD 段头
    // 3. 更新 ELF 头中的段数量
    
    *new_addr = new_vaddr;
    free(phdrs);
    
    LOGD("Added new segment at vaddr 0x%llx", (unsigned long long)new_vaddr);
    return 0;
}

/**
 * 修改 ELF 文件（简化版本：原地替换）
 */
int patch_elf_file(const char* path, const char* old_prefix, const char* new_prefix) {
    int fd = open(path, O_RDWR);
    if (fd < 0) {
        LOGE("Failed to open file: %s", path);
        return -1;
    }
    
    // 映射文件到内存
    struct stat st;
    if (fstat(fd, &st) < 0) {
        LOGE("fstat failed");
        close(fd);
        return -1;
    }
    
    void* map = mmap(NULL, st.st_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (map == MAP_FAILED) {
        LOGE("mmap failed");
        close(fd);
        return -1;
    }
    
    // 查找旧字符串的位置
    void* old_str_ptr = memmem(map, st.st_size, old_prefix, strlen(old_prefix));
    if (!old_str_ptr) {
        LOGD("Old prefix not found in file");
        munmap(map, st.st_size);
        close(fd);
        return 0;
    }
    
    uint64_t old_str_offset = (uint8_t*)old_str_ptr - (uint8_t*)map;
    LOGD("Found old prefix at file offset 0x%llx", (unsigned long long)old_str_offset);
    
    // 简化实现：如果新路径不长于旧路径，直接原地替换
    size_t old_len = strlen(old_prefix);
    size_t new_len = strlen(new_prefix);
    
    if (new_len <= old_len) {
        // 原地替换
        memcpy(old_str_ptr, new_prefix, new_len);
        // 填充剩余空间为 0
        if (new_len < old_len) {
            memset((char*)old_str_ptr + new_len, 0, old_len - new_len);
        }
        LOGD("Replaced string in-place");
        
        // 同步到磁盘
        msync(map, st.st_size, MS_SYNC);
    } else {
        LOGE("New prefix too long for in-place replacement (%zu > %zu)", new_len, old_len);
        LOGE("Full PT_LOAD implementation needed");
        // TODO: 实现完整的 PT_LOAD 添加方案
        munmap(map, st.st_size);
        close(fd);
        return -1;
    }
    
    munmap(map, st.st_size);
    close(fd);
    return 0;
}
