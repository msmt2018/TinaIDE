/**
 * ELF Patcher - ELF 文件修改器
 * 
 * 负责修改 ELF 文件中的硬编码路径字符串
 */

#ifndef TERMUX_ELF_PATCHER_H
#define TERMUX_ELF_PATCHER_H

#include <stdint.h>
#include <elf.h>

/**
 * 修改 ELF 文件中的路径字符串
 * 
 * @param path ELF 文件路径
 * @param old_prefix 旧的 prefix 路径
 * @param new_prefix 新的 prefix 路径
 * @return 0 成功，-1 失败
 */
int patch_elf_file(const char* path, const char* old_prefix, const char* new_prefix);

/**
 * ARM64 指令修复
 * 修复 adr/adrp + add 指令序列，使其指向新的字符串地址
 * 
 * @param code_base 代码段基址
 * @param code_size 代码段大小
 * @param old_str_addr 旧字符串地址
 * @param new_str_addr 新字符串地址
 * @return 修复的指令数量
 */
int fix_arm64_string_refs(uint8_t* code_base, size_t code_size, 
                          uint64_t old_str_addr, uint64_t new_str_addr);

/**
 * 在 ELF 文件末尾添加新的 PT_LOAD 段
 * 
 * @param fd 文件描述符
 * @param new_string 新字符串
 * @param new_addr 返回新字符串的虚拟地址
 * @return 0 成功，-1 失败
 */
int add_pt_load_segment(int fd, const char* new_string, uint64_t* new_addr);

#endif // TERMUX_ELF_PATCHER_H
