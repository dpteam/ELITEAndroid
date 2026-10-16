# Android 13 ARM64 preview device smoke test

Date: 2026-10-16. Result: **bounded smoke test passed after runtime fixes**.
This is evidence for `elite.preview`, not the original `elite.app` or a complete
universal Elite game. No original game installation or saves were modified.

## Target and exact final artifact

- Phone: UNISOC E5, Android 13, API 33.
- Reported supported ABIs: arm64-v8a, armeabi-v7a, armeabi.
- Installed package `primaryCpuAbi`: **arm64-v8a**. This is not a 64-bit-only phone.
- Display: 320x480 physical, 480x320 landscape; density override 150 dpi.
- Kernel page size: **4096**. Static 16KiB alignment is not runtime testing on a
  16KiB-page device.
- Package: `elite.preview`, version `0.2-device-test`, code 2.
- APK: `preview/build/outputs/apk/debug/preview-debug.apk`.
- SHA-256: `59944a641b83fb347295255816b14157831e9e19ca018e492aa50f849e02daa1`.
- Debug signature verified with SDK apksigner; SDK zipalign `-c -P 16 4` passed.
- `preview-apk.json` checks all eight packaged ELF files: four genuine ABIs,
  no TEXTREL, no writable-executable segments, 16KiB ELF/ZIP alignment.

## Method

Install with `adb -s SERIAL install -r`; no uninstall or data clearing. Launch
through MAIN/LAUNCHER. Use SDK ADB `input tap`/`input swipe` and allow settling
time for this slow phone. Capture real display PNGs with Python
`subprocess.run(..., capture_output=True)` and `adb exec-out screencap -p`, not
PowerShell binary redirection. scrcpy 5.0.1 was a separate read-only viewer
(`--no-control --no-audio`, software desktop rendering); input came from ADB.

For saved-instance restoration: pause Flight, press Home, wait, run
`am kill elite.preview` **only while backgrounded**, verify `pidof` is empty,
then launch MAIN/LAUNCHER again. Repeat with Station on top. This is process
death with a retained task, not `force-stop`, a reboot or persistent save/load.
The final APK's PIDs changed 11011 -> 11243 -> 11501.

## Observed final-APK results

| Check | Observation |
| --- | --- |
| Install/start and native load | Replacement install succeeded; real ARM64 ART/JNI calls and GLES2 output observed. |
| Shader/render modes | `ship0` displayed in shaded, wireframe and solid modes; no crash during the tested transitions. Not an exhaustive GPU parity test. |
| Small-screen layout | Model visible with compact bottom controls; throttle hidden outside Flight. Station prices/stock/buttons fit horizontally and the list scrolls. |
| Flight throttle | Speed changed from default 8 to 28/36 via ADB slider touch. |
| Flight steering | During a 6-second swipe, roll 23 / pitch 6; after release, roll/pitch returned to 0. Reference-star distribution changed. |
| Flight pause/reset | Reset restarted the scene; pause froze ticks. Flying past/away can legitimately cull the target; no collision/respawn is implemented. |
| Flight process recreation | Paused speed 28, range 5242, tick 432 and Flight mode survived. Before/after PNGs are byte-identical, including slider and star positions. New process recreated shaders/context. |
| Station buy Food | Credits 500.0 -> 496.4, stock 16 -> 15, cargo 0 -> 1. |
| Station sell Food | Credits -> 500.0, stock -> 16, cargo -> 0. |
| Station no stock | Buy Slaves rejected with stock 0; balance and cargo unchanged. |
| Station full hold | Nine Food purchases yielded 467.6 credits, cargo 9/9, stock 7. Tenth purchase rejected without extra debit. |
| Station process recreation | Retained Station on top, 467.6 credits, cargo 9/9, Food stock 7. Receipt text is transient and resets, as expected. |
| Runtime error scan | No `FATAL EXCEPTION`, `Fatal signal`, `JNI DETECTED` or `AndroidRuntime` error in collected final-process logs (PIDs 11011, 11243, 11501). This is a bounded log scan, not a stability guarantee. |

