# Elite AGB reference modules

The generated vendor files are bounded imports from the user-supplied `refs/eliteagb-0.5.2`, also
available at:
https://www.geocities.ws/quirky_2k1/downloads/eliteagb-0.5.2.src.zip

Elite AGB is by Quirky (Richard Quirk), based on Elite A by Angus J. C.
Duggan and the original Elite by Ian Bell and David Braben. It is **not**
Elite: The New Kind. The upstream README explicitly does not grant an
open-source licence; public source availability is not a redistribution
licence. See `upstream-readme.txt`. Do not publish these files or resulting
packages without reviewing the applicable rights. No replacement licence
is being applied to the imported content.

`scripts/prepare_native.py` reproduces the import and records SHA-256 digests
and transformations in `import-manifest.json`. The provided `refs/` files
are never modified. Hardware-dependent gameplay functions have not been
replaced with dummy implementations.

The selected math functions are compiled with 32-bit fixed-point vectors,
signed plain char, and defined two's-complement signed arithmetic wrapping
(`-fwrapv`) to avoid LP64 data-model drift.

`motion_object.c` imports `mveit` and `rotate`, replacing globals with an
explicit motion-state argument. `control_helpers.c` imports only dampen/bump/
redu helpers; `player_profiles.c` imports only the 15 player-profile records.
The Android-specific control order in `native/src/elite_flight.c` was
reconciled with the supplied APK's `update_motion`/`dampen_controls`, not
assumed identical to the older GBA code. `native/src/elite_scene.c` is a new
test harness (one stationary target and synthetic reference points), not a
recovered original game scene or `update_universe` replacement.
