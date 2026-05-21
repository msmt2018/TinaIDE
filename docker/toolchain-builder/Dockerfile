FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive
ARG NDK_VERSION=r27
ARG CMAKE_VERSION=3.31.6

RUN apt-get update && apt-get install -y \
    build-essential ninja-build ccache \
    python3 python3-pip \
    git wget curl ca-certificates unzip \
    libxml2-dev zlib1g-dev \
    libedit-dev libncurses5-dev \
    libzstd-dev \
    zstd xz-utils file pkg-config patch \
    && rm -rf /var/lib/apt/lists/*

# CMake 3.31.6 (Ubuntu 22.04 ships 3.22 which has CheckSymbolExists bug with Android NDK toolchain)
RUN wget -q "https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-linux-x86_64.tar.gz" \
    && tar -xzf "cmake-${CMAKE_VERSION}-linux-x86_64.tar.gz" -C /opt \
    && ln -sf "/opt/cmake-${CMAKE_VERSION}-linux-x86_64/bin/cmake" /usr/local/bin/cmake \
    && ln -sf "/opt/cmake-${CMAKE_VERSION}-linux-x86_64/bin/ctest" /usr/local/bin/ctest \
    && ln -sf "/opt/cmake-${CMAKE_VERSION}-linux-x86_64/bin/cpack" /usr/local/bin/cpack \
    && rm "cmake-${CMAKE_VERSION}-linux-x86_64.tar.gz"

# Android NDK
RUN wget -q "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip" \
    && unzip -q "android-ndk-${NDK_VERSION}-linux.zip" -d /opt \
    && rm "android-ndk-${NDK_VERSION}-linux.zip"

ENV ANDROID_NDK_HOME=/opt/android-ndk-${NDK_VERSION}
ENV PATH="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin:${PATH}"

WORKDIR /workspace
