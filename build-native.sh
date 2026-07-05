#!/usr/bin/env bash
#
# Baut die native Rust/Tectonic-Bibliothek (libtexdroid_native.so) für Android
# und legt sie samt libc++_shared.so unter app/src/main/jniLibs/<abi>/ ab.
#
# Voraussetzungen (einmalig einrichten — siehe README, Abschnitt "Native Build"):
#   - Rust + Android-Targets:  rustup target add x86_64-linux-android aarch64-linux-android
#   - cargo-ndk:               cargo install cargo-ndk
#   - Android NDK (via Android Studio SDK Manager)
#   - vcpkg unter $VCPKG_ROOT (Default: ~/vcpkg), mit dem C-Stack für das jeweilige
#     Android-Triplet gebaut, z.B. für x64-android:
#       vcpkg install --triplet x64-android \
#         "harfbuzz[core,freetype,graphite2,icu,png]" freetype graphite2 icu libpng fontconfig
#   - Host-Tools: cmake ninja pkg-config autoconf automake libtool(-bin) bison gperf autoconf-archive perl
#
# Aufruf:
#   ./build-native.sh                 # Default-ABI: x86_64 (Emulator)
#   ./build-native.sh x86_64 arm64-v8a  # mehrere ABIs (arm64 braucht den arm64-android-vcpkg-Stack!)

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
JNILIBS="$PROJECT_DIR/app/src/main/jniLibs"

# ── ABIs aus Argumenten, sonst Default x86_64 ────────────────────────────────
ABIS=("$@")
[ ${#ABIS[@]} -eq 0 ] && ABIS=("x86_64")

# ── Toolchain-Env ────────────────────────────────────────────────────────────
[ -f "$HOME/.cargo/env" ] && source "$HOME/.cargo/env"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME/ndk/"*/ 2>/dev/null | sort -V | tail -1)"
  export ANDROID_NDK_HOME="${ANDROID_NDK_HOME%/}"
fi
export VCPKG_ROOT="${VCPKG_ROOT:-$HOME/vcpkg}"
export TECTONIC_DEP_BACKEND="vcpkg"

NDK_SYSROOT_LIB="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib"

echo "NDK:        $ANDROID_NDK_HOME"
echo "VCPKG_ROOT: $VCPKG_ROOT"
echo "ABIs:       ${ABIS[*]}"

# ── ABI → vcpkg-Triplet + NDK-Lib-Verzeichnis (für libc++_shared.so) ─────────
abi_to_triplet() { case "$1" in
  x86_64)     echo "x64-android" ;;
  arm64-v8a)  echo "arm64-android" ;;
  armeabi-v7a) echo "arm-android" ;;
  x86)        echo "x86-android" ;;
  *) echo "UNBEKANNT"; return 1 ;;
esac }
abi_to_ndklib() { case "$1" in
  x86_64)     echo "x86_64-linux-android" ;;
  arm64-v8a)  echo "aarch64-linux-android" ;;
  armeabi-v7a) echo "arm-linux-androideabi" ;;
  x86)        echo "i686-linux-android" ;;
esac }

for ABI in "${ABIS[@]}"; do
  TRIPLET="$(abi_to_triplet "$ABI")"
  echo "── Baue $ABI (vcpkg-Triplet: $TRIPLET) ─────────────────────────────"
  export VCPKGRS_TRIPLET="$TRIPLET"
  ( cd "$PROJECT_DIR/rust" && cargo ndk -t "$ABI" -o "$JNILIBS" build --release )

  # libc++_shared.so mitliefern (HarfBuzz/ICU sind C++, brauchen sie zur Laufzeit)
  cp "$NDK_SYSROOT_LIB/$(abi_to_ndklib "$ABI")/libc++_shared.so" "$JNILIBS/$ABI/"
  echo "   ✓ libtexdroid_native.so + libc++_shared.so in jniLibs/$ABI/"
done

echo "Fertig. Danach: ./gradlew :app:installDebug"
