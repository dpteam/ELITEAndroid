# Android 13 ARM64 connected-session smoke test

This is the follow-up to `device-android13.md` for preview version
`0.3-session-test`. It tests the new bounded integration harness only; it is
not evidence that the original Elite game loop has been rebuilt.

## Artifact and target

- Device: UNISOC E5, Android 13 / API 33, ARM64 process ABI.
- Landscape display: 480x320; physical display 320x480; kernel page size 4096.
- Package: `elite.preview`, version `0.3-session-test`, version code 3.
- APK SHA-256: `3d30f915cd3844433bbfe4d783a3078e4b2d918e7cfcbc0f08f40001190eb807`.
- The package contains four rebuilt ABIs; this phone selected `arm64-v8a`.
- No uninstall, clear-data, original-game launch or original-save access was used.

## Device observations

1. Opened the exported launcher `MeshActivity`, then opened the non-exported
   `SessionActivity` through its visible `Session` button. A direct shell launch
   of the non-exported Activity was rejected by Android with `SecurityException`,
   as intended; the user-visible navigation worked.
2. Session station began at Lave with 500.0 Cr, Food stock 16 and cargo 0/9.
3. Bought Food: 496.4 Cr, stock 15, cargo 1/9.
4. Used `Launch test`: the Flight screen showed the same 496.4 Cr and cargo 1/9.
   The header clearly labels this as a connected test and the return as a
   shortcut, not docking.
5. Paused Flight, backgrounded it, killed only the background `elite.preview`
   process, and relaunched through the exported launcher. The saved Session
   state restored Flight mode, pause, commander summary and scene; before/after
   paused PNGs were byte-identical in the preceding session run.
6. Used `Return (test)`, then sold Food: 500.0 Cr, stock 16, cargo 0/9.
7. Opened the reset confirmation. It explicitly warns that only the test
   commander is discarded and original Elite saves are unaffected; cancel left
   the state unchanged.
8. A second buy/launch/return sequence showed the same shared-state behavior.
   The final on-device screen was left on Session station after the round trip.

## Offline exact-APK validation

`preview-packaged-session.json` was rerun against this APK for all four ABI
libraries: 100 cycles per ABI, 60 scene ticks per cycle, 6,000 scene-comparison
steps per ABI. It covers buy/launch, rejection of in-flight trade, atomic
invalid-step handling, scene reset, same-station return, sell/round-trip,
corrupt-state guards and cross-ABI trace equality. Host C tests pass for both
the existing core and the session composition.

The mesh, flight and station packaged replay reports were also rerun against
the same APK. Static verification passes for all eight native libraries:
correct ABI labels, no TEXTREL, no writable-executable segments and 16KiB ELF
and ZIP alignment. `apksigner` and `zipalign -c -P 16 4` pass.

## Boundary

`elite_session_state` is a new 1124-byte in-memory composition, not an original
save format. `Return (test)` does not perform approach, collision, docking,
fuel, legal-status, reward, station regeneration or universe updates. The full
original menus, HUD, AI/combat, persistence, audio and game loop remain future
work. This phone result does not validate ARM32, x86 or x86_64 Android runtime
execution, other Android versions/GPUs, 16KiB-page devices or the original
`elite.app` prototype.
