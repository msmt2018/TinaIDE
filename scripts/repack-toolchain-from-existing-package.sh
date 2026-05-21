#!/usr/bin/env bash
set -euo pipefail

# Fast repack workflow:
# 1) Optionally rebuild selected target binaries with ninja.
# 2) Extract an existing full toolchain package.
# 3) Replace selected ELF binaries (and optional clang resource).
# 4) Repack to new archive(s).

log() { echo "[INFO] $*"; }
die() { echo "[ERROR] $*" >&2; exit 1; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || die "missing command: $1"; }

resolve_ndk_runtime_dir() {
  local ndk_base=""
  local ndk_clang_ver=""
  local ndk_rt_dir=""

  if [[ -n "${ANDROID_NDK_HOME}" ]] && [[ -d "${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/lib/clang" ]]; then
    ndk_base="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/lib/clang"
  elif [[ -d "/opt/android-ndk-r27/toolchains/llvm/prebuilt/linux-x86_64/lib/clang" ]]; then
    ndk_base="/opt/android-ndk-r27/toolchains/llvm/prebuilt/linux-x86_64/lib/clang"
  fi

  if [[ -n "${ndk_base}" ]]; then
    ndk_clang_ver="$(ls -1 "${ndk_base}" | sort -V | tail -n 1 || true)"
    if [[ -n "${ndk_clang_ver}" ]]; then
      ndk_rt_dir="${ndk_base}/${ndk_clang_ver}/lib/linux"
      [[ -d "${ndk_rt_dir}" ]] || ndk_rt_dir=""
    fi
  fi

  echo "${ndk_rt_dir}"
}

