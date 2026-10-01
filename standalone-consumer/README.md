# Independent AAR consumer proof

This directory builds independently from a copied, SHA-256-pinned engine AAR.
It does not include or reference the original source project or smoke-host.

Run `sha256sum -c ENGINE-AAR.sha256`, then `./gradlew :app:assembleDebug
:app:assembleDebugAndroidTest` with JDK21 and SDK35. The engine's corresponding
source, exact upstream originals and patches are in the containing proof bundle.
This is an integration test host, not another product.
