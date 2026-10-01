# Retained dependency boundary

This is a reusable rendering-engine proof, not a finished editor.

## Compiled upstream implementation

FilterGLThread retains EGL setup, real enhancement → sharpening → custom
adjustments/user-curves → blur ordering, framebuffer selection and exact
getRenderBufferBitmap/glReadPixels output. FilterShaders retains those shader
bodies, generated Gaussian blur math, texture/buffer setup and delegate parameter
mapping. Native enhancement compiles only the actual calcCDT kernel/constants
plus the required MIN/MAX macros, under a narrow JNI entry point.

SavedFilterState is extracted data/isEmpty from MediaController, not a fake
MediaController. CurvesValue/CurvesToolValue retain PhotoFilterView's exact
interpolation, cache, defaults, curve texture packing and save/restore logic.
Protocol serialization is removed. DispatchQueue is the real local Looper/Handler
queue with bounded readiness handling.

HDR/video/UI blur service inputs are removed. Some unreachable video/SDR-neutral
source branches remain for a small reviewable diff; no video input API is exposed.
There is no AccountInstance, UserConfig, tgnet, transport, database, libyuv,
FFmpeg, model download, Telegram API configuration or network stack in the build.

## Deliberately unsupported smoothing

The optional skin-smoothing shader/program/pass block, its private ToneCurve,
related fields and shader compilation paths are completely removed. No alternate
algorithm was added. The independent lineage review could recognize original
MIT/BSD source matches but not establish the immediate Java port chain.

A deprecated SavedFilterState.softenSkinValue compatibility field remains solely
to reject any nonzero value with UnsupportedOperationException. The shader
interface has no smoothing operation. Ordinary user adjustment curves are a
separate retained GPL model and remain fully supported.

Full originals and full deletion patches are outside the distributable tree.
provenance/ records their URLs/pin/blob/hash and patch hashes without redistributing
excluded shader/tone-mapper bodies. Current retained sources are sufficient to
build; optional mechanical regeneration requires external exact reference files.

## Original narrow adapters and safety checks

PhotoEngine snapshots a caller bitmap, schedules asynchronous renders, owns
session/resource lifetime and returns upstream output. EngineLog delegates to
Android logging. NativeEnhancement loads the isolated native library and validates
writable buffers. The pbuffer option is a platform adapter for headless export;
original window-surface rendering is also tested. No original image math is added.

The wrapper requires input and scaled edges >=8 pixels and caps source size at
32 MP/output maximum side 4096 (default 2048). An actual 4×4 colored-image probe
became black because the unchanged enhancement CDF range degenerated; 8×8 and
16×16 retained color. Tiny inputs are rejected rather than substituted.
JNI independently rejects null/non-direct/undersized/misaligned buffers, native
dimensions below 4, excessive dimensions and pixel counts. Its algorithm body is
unchanged after validation. Readback/queue waits are bounded; close is idempotent,
queued requests cancel, and only owned input/temporary/result resources recycle.
A running GPU call is not claimed to be preemptible.

State objects are mutable upstream data. Do not mutate/share one state concurrently
across pending renders; use a fresh state or refresh a modified curve's cache.
An all-zero state has upstream 0.11 baseline sharpening; renderOriginal bypasses
adjustments. Original alpha is preserved; premultiplied transparent RGB behavior
and wider cross-effect color correctness need their own product policy. Mirror
only affects upstream preview coordinates, so mirrored export is not exposed.

## Binary closure

The engine has no runtime Maven dependency. The arm64-v8a/x86/x86_64 native
libraries import only libc/libm/libdl and expose the one relocated JNI operation.
Artifact checks inspect actual AAR/APK class/DEX names, embedded implementation
strings, ELF dependencies and merged permissions. Both test hosts request zero
permissions. Provenance checks compare retained shaders/passes/curves/kernel and
readback to pinned external references, allowing only documented removed-feature
plumbing. No line-count reuse percentage is used as a substantive-core claim.