ensure_clang_runtimes() {
  local pkg_root="$1"
  local clang_base="${pkg_root}/lib/clang"
  local clang_ver=""
  local rt_arch=""
  local sysroot_arch=""
  local rt_dir=""
  local arch_rt_dir=""
  local builtins_android=""
  local ndk_rt_dir=""
  local api_levels=()
  local legacy_dir=""
  local api=""

  [[ -d "${clang_base}" ]] || die "clang resource missing in package: ${clang_base}"
  clang_ver="$(ls -1 "${clang_base}" | sort -V | tail -n 1 || true)"
  [[ -n "${clang_ver}" ]] || die "cannot determine clang resource version under: ${clang_base}"

  case "${ARCH}" in
    aarch64)
      rt_arch="aarch64"
      sysroot_arch="aarch64-linux-android"
      ;;
    x86_64)
      rt_arch="x86_64"
      sysroot_arch="x86_64-linux-android"
      ;;
    *)
      die "unsupported ARCH for clang runtimes: ${ARCH}"
      ;;
  esac

  rt_dir="${clang_base}/${clang_ver}/lib/linux"
  arch_rt_dir="${rt_dir}/${rt_arch}"
  builtins_android="${rt_dir}/libclang_rt.builtins-${rt_arch}-android.a"
  mkdir -p "${rt_dir}" "${arch_rt_dir}"

  # For some Android driver paths, clang may resolve builtins to:
  #   <resource>/lib/<triple><api>/libclang_rt.builtins.a
  # Generate compatibility directories for all available sysroot API levels.
  if [[ -d "${pkg_root}/android-sysroot/usr/lib/${sysroot_arch}" ]]; then
    while IFS= read -r api; do
      [[ -n "${api}" ]] || continue
      api_levels+=("${api}")
    done < <(find "${pkg_root}/android-sysroot/usr/lib/${sysroot_arch}" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -n)
  fi
  api_levels+=("${API_LEVEL}")
  IFS=', ' read -r -a extra_api_levels <<< "${RUNTIME_API_LEVELS}"
  for api in "${extra_api_levels[@]}"; do
    [[ -n "${api}" ]] || continue
    api_levels+=("${api}")
  done
  mapfile -t api_levels < <(printf '%s\n' "${api_levels[@]}" | awk '/^[0-9]+$/ {print $1}' | sort -n -u)
  [[ ${#api_levels[@]} -gt 0 ]] || api_levels=("24")

  if [[ ! -f "${builtins_android}" || ! -f "${arch_rt_dir}/libunwind.a" ]]; then
    ndk_rt_dir="$(resolve_ndk_runtime_dir)"
    [[ -n "${ndk_rt_dir}" ]] || die "clang runtime missing and NDK runtime dir unavailable (need libclang_rt/libunwind)"
    log "补齐 clang runtime（from NDK）: ${ndk_rt_dir}"
  fi

  if [[ ! -f "${builtins_android}" ]]; then
    cp -a "${ndk_rt_dir}/libclang_rt.builtins-${rt_arch}-android.a" "${builtins_android}" 2>/dev/null || true
  fi
  if [[ ! -f "${arch_rt_dir}/libunwind.a" ]]; then
    cp -a "${ndk_rt_dir}/${rt_arch}/libunwind.a" "${arch_rt_dir}/" 2>/dev/null || true
  fi
  if [[ ! -f "${arch_rt_dir}/libatomic.a" ]]; then
    cp -a "${ndk_rt_dir}/${rt_arch}/libatomic.a" "${arch_rt_dir}/" 2>/dev/null || true
  fi

  [[ -f "${builtins_android}" ]] || die "missing runtime: ${builtins_android}"
  [[ -f "${arch_rt_dir}/libunwind.a" ]] || die "missing runtime: ${arch_rt_dir}/libunwind.a"

  for api in "${api_levels[@]}"; do
    [[ "${api}" =~ ^[0-9]+$ ]] || continue
    legacy_dir="${clang_base}/${clang_ver}/lib/${rt_arch}-unknown-linux-android${api}"
    mkdir -p "${legacy_dir}"
    cp -a "${builtins_android}" "${legacy_dir}/libclang_rt.builtins.a"
    cp -a "${arch_rt_dir}/libunwind.a" "${legacy_dir}/libunwind.a"
    if [[ -f "${arch_rt_dir}/libatomic.a" ]]; then
      cp -a "${arch_rt_dir}/libatomic.a" "${legacy_dir}/libatomic.a"
    fi
  done

  log "clang runtime ready: ${builtins_android}"
}

ARCH="${ARCH:-aarch64}"
API_LEVEL="${API_LEVEL:-28}"
TOOLCHAIN_VERSION="${TOOLCHAIN_VERSION:-0.2.0}"
WORKSPACE="${WORKSPACE:-/workspace}"
ROOT="${ROOT:-${WORKSPACE}/build/tina-toolchain}"
TARGET_BUILD="${TARGET_BUILD:-${ROOT}/build/android-${ARCH}-api${API_LEVEL}}"
RELEASE_ROOT="${RELEASE_ROOT:-${ROOT}/release}"

SOURCE_PACKAGE="${SOURCE_PACKAGE:-${RELEASE_ROOT}/tinaide-toolchain-${ARCH}-v${TOOLCHAIN_VERSION}.tar.zst}"
OUT_STEM_BASE="${OUT_STEM_BASE:-tinaide-toolchain-${ARCH}-v${TOOLCHAIN_VERSION}}"
OUT_SUFFIX="${OUT_SUFFIX:-repack}"
OUT_STEM="${OUT_STEM_BASE}${OUT_SUFFIX:+-${OUT_SUFFIX}}"

RUN_NINJA="${RUN_NINJA:-1}"
NINJA_TARGETS="${NINJA_TARGETS:-clang lld}"

# Comma/space separated. clang/lld are handled specially and can be omitted.
REPLACE_BINS="${REPLACE_BINS:-clangd,clang-format,llvm-ar,llvm-nm,llvm-objdump,llvm-objcopy,llvm-strip,llvm-ranlib,llvm-size,llvm-strings}"
REPLACE_CLANG_RESOURCE="${REPLACE_CLANG_RESOURCE:-1}"
STRIP_BINARIES="${STRIP_BINARIES:-1}"
# Extra compatibility API levels for:
#   lib/clang/<ver>/lib/<triple><api>/libclang_rt.builtins.a
# e.g. "24,28"
RUNTIME_API_LEVELS="${RUNTIME_API_LEVELS:-24}"

COMPRESS_ZST="${COMPRESS_ZST:-1}"
COMPRESS_XZ="${COMPRESS_XZ:-1}"
WORK_TMP="${WORK_TMP:-${ROOT}/_tmp_repack}"

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-}"

require_cmd tar
require_cmd xz
[[ "${COMPRESS_ZST}" == "0" ]] || require_cmd zstd
require_cmd sha256sum
require_cmd file
require_cmd readelf

[[ -d "${TARGET_BUILD}" ]] || die "TARGET_BUILD not found: ${TARGET_BUILD}"
[[ -f "${SOURCE_PACKAGE}" ]] || die "SOURCE_PACKAGE not found: ${SOURCE_PACKAGE}"
[[ -d "${RELEASE_ROOT}" ]] || die "RELEASE_ROOT not found: ${RELEASE_ROOT}"

log "ARCH=${ARCH} API_LEVEL=${API_LEVEL}"
log "TARGET_BUILD=${TARGET_BUILD}"
log "SOURCE_PACKAGE=${SOURCE_PACKAGE}"
log "RUN_NINJA=${RUN_NINJA} NINJA_TARGETS=${NINJA_TARGETS}"
log "REPLACE_CLANG_RESOURCE=${REPLACE_CLANG_RESOURCE} STRIP_BINARIES=${STRIP_BINARIES}"

if [[ "${RUN_NINJA}" != "0" ]]; then
  [[ -f "${TARGET_BUILD}/build.ninja" ]] || die "build.ninja missing in ${TARGET_BUILD}; run configure/build once first"
  # shellcheck disable=SC2206
  ninja_targets=( ${NINJA_TARGETS} )
  [[ ${#ninja_targets[@]} -gt 0 ]] || die "NINJA_TARGETS resolved empty"
  log "== Ninja build selected targets =="
  ninja -C "${TARGET_BUILD}" -j"$(nproc)" "${ninja_targets[@]}"
fi

log "== Extract existing package =="
rm -rf "${WORK_TMP}"
mkdir -p "${WORK_TMP}"

case "${SOURCE_PACKAGE}" in
  *.tar.zst)
    zstd -dc "${SOURCE_PACKAGE}" | tar -xf - -C "${WORK_TMP}"
    ;;
  *.tar.xz)
    xz -dc "${SOURCE_PACKAGE}" | tar -xf - -C "${WORK_TMP}"
    ;;
  *.tar)
    tar -xf "${SOURCE_PACKAGE}" -C "${WORK_TMP}"
    ;;
  *)
    die "unsupported SOURCE_PACKAGE format: ${SOURCE_PACKAGE}"
    ;;
