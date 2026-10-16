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
