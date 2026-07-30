#!/usr/bin/env bash
#
# Baut die native Rust/Tectonic-Bibliothek (libtexdroid_native.so) für Android
# und legt sie samt libc++_shared.so unter app/src/main/jniLibs/<abi>/ ab.
#
# Voraussetzungen (einmalig einrichten — siehe README, Abschnitt "Native Build"):
#   - Rust + Android-Targets:  rustup target add x86_64-linux-android aarch64-linux-android
#   - cargo-ndk:               cargo install cargo-ndk
#   - Android NDK (via Android Studio SDK Manager)
#   - vcpkg unter $VCPKG_ROOT (Default: ~/.local/share/texslate/vcpkg, sonst ~/vcpkg),
#     mit dem C-Stack für das jeweilige
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
# Default: XDG-Datenverzeichnis (~/.local/share/texslate/vcpkg). Der alte Ort
# ~/vcpkg wird weiter akzeptiert, damit bestehende Arbeitsplätze nichts merken.
if [ -z "${VCPKG_ROOT:-}" ]; then
  if [ -d "$HOME/.local/share/texslate/vcpkg" ]; then
    VCPKG_ROOT="$HOME/.local/share/texslate/vcpkg"
  else
    VCPKG_ROOT="$HOME/vcpkg"
  fi
fi
export VCPKG_ROOT
# ⚠️ Zieht VCPKG_ROOT um, halten die Build-Skripte von Cargo ihre alten
# -L-Pfade fest (cargo:rustc-link-search im gecachten `output`) und das Linken
# scheitert mit „unable to find library -lbz2" o.ä. Dann einmal:
#   rm -rf rust/target/*/release/build/tectonic_bridge_* rust/target/*/release/build/tectonic_engine_*
export TECTONIC_DEP_BACKEND="vcpkg"

NDK_SYSROOT_LIB="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib"

echo "NDK:        $ANDROID_NDK_HOME"
echo "VCPKG_ROOT: $VCPKG_ROOT"
echo "ABIs:       ${ABIS[*]}"

# ── Engine-Fix (XeTeX print_glyph_name) ──────────────────────────────────────
# tectonic_engine_xetex' print_glyph_name() gibt einen INNEREN Zeiger frei: die
# Druckschleife schiebt `s` weiter (print_char(*s++)), danach freeGlyphName(s).
# glibc (Desktop) toleriert dieses fehlerhafte free(); Androids Scudo-Allocator
# bricht mit SIGABRT ab ("misaligned pointer when deallocating") -> JEDER Compile,
# der \XeTeXglyphname erreicht (u.a. unicode-math), reisst die App mit. Wir patchen
# die vendored Crate-Quelle idempotent vor dem cargo-Build (Fix: ueber eine Kopie
# drucken, den Original-Zeiger freigeben).
patch_xetex_print_glyph_name() {
  local f
  f="$(ls -d "$HOME"/.cargo/registry/src/*/tectonic_engine_xetex-*/xetex/xetex-ext.c 2>/dev/null | head -1)"
  if [ -z "$f" ]; then
    echo "  print_glyph_name-Fix: Quelle noch nicht im cargo-Cache (kommt beim ersten Build; einfach erneut aufrufen)."
    return 0
  fi
  if grep -q 'SCUDO print_glyph_name fix' "$f"; then
    echo "  print_glyph_name-Fix: bereits angewandt."
    return 0
  fi
  perl -0777 -pi -e 's#\n([ \t]*)while \(len-- > 0\)\n[ \t]*print_char\(\*s\+\+\);#\n${1}{ /* SCUDO print_glyph_name fix: print via copy, free the original pointer (Android/Scudo aborts on the interior free) */ const char* p = s; while (len-- > 0) print_char(*p++); }#' "$f"
  if grep -q 'SCUDO print_glyph_name fix' "$f"; then
    echo "  print_glyph_name-Fix: angewandt -> $f"
    # cargo erkennt Aenderungen im Registry-Cache NICHT per mtime -> Neubau der
    # Crate erzwingen, damit die gepatchte C-Datei neu kompiliert wird.
    rm -rf "$PROJECT_DIR"/rust/target/*/release/build/tectonic_engine_xetex-* \
           "$PROJECT_DIR"/rust/target/*/release/deps/*tectonic_engine_xetex* 2>/dev/null || true
  else
    echo "  WARN: print_glyph_name-Fix nicht angewandt (Quelltext der Crate abweichend?)."
  fi
}
patch_xetex_print_glyph_name

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

  # cargo-ndk kopiert alle .so aus dem Build-Graph nach jniLibs (z.B. ein
  # verwaistes libslug-*.so aus den deps/) — ins APK gehört aber nur unsere
  # eigene Lib. Alle Fremd-.so entfernen, bevor libc++_shared.so ergänzt wird.
  find "$JNILIBS/$ABI" -maxdepth 1 -name '*.so' ! -name 'libtexdroid_native.so' -delete

  # libc++_shared.so mitliefern (HarfBuzz/ICU sind C++, brauchen sie zur Laufzeit)
  cp "$NDK_SYSROOT_LIB/$(abi_to_ndklib "$ABI")/libc++_shared.so" "$JNILIBS/$ABI/"
  echo "   ✓ libtexdroid_native.so + libc++_shared.so in jniLibs/$ABI/"
done

echo "Fertig. Danach: ./gradlew :app:installDebug"
