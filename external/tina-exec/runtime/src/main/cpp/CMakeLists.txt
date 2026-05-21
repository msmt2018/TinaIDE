cmake_minimum_required(VERSION 3.22.1)

project(tina_exec_runtime C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS ON)

# Android 15+ 的 16KB 页面设备要求 ELF LOAD 段按 16KB 边界对齐。
# 这里使用全局 shared linker flags，确保 AGP/NDK 生成的实际 LINK_FLAGS 能带上该参数。
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

set(TINA_EXEC_VENDOR_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../vendor)
set(TINA_EXEC_CORE_DIR ${TINA_EXEC_VENDOR_DIR}/termux-core_nos_c/tre)
set(TINA_EXEC_LIB_DIR ${TINA_EXEC_VENDOR_DIR}/termux-exec_nos_c/tre)
set(TINA_EXEC_APP_PACKAGE "com.wuxianggujun.tinaide")
set(TINA_EXEC_APP_DATA_DIR "/data/user/0/${TINA_EXEC_APP_PACKAGE}")
set(TINA_EXEC_ROOTFS_DIR "${TINA_EXEC_APP_DATA_DIR}/files")
set(TINA_EXEC_HOME_DIR "${TINA_EXEC_ROOTFS_DIR}/home")
set(TINA_EXEC_PREFIX_DIR "${TINA_EXEC_ROOTFS_DIR}/usr")
set(TINA_EXEC_PREFIX_INCLUDE_DIR "${TINA_EXEC_PREFIX_DIR}/include")
set(TINA_EXEC_PREFIX_LIB_DIR "${TINA_EXEC_PREFIX_DIR}/lib")
set(TINA_EXEC_PREFIX_TMP_DIR "${TINA_EXEC_PREFIX_DIR}/tmp")

set(TINA_EXEC_CORE_SOURCES
    ${TINA_EXEC_CORE_DIR}/src/TermuxCoreLibraryConfig.c
    ${TINA_EXEC_CORE_DIR}/src/android/shell/command/environment/AndroidShellEnvironment.c
    ${TINA_EXEC_CORE_DIR}/src/data/AssertUtils.c
    ${TINA_EXEC_CORE_DIR}/src/data/DataUtils.c
    ${TINA_EXEC_CORE_DIR}/src/logger/FileLoggerImpl.c
    ${TINA_EXEC_CORE_DIR}/src/logger/Logger.c
    ${TINA_EXEC_CORE_DIR}/src/logger/StandardLoggerImpl.c
    ${TINA_EXEC_CORE_DIR}/src/security/SecurityUtils.c
    ${TINA_EXEC_CORE_DIR}/src/termux/file/TermuxFile.c
    ${TINA_EXEC_CORE_DIR}/src/termux/shell/command/environment/TermuxShellEnvironment.c
    ${TINA_EXEC_CORE_DIR}/src/termux/shell/command/environment/termux_core/TermuxCoreShellEnvironment.c
    ${TINA_EXEC_CORE_DIR}/src/unix/file/Canonicalize.c
    ${TINA_EXEC_CORE_DIR}/src/unix/file/UnixFileUtils.c
    ${TINA_EXEC_CORE_DIR}/src/unix/os/process/UnixForkUtils.c
    ${TINA_EXEC_CORE_DIR}/src/unix/os/process/UnixSafeStrerror.c
    ${TINA_EXEC_CORE_DIR}/src/unix/os/process/UnixSignalUtils.c
    ${TINA_EXEC_CORE_DIR}/src/unix/os/selinux/UnixSeLinuxUtils.c
    ${TINA_EXEC_CORE_DIR}/src/unix/shell/command/environment/UnixShellEnvironment.c
)

set(TINA_EXEC_COMMON_SOURCES
    ${TINA_EXEC_LIB_DIR}/src/TermuxExecLibraryConfig.c
    ${TINA_EXEC_LIB_DIR}/src/termux/api/termux_exec/service/ld_preload/TermuxExecLDPreload.c
    ${TINA_EXEC_LIB_DIR}/src/termux/api/termux_exec/service/ld_preload/direct/exec/ExecIntercept.c
    ${TINA_EXEC_LIB_DIR}/src/termux/api/termux_exec/service/ld_preload/direct/exec/ExecVariantsIntercept.c
    ${TINA_EXEC_LIB_DIR}/src/termux/os/process/termux_exec/TermuxExecProcess.c
    ${TINA_EXEC_LIB_DIR}/src/termux/shell/command/environment/termux_exec/TermuxExecShellEnvironment.c
)

