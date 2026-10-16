# Release gates

These are subsystem gates, not equal-size units or a percentage-complete
estimate. A successful APK build is not evidence of a working game.

| Gate | State | Evidence / missing work |
| --- | --- | --- |
| 1. Android project / reproducible toolchain | Foundation ready | Gradle, SDK/NDK and prototype APK builds work. |
| 2. Artifact analysis / source identification | Foundation ready | DEX recovery, native inventories, close AGB source, geometry match, bounded behavioral replay. This does not mean all native code has been reconstructed. |
| 3. Full rebuilt gameplay core | Partial | Math, seed, autopilot vector routines and ship data work on four ABIs. Remaining game-state systems and full simulation are not connected. |
| 4. Rebuilt graphics | Partial | Mesh preparation validated; separate GLES2/JNI viewer builds. Device GPU testing, sprites, HUD, effects and integration remain. |
| 5. Connected game loop / menus / controls | Not connected | The game must reach a playable docked/flight state using only rebuilt code. |
| 6. Saves / sound / Android lifecycle | Not validated | Preserve original saves, connect sound callbacks, implement safe pause/resume and context recreation. |
| 7. Modern Android runtime matrix | Not performed | Test actual Android 11/13+ and 64-bit-only installs, JNI/GPU behavior and gameplay. |
| 8. Release packaging / regression | Not ready | Complete gameplay tests, all required native ABIs, appropriate signing, licensing review and release documentation. |

Current summary: **2 foundation gates ready, 2 in progress, 4 not completed**.
The largest work is still integrating the full rebuilt game, not adding ABI
directory names. No credible release-date estimate is established yet.

The original `app` APK remains a prototype with the original 32-bit objects.
The separate `preview` APK is a graphics test with four rebuilt ABIs. Neither
is claimed to be a completed universal release of Elite.
