# TinaIDE Rsync Builder with 16KB Page Alignment
# 从 rsync 源码编译支持 16KB 页面对齐的 Android 版本
#
# 特性：
# - 支持 16KB 页面对齐（Android 15+）
# - 多架构支持（arm64-v8a, armeabi-v7a, x86_64, x86）
# - 源码持久化（挂载 volume，避免重复克隆）

FROM ubuntu:24.04

# 设置非交互模式
ENV DEBIAN_FRONTEND=noninteractive

# 使用清华大学镜像源（中国大陆加速）
RUN set -e; \
    file=/etc/apt/sources.list.d/ubuntu.sources; \
    sed -i 's|URIs: http://archive.ubuntu.com/ubuntu/|URIs: http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' "$file"; \
    sed -i 's|URIs: http://security.ubuntu.com/ubuntu/|URIs: http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' "$file"

# ============================================
# 第一层：安装最基础的工具
# ============================================
RUN set -e; \
    file=/etc/apt/sources.list.d/ubuntu.sources; \
    write_official_sources() { \
      printf '%s\n' \
        'Types: deb' \
        'URIs: http://archive.ubuntu.com/ubuntu/' \
        'Suites: noble noble-updates noble-backports' \
        'Components: main universe restricted multiverse' \
        'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' \
        '' \
        'Types: deb' \
        'URIs: http://security.ubuntu.com/ubuntu/' \
        'Suites: noble-security' \
        'Components: main universe restricted multiverse' \
        'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' \
        > "$file"; \
    }; \
    apt_get_update() { apt-get -o Acquire::Retries=5 update; }; \
    apt_get_install() { apt-get -o Acquire::Retries=5 install -y --no-install-recommends "$@"; }; \
    apt_get_update || ( \
      echo "[WARN] apt update failed, fallback to official mirror..."; \
      write_official_sources; \
      apt_get_update \
    ); \
    apt_get_install ca-certificates curl unzip || ( \
      echo "[WARN] apt install failed, retry with official mirror..."; \
      write_official_sources; \
      apt_get_update; \
      apt_get_install ca-certificates curl unzip \
    ); \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# ============================================
# 第二层：下载 Android NDK
# ============================================
ARG NDK_VERSION=r27c
ARG NDK_PLATFORM=linux
ENV ANDROID_NDK_ROOT=/opt/android-ndk
ENV ANDROID_NDK_HOME=/opt/android-ndk

RUN mkdir -p /opt && cd /opt && \
    echo "[INFO] 下载 Android NDK ${NDK_VERSION}..." && \
    echo "[INFO] 使用阿里云镜像..." && \
    ( \
      set -e; \
      download() { \
        url="$1"; \
        echo "[INFO] 下载: $url"; \
        i=0; \
        while [ $i -lt 5 ]; do \
          i=$((i+1)); \
          rm -f ndk.zip; \
          if curl -fL --connect-timeout 30 --max-time 1200 --retry 3 --retry-delay 2 --retry-all-errors -o ndk.zip "$url"; then \
            return 0; \
          fi; \
          echo "[WARN] 下载失败，重试 $i/5 ..."; \
          sleep 2; \
        done; \
        return 1; \
      }; \
      download "https://mirrors.aliyun.com/android/repository/android-ndk-${NDK_VERSION}-${NDK_PLATFORM}.zip" || \
      (echo "[WARN] 阿里云失败，使用官方源..." && download "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-${NDK_PLATFORM}.zip"); \
    ) && \
    echo "[INFO] 解压 NDK..." && \
    unzip -q ndk.zip && \
    mv android-ndk-* android-ndk && \
    rm ndk.zip && \
    echo "[OK] NDK 安装完成"

# ============================================
# 第三层：安装构建依赖
# ============================================
RUN set -e; \
    file=/etc/apt/sources.list.d/ubuntu.sources; \
    write_official_sources() { \
      printf '%s\n' \
        'Types: deb' \
        'URIs: http://archive.ubuntu.com/ubuntu/' \
        'Suites: noble noble-updates noble-backports' \
        'Components: main universe restricted multiverse' \
        'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' \
        '' \
        'Types: deb' \
        'URIs: http://security.ubuntu.com/ubuntu/' \
        'Suites: noble-security' \
        'Components: main universe restricted multiverse' \
        'Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg' \
        > "$file"; \
    }; \
    apt_get_update() { apt-get -o Acquire::Retries=5 update; }; \
    apt_get_install() { apt-get -o Acquire::Retries=5 install -y --no-install-recommends "$@"; }; \
    apt_get_update || ( \
      echo "[WARN] apt update failed, fallback to official mirror..."; \
      write_official_sources; \
      apt_get_update \
    ); \
    apt_get_install \
    git \
    build-essential \
    autoconf \
    automake \
    file \
    python3 \
    || ( \
      echo "[WARN] apt install failed, retry with official mirror..."; \
      write_official_sources; \
      apt_get_update; \
      apt_get_install git build-essential autoconf automake file python3 \
    ); \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# 配置 Git
RUN git config --global http.sslVerify false && \
    git config --global credential.helper store

# ============================================
# 第四层：设置工作目录和复制脚本
# ============================================
WORKDIR /build

# 创建目录结构
RUN mkdir -p /build/src /build/scripts

# 复制构建脚本
COPY build-rsync.sh /build/scripts/build-rsync.sh
RUN chmod +x /build/scripts/*.sh

# 架构参数
ARG TARGET_ARCH=aarch64-linux-android
ENV TARGET_ARCH=${TARGET_ARCH}

# 源码目录作为 volume（持久化）
VOLUME ["/build/src"]

# 输出目录
VOLUME ["/output"]

# 入口脚本
CMD ["/bin/bash", "/build/scripts/build-rsync.sh"]
