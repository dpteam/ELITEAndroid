# Release gates

These are subsystem gates, not equal-size units or a percentage-complete
estimate. A successful APK build is not evidence of a working game.

| Gate | State | Evidence / missing work |
| --- | --- | --- |
| 1. Android project / reproducible toolchain | Foundation ready | Gradle, SDK/NDK and prototype APK builds work. |
| 2. Artifact analysis / source identification | Foundation ready | DEX recovery, native inventories, close AGB source, geometry match, bounded behavioral replay. This does not mean all native code has been reconstructed. |
| 3. Full rebuilt gameplay core | Partial | Math, movement/controls, seed, ship data, commander/system/market math work on four ABIs. Original universe, combat/AI and full game-state systems are not connected. |
| 4. Rebuilt graphics | Partial | Mesh preparation validated; GLES2/JNI viewer observed on Android 13 ARM64. Wider GPU/model coverage, sprites, HUD, effects and integration remain. |
| 5. Connected game loop / menus / controls | Partial: test harness only | New Session screen shares one commander/market snapshot across test launch, bounded navigation/jump, approach/dock test, flight, Return shortcut and trades. It is not the original universe, docking, menu or connected game loop. |
| 6. Saves / sound / Android lifecycle | Partial: test harness only | Flight and Station Bundle restoration passed background process death on Android 13. Original saves, sound and full-game lifecycle remain unimplemented/unvalidated. |
| 7. Modern Android runtime matrix | Partial: one device | Android 13 ARM64 preview install/JNI/GPU/input passed. Android 11, other versions/GPUs, 64-bit-only devices, other runtime ABIs and actual gameplay remain. |
| 8. Release packaging / regression | Not ready | Complete gameplay tests, all required native ABIs, appropriate signing, licensing review and release documentation. |

Current summary: **2 foundation gates ready, 4 partial, 2 not connected/ready**.
Partial gates are not release passes; see `analysis/validation/device-android13.md`
for the bounded device smoke test.
The largest work is still integrating the full rebuilt game, not adding ABI
directory names. No credible release-date estimate is established yet.

The original `app` APK remains a prototype with the original 32-bit objects.
The separate `preview` APK is a graphics test with four rebuilt ABIs. Neither
is claimed to be a completed universal release of Elite.
