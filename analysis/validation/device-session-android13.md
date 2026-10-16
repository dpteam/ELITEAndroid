# Android 13 ARM64 connected-session smoke test

This is the follow-up to `device-android13.md` for preview version
`0.5-docking-test`. It tests the new bounded integration harness only; it is
not evidence that the original Elite game loop has been rebuilt.

## Artifact and target

- Device: UNISOC E5, Android 13 / API 33, ARM64 process ABI.
- Landscape display: 480x320; physical display 320x480; kernel page size 4096.
- Package: `elite.preview`, version `0.5-docking-test`, version code 5.
- APK SHA-256: `294748573b23061d5048d4506ceb8c55e5923207f441af2fe04e46c1daa63980`.
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
9. Used `Reachable` to select a target within the 6.0 ly fuel range. `Jump
   (test)` moved from Lave (#7) to Reorte (#39), reduced fuel from 6.0 to 1.6
   ly, regenerated the target market and retained commander cargo/credits.
   `Return (test)` then showed Reorte system data and the changed prices.
10. Tested the docking corridor. `Dock (test)` before approach was rejected with
    `approach is not ready`. `Approach (test)` then reported `distance 0 · speed
    2 · ready`; `Dock (test)` returned to the station screen successfully.

## Offline exact-APK validation

`preview-packaged-session.json` was rerun against this APK for all four ABI
libraries: 100 cycles per ABI, 60 scene ticks per cycle, 6,000 scene-comparison
steps per ABI. It covers buy/launch, rejection of in-flight trade, atomic
invalid-step handling, scene reset, same-station return, sell/round-trip,
corrupt-state guards and cross-ABI trace equality. Host C tests pass for both
the existing core and the session composition.
The navigation portion also covers reachable target selection, fuel deduction,
coordinate/name/system update, market regeneration and cargo preservation. The
docking portion covers early rejection, bounded deceleration/readiness and the
final dock phase transition.

The mesh, flight and station packaged replay reports were also rerun against
the same APK. Static verification passes for all eight native libraries:
correct ABI labels, no TEXTREL, no writable-executable segments and 16KiB ELF
and ZIP alignment. `apksigner` and `zipalign -c -P 16 4` pass.

## Boundary

`elite_session_state` is a new 1184-byte in-memory composition, not an original
save format. `Return (test)` does not perform approach, collision, docking,
fuel, legal-status, reward or full universe object updates. `Jump (test)` is a
bounded fuel/coordinates/market composition, not the original hyperspace
countdown or miss-jump path. `Approach (test)` and `Dock (test)` are likewise a
new bounded corridor, not original collision or station docking logic. The full original menus, HUD, AI/combat,
persistence, audio and game loop remain future work. This phone result does not
validate ARM32, x86 or x86_64 Android runtime
execution, other Android versions/GPUs, 16KiB-page devices or the original
`elite.app` prototype.
