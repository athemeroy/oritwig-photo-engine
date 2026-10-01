# Verified scope

Tested AAR SHA-256:
`990cf1396e8c7bf04230f9a64209ea8279c16c1c6c7b1414a1af7564bb1f4909`

- Independently built AAR and two separately buildable hosts
- Fresh-install API26 x86/SwiftShader run; both instrumentation runners returned
  success (`INSTRUMENTATION_CODE: -1`)
- Original/default renders, eight retained effect cases, user curves, all three
  nonzero orthogonal rotations, original alpha, scaling and PNG pixel roundtrips
- Source immutability, repeated export, cancellation, idempotent close/post-close
  rejection and two real TextureView render/export/destruction cycles
- Minimum accepted 8-pixel input and explicit unsupported-smoothing rejection
- Independent AAR-only consumer: original/adjusted rendering, PNG export and close
- No newly logged GL driver errors during the final exact-pin run

Identity RGB mean absolute error was 0.1985134548611111, maximum 2, on the 0–255
scale. Declared test bounds are mean <=3 and max <=12. Rotation cases also had
maximum 2. All eight individually exercised effects changed significant pixels.
This is not an official-app golden comparison or real-device GPU coverage.

The kernel completed 1,617 guarded ASan/UBSan host fixtures. LeakSanitizer was
unavailable under the execution environment's ptrace wrapper and was explicitly
disabled; no native leak-check claim is made. Engine lint has no issues. The small
fixture host has zero errors and 11 nonfatal UI/icon/backup-declaration warnings.

Source checks compare all 12 retained shader constants, filter math, normal blur
shader generation, parameter mapping, user curves, calcCDT and readback to pinned
upstream references. Artifact checks reject removed smoothing implementation,
account/network symbols and unexpected ELF dependencies. The native libraries
link only libc/libm/libdl; both host merged manifests request zero permissions.

Network independence was checked through code/binary closure and actual local
fixtures with no Telegram setup. Airplane-mode operation was not tested. No
production-readiness, complete-editor or broad legal-clearance claim is made.
