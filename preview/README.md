# Elite graphics test — not the game

This is a separate application (`elite.preview`) for validating the rebuilt
native rendering path. It can coexist with the original `elite.app` and has
no access to the original game's private saves.

## What it does

- Uses the actual imported ship geometry and reconstructed fixed-point mesh
  preparation from the supplied APK / Elite AGB reference.
- Displays 66 model descriptors with previous/next controls.
- Supports wireframe, solid, and shaded modes, rotation, and touch drag.
- Includes a `Flight` test mode that advances a pointer-free scene snapshot
  through the rebuilt control and object-motion subset. It is deliberately a
  test scene with one stationary reference target and synthetic stars, not the
  original universe/AI loop.
- In Flight mode, hold and drag from the initial touch point to steer, use the
  throttle slider (Adder profile: 1–36), and use Pause/Reset. Readouts show real
  test-state speed, roll, pitch, range and ticks; reference stars provide camera
  motion cues. Simulation uses a new bounded 30Hz scheduler, independent of
  display refresh. These are test readouts, **not the original game's HUD**.
- Submits generated mesh commands using a new GLES2 renderer and small JNI
  bridge. This renderer is a new implementation, not a recovered original
  GLES1 driver.
- Shows native-frame statuses and GL error codes rather than treating an
  unsupported mesh as successful rendering.
- The `Station` button opens a separate new test UI over actual rebuilt
  commander/system/market math: Lave data, credits, cargo, prices, stock and
  one-unit buy/sell. It has independent in-memory state from the Flight scene;
  switching screens is **not** docking/launching in a connected universe.
- Compact fullscreen panels support the tested 480x320 landscape display.
  Throttle controls are shown only in Flight. Both Activities keep the display
  awake only while visible; no global sleep or lock-screen settings are changed.
- Saved-instance Bundles preserve the ephemeral Flight scene/settings and
  Station snapshot across Activity/process recreation. This is **not disk
  saving**, nor a guarantee of recovery after force-stop, reboot or task removal.

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
adb -s DEVICE_SERIAL shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n elite.preview/.MeshActivity
```

Do not mistake this for a release of the game. There is no complete flight
simulation, original HUD/menu system, audio or disk save/load in this module.
The Flight button is a bounded movement test, not the connected game loop.
The Station button exercises market transactions in a separate snapshot, not
missions, travel, equipment, launch/docking or complete station gameplay.

## Checks already performed

- Successful Gradle/NDK build for four ABI directories.
- 66 descriptors, 10 poses each, 3 modes, 4 ABIs: 7920 mesh command comparisons
  against original APK x86 `entry()` passed. See
  `analysis/validation/mesh.json` for scope and harness substitutions.
- Native frame layout and invalid-input checks in host C tests.
- Host tests cover scene reset/step, state ownership, movement bounds, invalid
  input and snapshot ABI layout.
- `analysis/validation/flight.json` and `preview-packaged-flight.json` record
  control/movement replay against original APK instructions. Profile bytes,
  acceleration order, tilt clamping, damping, object movement and periodic
  matrix tidy are covered. New scene snapshots are also compared across ABI
  traces, not asserted equivalent to the original universe.
- Additional 2376 command comparisons using the exact stripped libraries
  extracted directly from the preview APK passed. See
  `analysis/validation/preview-packaged-mesh.json` (includes the APK digest).
- Preview APK signature verified with SDK `apksigner`.
- Station math comparison against original code: all 2048 market inputs,
  256 systems/names/info, nearest-system queries and 1000 trade snapshots per
  ABI. Overflow rejection is an intentional safety improvement tested in C,
  not an assertion of equivalence to old counter-wrapping behavior.
- SDK `zipalign -c -P 16` passed; static packaged-library checks passed for all
  8 ELF files. See `analysis/validation/preview-apk.json`.

**Device smoke test passed:** Android 13 / API 33, E5, ARM64, 480x320
landscape. Real ART/JNI calls, GLES2 shader compilation/output, three model
modes, steering/throttle, pause/reset, Station transactions and background
process-death restoration were observed. The final paused Flight screenshots
before/after process recreation are byte-identical. See
[`../analysis/validation/device-android13.md`](../analysis/validation/device-android13.md)
for APK identity, observations and limitations.

**Not checked yet:** other Android versions/GPUs, 64-bit-only devices, actual
ARM32/x86/x86_64 runtime execution, exhaustive on-device model/mode coverage,
long sessions, full-game lifecycle, original saves/audio and 16KiB-page devices.
The software PNGs under `build/mesh/` remain offline visualizations, not phone
screenshots; actual phone captures are under ignored `build/device-test/`.

The empty-command regression also runs without Android on the host JVM:

```text
javac --release 8 -d build/java-tests preview/src/main/java/elite/preview/MeshPacket.java preview/tests/MeshPacketTest.java
java -cp build/java-tests elite.preview.MeshPacketTest
```

It checks empty/fully culled packets, one-point/line commands and malformed
length/count/index/colour bounds. GLES submission skips empty mesh/star buffers
instead of setting a colour-pointer position beyond the buffer limit.

Reproduce exact packaged-library comparisons with Python and Unicorn 2.1.4:

```text
python scripts/test_mesh_replay.py --packaged-apk preview/build/outputs/apk/debug/preview-debug.apk --samples 3 --output analysis/validation/preview-packaged-mesh.json
python scripts/verify_preview_apk.py
python scripts/test_flight_replay.py --packaged-apk preview/build/outputs/apk/debug/preview-debug.apk --output analysis/validation/preview-packaged-flight.json
python scripts/test_station_replay.py --packaged-apk preview/build/outputs/apk/debug/preview-debug.apk --output analysis/validation/preview-packaged-station.json
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

For Flight testing, confirm: target and reference points move when steering;
release recenters input; throttle changes speed; Pause freezes ticks; Reset
restores the scene; background/restore does not advance hidden simulation or
leave a stuck input. Flying past/away from the target can cull the model; this
does not respawn or fake it, so use Reset to bring it back. No collision,
docking, AI, trade, weapon or save functionality is claimed for the Flight scene.

For Station testing: buy and sell Food and Minerals; verify exact balance,
stock and cargo changes; exhaust the hold; attempt purchases without funds or
stock; test kg/g goods; background/restore the Activity. Bundle restoration
preserves the ephemeral Station screen snapshot, **not an original Elite save
file**. No connection of station trades to the Flight scene is claimed.