set(TINA_EXEC_COMMON_DEFINITIONS
    TERMUX_CORE_PKG__VERSION=\"2.0.0-tina\"
    TERMUX_EXEC_PKG__VERSION=\"2.4.0-tina\"
    TERMUX__NAME=\"TinaIDE\"
    TERMUX__LNAME=\"tinaide\"
    TERMUX__REPOS_HOST_ORG_NAME=\"wuxianggujun\"
    TERMUX_APP__NAME=\"TinaIDE\"
    TERMUX_APP__PACKAGE_NAME=\"${TINA_EXEC_APP_PACKAGE}\"
    TERMUX_APP__DATA_DIR=\"${TINA_EXEC_APP_DATA_DIR}\"
    TERMUX__ROOTFS=\"${TINA_EXEC_ROOTFS_DIR}\"
    TERMUX__HOME=\"${TINA_EXEC_HOME_DIR}\"
    TERMUX__PREFIX=\"${TINA_EXEC_PREFIX_DIR}\"
    TERMUX__PREFIX__BIN_DIR=\"${TINA_EXEC_PREFIX_DIR}/bin\"
    TERMUX__PREFIX__INCLUDE_DIR=\"${TINA_EXEC_PREFIX_INCLUDE_DIR}\"
    TERMUX__PREFIX__LIB_DIR=\"${TINA_EXEC_PREFIX_LIB_DIR}\"
    TERMUX__PREFIX__TMP_DIR=\"${TINA_EXEC_PREFIX_TMP_DIR}\"
    TERMUX_ENV__S_ROOT=\"TINA_\"
    TERMUX_ENV__SS_TERMUX=\"\"
    TERMUX_ENV__S_TERMUX=\"TINA_\"
    TERMUX_ENV__SS_TERMUX_APP=\"APP__\"
    TERMUX_ENV__S_TERMUX_APP=\"TINA_APP__\"
    TERMUX_ENV__SS_TERMUX_ROOTFS=\"ROOTFS__\"
    TERMUX_ENV__S_TERMUX_ROOTFS=\"TINA_ROOTFS__\"
    TERMUX_ENV__SS_TERMUX_CORE=\"CORE__\"
    TERMUX_ENV__S_TERMUX_CORE=\"TINA_CORE__\"
    TERMUX_ENV__SS_TERMUX_CORE__TESTS=\"CORE__TESTS__\"
    TERMUX_ENV__S_TERMUX_CORE__TESTS=\"TINA_CORE__TESTS__\"
    TERMUX_ENV__SS_TERMUX_EXEC=\"EXEC__\"
    TERMUX_ENV__S_TERMUX_EXEC=\"TINA_EXEC__\"
    TERMUX_ENV__SS_TERMUX_EXEC__TESTS=\"EXEC__TESTS__\"
    TERMUX_ENV__S_TERMUX_EXEC__TESTS=\"TINA_EXEC__TESTS__\"
)

add_library(tina_exec_vendor_config INTERFACE)
target_include_directories(tina_exec_vendor_config INTERFACE
    ${TINA_EXEC_CORE_DIR}/include
    ${TINA_EXEC_LIB_DIR}/include
)
target_compile_definitions(tina_exec_vendor_config INTERFACE ${TINA_EXEC_COMMON_DEFINITIONS})
target_compile_options(tina_exec_vendor_config INTERFACE
    -Wall
    -Wextra
    -Werror
    -Wshadow
    -O2
    -fstack-protector-strong
)

add_library(tina_exec_core_nos_c STATIC ${TINA_EXEC_CORE_SOURCES})
target_link_libraries(tina_exec_core_nos_c PRIVATE tina_exec_vendor_config)
set_target_properties(tina_exec_core_nos_c PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(tina_exec_common_nos_c STATIC ${TINA_EXEC_COMMON_SOURCES})
target_link_libraries(tina_exec_common_nos_c PRIVATE tina_exec_vendor_config tina_exec_core_nos_c)
set_target_properties(tina_exec_common_nos_c PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(tina_exec_direct_ld_preload SHARED
    ${CMAKE_CURRENT_LIST_DIR}/entrypoints/TinaExecDirectLdPreloadEntryPoint.c
)
target_link_libraries(tina_exec_direct_ld_preload
    PRIVATE
    tina_exec_vendor_config
    tina_exec_common_nos_c
    tina_exec_core_nos_c
)
set_target_properties(tina_exec_direct_ld_preload PROPERTIES
    OUTPUT_NAME "tina_exec_direct_ld_preload"
)

add_library(tina_exec_linker_ld_preload SHARED
    ${CMAKE_CURRENT_LIST_DIR}/entrypoints/TinaExecLinkerLdPreloadEntryPoint.c
)
target_link_libraries(tina_exec_linker_ld_preload
    PRIVATE
    tina_exec_vendor_config
    tina_exec_common_nos_c
    tina_exec_core_nos_c
)
set_target_properties(tina_exec_linker_ld_preload PROPERTIES
    OUTPUT_NAME "tina_exec_linker_ld_preload"
)
