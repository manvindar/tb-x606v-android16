# ROM Tuner

ROM Tuner is a settings app for the TB-X606V ROMs (Android 14 and 16). It groups the performance, memory, display and behaviour options of this tablet in one place and applies them through a small root daemon. It builds without Gradle.

<img src="docs/icon-light.png" width="96"> <img src="docs/icon-dark.png" width="96">

The launcher icon is a flat performance dial with a light and a dark variant. The dark variant (`res/drawable-night`) is used when the system is in dark mode, and a monochrome layer supports themed icons. The sources are `assets/icon.svg` and `assets/icon-dark.svg`.

## Features

The app opens from the launcher and from the top of the Settings homepage. A "Performance" quick settings tile cycles through the battery, balanced and performance profiles.

| Section | Options |
|---|---|
| Presets | Battery saver, Balanced, Smooth, Performance, Extreme |
| Performance | Profile (battery, balanced, performance), GPU clock, maximum clock for the fast and the efficiency cores |
| Tuning | Overclock, CPU governor, thermal limits, I/O scheduler, read-ahead, swappiness, TCP congestion control, foreground and top-app boost, big-task rotation, cache pressure, write-back threshold, free memory reserve, swap read-ahead |
| Appearance | Dark mode, pure black, accent colour, palette style, font, icon shape and icon pack (the last three only when the ROM ships those overlays) |
| Display and system UI | Navigation mode, animation speed, display size, boot animation |
| Behaviour | Screen timeout, font size, stay awake while charging, auto-rotate, show taps, window blur, private DNS, cached app limit, Treble audio effects, ADB over network, Bluetooth MediaTek workaround |
| Actions | Restart System UI, reboot, shortcuts to LineageOS settings screens and Dolby Atmos |

A fresh install starts on the Smooth preset.

## Presets

The presets were measured on the tablet with a scripted fling through the Settings app (janky frames from `dumpsys gfxinfo`) and an eight-thread compute loop.

| Preset | Janky frames | 95th percentile frame time |
|---|---|---|
| Battery saver | 7.4% | 34 ms |
| Balanced | 2.8% | 19 ms |
| Performance profile alone | 2.6% | 23 ms |
| Smooth (foreground boost 60, top-app boost 30, larger free-memory reserve) | 1.7% | 18 ms |
| Performance (boosts 100 and 70, larger write buffer) | 1.1% | 22 ms |

Extreme adds pinned top clocks, the performance governor and no thermal limit. It gave no smoother UI than Performance in these tests and runs hotter. Big-task rotation made the compute loop 20 to 30 percent slower, so no preset turns it on.

## How it works

The app never writes to the system itself. It appends `key=value` lines to `files/request` in its own data directory. The daemon in `device/tablet_tunerd.sh` watches that directory with `inotifyd`, checks each value against a strict character set, applies it, and keeps the master state in `/data/local/tmp/tuner.state`. A copy of the state and the lists of available governors, GPU clocks, schedulers and fonts go back to the app. The log is `/data/local/tmp/tuner.log`.

The MediaTek power HAL rewrites the power mode, the boosts and the minimum clock limits on touch boosts and when the screen changes state. The daemon compares those values every five seconds and writes its own back when they differ. When it sets a profile it writes `cpufreq_power_mode` before the clock limits, because the sports mode resets them.

The daemon also keeps the Treble Settings audio effects switched off. Those effects attach to every playback session and cut app audio after a few milliseconds on this tablet. The "Treble audio effects" switch lets you leave them alone.

The daemon runs as `u:r:su:s0`, so it needs a userdebug ROM or permissive SELinux.

## Build

Requirements: the Android SDK (build-tools 35, platform 34) and a JDK.

```sh
sh build.sh
```

The script runs aapt2, javac, d8, zipalign and apksigner and writes `RomTuner.apk`. It generates a throwaway signing key, `tuner.keystore`, on the first run and does not commit it. An APK signed with a different key must replace the system copy, or the old app must be uninstalled first.

## Install as a system app

```sh
adb root
adb shell mount -o remount,rw /
adb shell mkdir -p /system/app/RomTuner
adb push RomTuner.apk /system/app/RomTuner/
adb push device/tablet_tunerd.sh /system/bin/
adb push device/tablet_tunerd.rc /system/etc/init/
adb shell 'chmod 755 /system/bin/tablet_tunerd.sh; chmod 644 /system/app/RomTuner/RomTuner.apk /system/etc/init/tablet_tunerd.rc'
adb shell 'restorecon -R /system/app/RomTuner /system/bin/tablet_tunerd.sh /system/etc/init/tablet_tunerd.rc'
adb reboot
```

The boot animation option copies one of `bootanimation-arrow.zip`, `bootanimation-galaxy.zip`, `bootanimation-aurora.zip` or `bootanimation-stock.zip` from `/system/product/media` over `bootanimation.zip`. The image builder installs the arrow, stock and (with `--galaxy`) galaxy files; the aurora file is not in this repository. `boot-animation/bootanimation-arrow.zip` is the ArrowOS animation, and `boot-animation/make_galaxy.py` renders the galaxy one. `build/build_image.py` in the parent directory installs all of this into a system image, so a manual install is only needed on a running ROM.
