# Native rebuild plan

The AGB source in `refs/eliteagb-0.5.2` is now the primary reconstruction
reference. The practical route to a real 64-bit APK is:

1. Copy the gameplay modules and data model into a new Android NDK library;
   retain the fixed-point types and structure layouts.
2. Replace GBA-only modules (`gbahandler`, sprite/OAM, mode-0 tile maps,
   interrupt/timer code, and SXMP playback) with Android implementations.
3. Implement the existing Android JNI contract exactly:
   `elite_start`, key/touch/tilt input, GL surface lifecycle, pause and
   special input. Keep callback names used by `EliteView` for sound, text,
   save/load and exit.
4. Implement the current GLES renderer as a compatibility layer over the
   existing `platform_*` calls. The shipped APK uses GLES 1.x and contains
   texture/sprite helpers that are not part of the AGB source.
5. Build the same source for `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`.
   Do not reuse the old `armeabi` ELF as `arm64-v8a`; it has 32-bit pointers
   and declared text relocations.
6. Compare gameplay states, save data, ship geometry, text and render output
   against the shipped x86/ARM libraries before replacing the fallback ABI
   libraries.

The current project deliberately keeps the original native objects as a
known-behavior fallback while this rebuild is incomplete.
