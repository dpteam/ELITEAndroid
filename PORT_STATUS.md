# Port status

## Completed

- Decompiled the supplied DEX and restored `EliteActivity`, `EliteView`, and
  `VersionedRotationDetector` as Java source.
- Preserved the original GLES 1.x texture/render bridge and JNI method names.
- Migrated the manifest to an exported launcher activity suitable for Android
  12+ and made sensors optional with null-sensor handling.
- Migrated the build to AGP 8.7.3 / compileSdk 35 / targetSdk 35 / minSdk 21.
- Preserved all five UI textures and all 21 audio/MIDI resources.
- Built and packaged the APK successfully.
- Added reproducible static native analysis scripts and regenerated Ghidra
  pseudocode for both shipped ELF32 libraries.
- Added a separately buildable fixed-width native subset and NDK output for
  `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`; it is not yet wired into the
  APK because it does not contain the full game/platform layer.
- Offline differential replay passed for all four rebuilt ABIs on the selected
  core routines; see `analysis/validation/android-core.json`.
- Reconstructed a reentrant non-effect mesh-preparation path, compared 66 model
  descriptors in wireframe/solid/shaded modes against the original APK command
  stream on four ABIs, and built a separate GLES2/JNI graphics-test application.
- Added a pointer-free Flight test scene with bounded control/object-motion
  snapshots, including original tick ordering for queued acceleration; this is
  a test harness, not the connected Elite game loop.
- The Flight preview adds hold/drag steering, a throttle slider, pause/reset,
  synthetic reference points and numeric state readouts. Its new 30Hz test
   scheduler and UI passed a bounded Android 13 ARM64 device smoke test; this
   does not validate the original full-game scheduler or HUD.
- Added a commander/navigation/market subset and a separate Station screen in
  `elite.preview`: current system data, credit balance, cargo and one-unit
  transactions. It is not the original station UI or a connected game state.
- Commander layout is explicitly reconciled to the APK's 92 bytes: 15 equipment
  slots versus 13 in older AGB source. Two extra equipment meanings are unknown.
- Station validation covers all 2048 economy/market-random combinations,
  256 systems in the first galaxy, nearest-system searches and sampled original
  trade-wrapper state changes on four ABIs. See station replay reports.
- Installed and ran `elite.preview` on an Android 13 E5 phone using ARM64
  libraries. Confirmed three mesh modes, ADB touch steering/throttle, pause,
  reset, Food trades, full-hold/no-stock rejection and process-death restoration.
- Fixed an on-device empty draw-buffer crash and added a host-JVM packet
  regression test. Compacted both test screens for the 480x320 landscape display.
- Added Flight Bundle restoration for scene, model, mode, pause and throttle;
  KEEP_SCREEN_ON applies only while each test Activity is visible.

## Separate graphics-test APK

`preview/build/outputs/apk/debug/preview-debug.apk` (`elite.preview`) contains
rebuilt ARMv7/ARM64/x86/x86_64 libraries. It is a model viewer, **not the game**.
Build, signature and static ELF/ZIP alignment checks passed. Android 13 ARM64
GPU/JNI smoke testing passed on one phone, including fresh process/context
creation and saved-instance restoration. See `analysis/validation/device-android13.md`.
This is not a 64-bit-only or multi-version/device matrix. Full limitations are listed in
`preview/README.md` and `RELEASE_CHECKLIST.md`.

## Verified artifact

`app/build/outputs/apk/debug/app-debug.apk`

The verified debug build reports:

- package: `elite.app`
- version: `1.6-modern` / version code `7`
- min SDK: `21`
- target SDK: `35`
- native ABI directories: `armeabi`, `x86`

## Remaining work for true modern universal support

The two shipped native objects are 32-bit and were built against the old
Android platform ABI. A complete Android 11/13 universal release needs a
source-level native rebuild producing at least:

- `arm64-v8a/libelite_native.so`
- `x86_64/libelite_native.so`

The Java port alone cannot produce those binaries. The native code contains
the game engine, GLES renderer, and platform callbacks; it must be recovered
from the original source or ported function-by-function from the ARM/x86
artifacts, then validated on Android 11 and Android 13 devices/emulators.

## AGB source comparison

The supplied `refs/eliteagb-0.5.2` is a close source reference for this APK,
not a drop-in Android build. Static comparison found:

- 251 matching gameplay/exported function names;
- 110/110 compared numeric ship-geometry arrays matching byte-for-byte in both
  APK ABIs;
- the same characteristic routines such as `fly_to_vector_final`,
  `ship_bay_collection`, `g_commander`, and
  `g_galactic_hyper_preliminary`.

The APK additionally has an Android/GLES platform layer and dynamic JNI
registration. `scripts/inspect_native.py` reproduces the bounded comparison in
`analysis/artifact/inventory.json`. Ghidra output in `analysis/native-arm` and
`analysis/native-x86` is static pseudocode for reference only; it is not
claimed to be compilable source.

## Supplied `refs`

`refs/ELITE1NES` is a documented 6502/NES Elite source reconstruction. It is
valuable as a behavioral and naming reference: it contains the flight loop,
ship blueprints, market/gameplay routines, text, save/load logic, and drawing
algorithms. It is not the C/Android source used to build the supplied
`libelite_native.so`, so it cannot be compiled directly as the missing
`arm64-v8a` or `x86_64` library.

`refs/glfrontier-master` is a separate reverse-engineered Frontier/Atari ST
project. It is not the same game engine as this APK and is retained only as a
reference; it should not be mixed into the Android module.

The licensing notices in those reference trees are intentionally preserved.
