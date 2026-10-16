# Elite graphics test — not the game

This is a separate application (`elite.preview`) for validating the rebuilt
native rendering path. It can coexist with the original `elite.app` and has
no access to the original game's private saves.

## What it does

- Uses the actual imported ship geometry and reconstructed fixed-point mesh
  preparation from the supplied APK / Elite AGB reference.
- Displays 66 model descriptors with previous/next controls.
- Supports wireframe, solid, and shaded modes, rotation, and touch drag.
- Submits generated mesh commands using a new GLES2 renderer and small JNI
  bridge. This renderer is a new implementation, not a recovered original
  GLES1 driver.
- Shows native-frame statuses and GL error codes rather than treating an
  unsupported mesh as successful rendering.

## Build

Requirements: JDK, Android SDK 35, NDK `28.2.13676358`, CMake `3.22.1`, and
the locally supplied `refs/eliteagb-0.5.2` source tree.

```text
python scripts/prepare_native.py
gradlew.bat :preview:assembleDebug
```

APK: `preview/build/outputs/apk/debug/preview-debug.apk`.

Optional install on a connected, explicitly selected test device:

```text
adb -s DEVICE_SERIAL install -r preview/build/outputs/apk/debug/preview-debug.apk
adb -s DEVICE_SERIAL shell am start -n elite.preview/.MeshActivity
```

Do not mistake this for a release of the game. There is no trade, flight
simulation, HUD, menu system, audio or save/load in this module.

## Checks already performed

- Successful Gradle/NDK build for four ABI directories.
- 66 descriptors, 10 poses each, 3 modes, 4 ABIs: 7920 mesh command comparisons
  against original APK x86 `entry()` passed. See
  `analysis/validation/mesh.json` for scope and harness substitutions.
- Native frame layout and invalid-input checks in host C tests.
- Additional 2376 command comparisons using the exact stripped libraries
  extracted directly from the preview APK passed. See
  `analysis/validation/preview-packaged-mesh.json` (includes the APK digest).
- Preview APK signature verified with SDK `apksigner`.
- SDK `zipalign -c -P 16` passed; static packaged-library checks passed for all
  8 ELF files. See `analysis/validation/preview-apk.json`.

**Not checked yet:** real Android install/start, JNI calls on ART, GLES shader
compilation on a device, GPU output, touch behavior, and pause/resume/context
recreation. The software PNGs under `build/mesh/` are offline visualizations,
not screenshots or proof of Android execution.

Reproduce exact packaged-library comparisons with Python and Unicorn 2.1.4:

```text
python scripts/test_mesh_replay.py --packaged-apk preview/build/outputs/apk/debug/preview-debug.apk --samples 3 --output analysis/validation/preview-packaged-mesh.json
python scripts/verify_preview_apk.py
```

## Known scope limitations

The mesh path does not implement distant-object flicker, lasers, explosions,
HUD, background tiles, sprites or gameplay mutation. Unsupported effect flags
are rejected explicitly. Clearing per-frame face visibility is intentional;
the comparative replay also starts with cleared buffers and does not claim
equivalence to the original renderer's stale-buffer behavior across frames.

Manual device checklist: launch all four architecture variants as available,
cycle all 66 descriptors and three modes, drag/rotate/pause, background/restore,
turn display off/on, and capture any GL/JNI error with logcat.
