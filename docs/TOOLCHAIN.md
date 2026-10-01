# Build pins

- JDK: Eclipse Temurin 21.0.12.1+1 used for verification
- Gradle 8.11.1, distribution SHA-256 pinned in wrapper properties
- Android Gradle Plugin 8.9.3, strict dependency verification metadata
- Java source/target 17
- Compile/target SDK 35, build-tools 35.0.0, minimum API 26
- NDK 27.2.12479018 (r27c); CMake 3.22.1
- Native ABIs: arm64-v8a, x86, x86_64; no STL runtime
- Runtime verification: API26 default x86 revision1, Emulator 37.1.11,
  software acceleration and SwiftShader

Both consumer app modules pin the same NDK for deterministic native stripping.
Packaged APK native files contain no `.debug_*` sections. Android SDK/NDK tools
and their licenses must be installed normally on the developer's machine.
