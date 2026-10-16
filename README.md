# Elite Android modernisation

This directory contains a reproducible Android Studio/Gradle project rebuilt
from the supplied `Elite.apk` (SHA-256:
`55adfacd919f4eb48ad54f4f022eb4bd801dd39ba12f4b7fe21582f1a382c690`).

## Current result

The project builds with Android Gradle Plugin 8.7.3, compile/target SDK 35,
and min SDK 21. The original Java/OpenGL ES wrapper, resources, sound files,
and native libraries are included. The native registration class remains
`elite.app.EliteView`, as required by the shipped libraries.

Build a debug APK:

```text
gradlew.bat assembleDebug
```

Output:
`app/build/outputs/apk/debug/app-debug.apk`

The generated APK is signed with the standard debug key and is suitable for
local installation/testing.

## ABI limitation

The supplied APK contains these native objects only:

* `lib/armeabi/libelite_native.so` — 32-bit ARM ELF
* `lib/x86/libelite_native.so` — 32-bit x86 ELF

They are kept unchanged in the modern APK. This is a **universal APK for the
ABIs present in the supplied artifact**, not a complete 64-bit universal APK.
There is no valid way to turn a 32-bit ARM ELF into `arm64-v8a` by renaming or
repacking it. Adding `arm64-v8a` and `x86_64` requires rebuilding the native
game from source or a genuine 64-bit port of the native code. On 64-bit-only
devices (for example some recent Android 13+ devices), this APK will not be
installable/runnable until those two libraries exist.

## Reverse-engineering notes

The native library uses dynamic JNI registration and exports the following
entry points through `elite/app/EliteView`: game start, key/touch input, tilt,
OpenGL surface creation/resizing/rendering, pause, and special input. It also
calls back into Java for sound, text rendering, save/load, and exit. Therefore
the port preserves the original wrapper rather than replacing the game with a
placeholder.

The old APK was signed with the original `ALIAS_NA` certificate. The rebuilt
APK is necessarily signed with a new debug key; it cannot update the old
installation in place and must be installed as a separate/reinstalled package.

## Reproducible analysis

The bounded static comparison against the supplied AGB source is stored in
`analysis/artifact/inventory.json` and can be regenerated with:

```text
python scripts/inspect_native.py
```

The script reports the APK digest, ABI/ELF facts, dynamically registered JNI
methods, exported-name overlap, and exact numeric ship-geometry matches. It
does not execute the APK or native code. The larger Ghidra pseudocode dumps
are intentionally ignored by Git; they can be regenerated with the scripts
under `scripts/ghidra/` and the installed Ghidra headless analyzer.

The local `refs/` directory is treated as user-supplied reference material and
is ignored by the current `.gitignore`; its licensing notices are not copied
into the Android application.