## Runtime fixes made in this checkpoint

1. A valid empty mesh previously hit `FloatBuffer.position(3)` with limit 0,
   raising `IllegalArgumentException: Bad position 3/0` on the GL thread. Validate
   packet bounds and skip empty submissions; also skip empty star buffers and
   submit one-vertex commands as GL_POINTS. Host JVM regression tests cover
   empty and malformed packets. This crash was in the new Java test renderer,
   not a discrepancy in the replay-tested native geometry.
2. Preserve Flight model/mode, pause, throttle, camera angles and the owned
   912-byte scene through saved-instance Bundles. No hidden-time catch-up or
   held touch is restored. Station retains its separate 204-byte Bundle state.
3. Compact fullscreen UI and foreground-only KEEP_SCREEN_ON for both screens.
   No device-wide density, sleep, security or developer settings were changed.

## Evidence locations and hashes

Actual PNGs and app-specific logs are local, ignored files under
`build/device-test/`; hashes below identify the observations, not downloadable
files in a fresh source checkout. No personal device serial is stored here.

| PNG | SHA-256 |
| --- | --- |
| `35-final-shaded.png` | `7058bc7138dc8ff54c633891e2f87e3c290acd311c2775ce799acd0e7efdd9e8` |
| `36-final-wireframe.png` | `91185da69759012944c355a504e6e5657343b76c6dc106de5daa60fc8d9c42a9` |
| `38-final-steering.png` | `bef5ef265b21d471cc59295d3d9eb1b3faab2b5969cc94e6a3d204592eb1dd8e` |
| `39-final-paused-before.png` | `3d5b2be79eb1e5a2eec1d563636c807674a3d4dd83cacb14e076a81dedcee090` |
| `40-final-paused-after.png` | `3d5b2be79eb1e5a2eec1d563636c807674a3d4dd83cacb14e076a81dedcee090` |
| `42-final-buy.png` | `54e419b2e99716dce5ed2ef805797a11360049c48a80b2af072054b15a7dfab7` |
| `43-final-sell.png` | `a448ef88d25dbf45caec9f70dbbf3f216b5fb84639561724d9881ffc82333002` |
| `44-final-no-stock.png` | `6f2b79fa46034397ae6bcafe7588ee71dabe6502aa8871f87deabc82c3906c5c` |
| `45-final-full-hold.png` | `b4fa76a0a12e2505011963c286c726f7b947d5b2976bb6a6e0ed8892357ca8ee` |
| `46-final-station-restored.png` | `850fe150cee8f04567bc8468a59690e77079b33f8fbca49439ee45bb8d0dd35c` |

## Non-device regressions rerun against this exact APK

- 2376 mesh command comparisons: 66 models x 3 poses x 3 modes x 4 ABIs.
- Flight per ABI: 15 profile comparisons, 3000 control ticks, 300 individual
  movement ticks, 1200 continuous movement ticks and a 600-tick test-scene trace;
  cross-ABI scene traces agree.
- Station per ABI: all 2048 market combinations, 256 galaxy systems/names/info,
  53 nearest-system queries and 1000 sampled transactions.
- Corresponding `preview-packaged-{mesh,flight,station}.json` reports record
  the same final APK digest. These are bounded Unicorn comparisons, not actual
  ARM32/x86/x86_64 Android runtime execution.

## Still unvalidated / incomplete

Other Android versions, other GPUs, real ARM32/x86/x86_64 execution,
64-bit-only installs, 16KiB-page devices, exhaustive on-device model/mode
coverage, low-memory stress, long sessions, lock/unlock/reboot/task-removal
behavior and full-game persistence/audio remain outside this checkpoint.
Flight and Station still have independent snapshots; no launch/docking,
universe/AI/combat, original HUD, missions or original save compatibility is
claimed. The separate original-library `app` prototype was **not** tested here.