esac

pkg_dir="$(find "${WORK_TMP}" -mindepth 1 -maxdepth 1 -type d | head -n 1 || true)"
[[ -n "${pkg_dir}" ]] || die "cannot find extracted package root dir in ${WORK_TMP}"
pkg_name="$(basename "${pkg_dir}")"
pkg_bin="${pkg_dir}/bin"

[[ -d "${pkg_bin}" ]] || die "package bin dir missing: ${pkg_bin}"

log "== Replace target ELFs =="

# clang/clang++ handling.
if [[ -L "${TARGET_BUILD}/bin/clang" ]]; then
  clang_real="$(readlink "${TARGET_BUILD}/bin/clang")"
  [[ -e "${TARGET_BUILD}/bin/${clang_real}" ]] || die "clang symlink target missing: ${TARGET_BUILD}/bin/${clang_real}"
  cp -a "${TARGET_BUILD}/bin/${clang_real}" "${pkg_bin}/"
  rm -f "${pkg_bin}/clang" "${pkg_bin}/clang++"
  ln -sf "${clang_real}" "${pkg_bin}/clang"
  ln -sf "${clang_real}" "${pkg_bin}/clang++"
else
  [[ -e "${TARGET_BUILD}/bin/clang" ]] || die "clang missing: ${TARGET_BUILD}/bin/clang"
  cp -a "${TARGET_BUILD}/bin/clang" "${pkg_bin}/"
  rm -f "${pkg_bin}/clang++"
  ln -sf clang "${pkg_bin}/clang++"
