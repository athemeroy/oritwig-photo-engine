# Oritwig Photo Engine

An offline Android photo-filter library extracted from Telegram Android's mature
renderer. It accepts a local Bitmap and returns real GPU-rendered pixels, without
Telegram accounts, API credentials, networking or the messenger application.

This repository contains a reusable library and two small test hosts. The complete
Photo editor is a separate application.

## Upstream and extraction

Pinned upstream: [DrKLO/Telegram](https://github.com/DrKLO/Telegram/tree/f2908b14133bbffbf7ab04f641ecb5bfaf533242),
commit `f2908b14133bbffbf7ab04f641ecb5bfaf533242`.

The retained core is FilterGLThread → FilterShaders → native calcCDT → framebuffer
readback. Enhancement, sharpening, exposure/contrast/saturation and other custom
parameters, RGB/luminance curves, and radial/linear blur retain upstream shader
and algorithm bodies. No CPU replacement renderer was written.

Extraction code provides package/model relocation, local Android adapters,
headless EGL surfaces, asynchronous lifetime management, input validation and a
small isolated JNI target. No account-shaped stubs or full tmessages native
library are included. See [NOTICE](NOTICE), [provenance](provenance/) and the
[dependency boundary](docs/DEPENDENCIES.md).

Optional skin smoothing and its private tone mapper are deliberately absent while
an immediate upstream port/licensing chain remains unresolved. A nonzero legacy
softenSkinValue is explicitly rejected. Ordinary user adjustment curves remain.

## Build

Use JDK 21, Android SDK/build-tools 35, NDK 27.2.12479018 and CMake 3.22.1.
The wrapper pins Gradle 8.11.1; AGP 8.9.3 uses checked-in dependency verification.

```sh
./gradlew :engine:assembleDebug
./gradlew :smoke-host:assembleDebug :smoke-host:assembleDebugAndroidTest
python3 tools/verify-retained-source.py
./tools/test-native-host.sh
./tools/build-independent-consumer.sh
```

The AAR is produced at `engine/build/outputs/aar/engine-debug.aar`. The independent
consumer is built in a fresh directory using only a copied, checksum-pinned AAR.
Its helper uses offline Gradle caches; resolve the pinned dependencies once first.

Normal builds use only this repository. Optional mechanical reproduction requires
exact reference files listed in `provenance/upstream-files.json`, downloaded
outside this repository; set TELEGRAM_REFERENCE_DIR before tools/extract.py or
tools/verify-extraction.py. Full excluded originals/deletion patches are not
redistributed; their URLs, blob IDs and patch digests are recorded.

## Reuse

```java
PhotoEngine engine = new PhotoEngine(inputBitmap, 0, 2048);
SavedFilterState state = new SavedFilterState();
state.exposureValue = 20;
engine.render(state).whenComplete((outputBitmap, error) -> {
    // Save/display the caller-owned output, or handle the error.
    engine.close();
});
```

Classes are in `dev.oritwig.telegram.photo`. Construct/decode off the UI thread;
callbacks run off the UI thread. The constructor snapshots the input, and callers
own returned bitmaps. Do not mutate/share a state across pending renders. Refresh
a reused curve's interpolation cache after changing its levels.

`renderOriginal()` bypasses adjustments. A zero-valued adjustment state still has
the upstream 0.11 baseline sharpening. `closeAsync()` supports awaited cleanup.

## Limits and verified scope

- Android API 26+, SDR still images, source/scaled edges at least 8 pixels
- Orthogonal orientation: 0/90/180/270; maximum output side 4096, default 2048
- Source limit 32 MP; no crop, drawing/text, mirrored export, HDR or video here
- Original rendering preserves alpha; broad transparent-color/device-GPU parity
  is not promised
- GPL-2.0-or-later, preserving the original GPL version 2 text and file notices; no endorsement or
  claim that every retained algorithm originated with Telegram

The exact clean AAR passed fresh-install API26 x86/SwiftShader rendering/export,
lifecycle and independent-consumer tests. Identity error was mean 0.1985 and max 2
on a 0–255 RGB scale. Host/AAR scans found no account/network dependency; both test
hosts request zero permissions. Kernel ASan/UBSan fixtures and source-retention
checks also pass. See [test results](docs/TEST-RESULTS.md) for the exact scope.
