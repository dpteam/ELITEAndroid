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

## Current native subset

`native/` now contains a separately buildable subset, not yet the full game:

- imported ship geometry from Elite AGB;
- fixed-width (`int32_t`) vector/matrix layout checks;
- integer square root, seed churn, vector normalization and rotation;
- autopilot vector routines;
- host tests and Android NDK builds for all four requested ABIs.

Run the import and host tests:

```text
python scripts/prepare_native.py
cmake -S native -B build/native-host -G Ninja -DCMAKE_C_COMPILER=gcc
cmake --build build/native-host
ctest --test-dir build/native-host --output-on-failure
```

Build Android subset libraries after installing NDK 28.2 and CMake 3.22.1:

```text
./scripts/build_native.ps1 -Sdk "$env:ANDROID_HOME" -Abis arm64-v8a,x86_64
```

The script's output is intentionally not copied into the APK yet. The full
platform layer still needs to be ported before `elite_core` can replace
`libelite_native.so`.

The generated `native/vendor/eliteagb` directory is ignored by Git because it
is derived from the local user-supplied `refs/eliteagb-0.5.2` tree. The build
script regenerates it before configuring CMake; `refs/` must therefore be
present in a clean working copy.

## Validation result

`analysis/validation/android-core.json` records an offline replay of the
original x86 machine code against every rebuilt ABI. At the current bounded
sample count (100 per routine), all four ABIs passed `geometry_checksum`,
`churn_seed`, `norm`, `norm_big`, `fixed_rotate`, `flip_vect`,
`fly_to_vector_final`, and `fly_to_vector`. The replay uses Unicorn and does
not execute an Android process. It does not validate the full renderer,
JNI/audio callbacks, lifecycle, saves, or device behavior.

## Mesh / viewer checkpoint

`native/src/elite_mesh.c` now reconstructs the non-effect part of `entry()`:
fixed-point camera transform, face visibility, detail thresholds, saturated
short GLES coordinates and indexed polygon/line commands. It intentionally
has no laser/explosion/distant-flicker/game-state side effects.

`scripts/test_mesh_replay.py` runs the original APK's x86 `entry()` and records
its `polygon3d()` submissions, then compares each rebuilt ABI's packets with
those submissions. This validates mesh math/commands, not pixels or a GPU.
The harness explicitly substitutes memory clearing and supplies test TLS for
stack canaries. See the retained JSON for full limits.

`preview/` connects the mesh subset through a separate JNI bridge and GLES2
viewer. This is a visual test application and must not be mistaken for the
full native game rebuild. The next integration work remains the game state,
full simulation/menus, HUD/sprites/effects and persistence/audio callbacks.