fi

[[ -e "${TARGET_BUILD}/bin/lld" ]] || die "lld missing: ${TARGET_BUILD}/bin/lld"
cp -a "${TARGET_BUILD}/bin/lld" "${pkg_bin}/"
rm -f "${pkg_bin}/ld.lld"
ln -sf lld "${pkg_bin}/ld.lld"

IFS=', ' read -r -a replace_bins <<< "${REPLACE_BINS}"
for b in "${replace_bins[@]}"; do
  [[ -n "${b}" ]] || continue
  [[ "${b}" == "clang" || "${b}" == "clang++" || "${b}" == "lld" || "${b}" == "ld.lld" ]] && continue
  if [[ -e "${TARGET_BUILD}/bin/${b}" ]]; then
    cp -a "${TARGET_BUILD}/bin/${b}" "${pkg_bin}/"
  else
    log "skip missing binary in TARGET_BUILD: ${b}"
  fi
done

if [[ "${REPLACE_CLANG_RESOURCE}" != "0" ]]; then
  [[ -d "${TARGET_BUILD}/lib/clang" ]] || die "clang resource missing: ${TARGET_BUILD}/lib/clang"
  mkdir -p "${pkg_dir}/lib/clang"
  # Overlay instead of hard-replace, keep existing runtime files when target build
  # only has partial clang resource.
  cp -a "${TARGET_BUILD}/lib/clang/." "${pkg_dir}/lib/clang/"
fi

log "== Ensure compiler-rt runtimes ==" 
ensure_clang_runtimes "${pkg_dir}"

if [[ "${STRIP_BINARIES}" != "0" ]]; then
  ndk_strip=""
  if [[ -n "${ANDROID_NDK_HOME}" ]] && [[ -x "${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ]]; then
    ndk_strip="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
  elif [[ -x "/opt/android-ndk-r27/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" ]]; then
    ndk_strip="/opt/android-ndk-r27/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
  fi

  if [[ -n "${ndk_strip}" ]]; then
    log "== Strip ELF binaries =="
    find "${pkg_bin}" -maxdepth 1 -type f -executable -print0 | xargs -0 -n 1 "${ndk_strip}" --strip-all 2>/dev/null || true
  else
    log "WARNING: llvm-strip not found, skip strip"
  fi
fi

log "== Repack =="
mkdir -p "${RELEASE_ROOT}"
checksum_file="${RELEASE_ROOT}/${OUT_STEM}.sha256"
rm -f "${checksum_file}"

if [[ "${COMPRESS_ZST}" != "0" ]]; then
  out_zst="${RELEASE_ROOT}/${OUT_STEM}.tar.zst"
  tar -C "${WORK_TMP}" -cf - "${pkg_name}" | zstd -19 -T0 -o "${out_zst}"
  sha256sum "${out_zst}" >> "${checksum_file}"
  log "written: ${out_zst}"
fi

if [[ "${COMPRESS_XZ}" != "0" ]]; then
  out_xz="${RELEASE_ROOT}/${OUT_STEM}.tar.xz"
  tar -C "${WORK_TMP}" -cf - "${pkg_name}" | xz -9e -T0 > "${out_xz}"
  sha256sum "${out_xz}" >> "${checksum_file}"
  log "written: ${out_xz}"
fi

log "written: ${checksum_file}"
log "done"
