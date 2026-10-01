#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?JDK required for JNI headers}"
mkdir -p artifacts/native
${CXX:-c++} -std=c++17 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" engine/src/main/cpp/enhancement.cpp tests/native/cdt_harness.cpp -o artifacts/native/cdt-sanitized
ASAN_OPTIONS=detect_leaks=0 artifacts/native/cdt-sanitized | tee artifacts/native/sanitizers.txt
