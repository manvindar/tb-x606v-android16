# Android 16 for the Lenovo TB-X606V

A build recipe, patch set and prebuilt image that run Android 16 (LineageOS 23.2 GSI) on the Lenovo Tab M10 FHD Plus (TB-X606V). Flashing it replaces the system partition and needs an unlocked bootloader. The tablet keeps its stock Android 10 vendor image and its 4.9.190 kernel. Only the `system` partition is replaced.

Lenovo stopped at Android 10 for this tablet. A stock GSI of Android 16 boots to the boot animation and stays there, and the apps that do start crash or lose their audio. This repository contains the fixes found while debugging that, the scripts that apply them, and a GitHub Actions workflow that builds the image from the public GSI.

## Device

| | |
|---|---|
| Model | Lenovo TB-X606V (Tab M10 FHD Plus, 2nd gen) |
| SoC | MediaTek MT8768, PowerVR GE8320 |
| Memory | 3.8 GB RAM, 3.8 GiB `system` partition (0xf3800000 bytes) |
| Base | Android 10 vendor, kernel 4.9.190, A-only, bootloader unlocked |
| Recovery | TWRP in `recovery`. Nothing in this repository touches it. |
| ROM base | LineageOS 23.2 EXT4 GSI, build 2026-05-24 (MisterZtr), vanilla or GAPPS |

## Status

Verified on the tablet: boots to the launcher, root adb from the first boot, Wi-Fi, Bluetooth (with the MediaTek workaround), speaker audio, YouTube playback with hardware H.264 decoding, and the ROM Tuner app.

The prebuilt vanilla image is assembled with the same scripts and byte-identical patched files as the GAPPS build that was tested, but it has not been booted on a tablet yet, so the release is marked as a pre-release. Please report the result in an issue.

Not verified: camera, 5 GHz Wi-Fi throughput, DRM level for streaming apps, cellular on LTE variants.

The image trades security for convenience: SELinux boots permissive, `ro.debuggable=1`, and adb accepts any USB host without authorization. Treat it as a personal device ROM.

## What the patches fix

| Problem | Cause | Fix |
|---|---|---|
| Boot animation never ends | `system_server` aborts about two seconds after it starts. Android 16 rejects the vendor codec entry `OMX.MTK.AUDIO.DECODER.MP2` (`audio/mpeg-L2`) and a UBSan check in `AudioCapabilities::getDefaultFormat` kills the process. | `patches/fix_vendor_codecs.py` removes the entry from `/vendor/etc/media_codecs_mediatek_audio.xml`. |
| SELinux denials during boot and no usable logs | The boot command line override `androidboot.selinux=permissive` is ignored by this bootloader. | `patches/patch_init.py` changes one instruction in `/system/bin/init` so it boots permissive. The original is kept as `init.enforcing`. |
| YouTube and other apps crash with "Could not copy bitmap to parcel blob" | `libhwui` seals bitmap memfds with `F_SEAL_FUTURE_WRITE`, which needs Linux 5.1. On kernel 4.9 the call fails, so no bitmap over 16 KB can cross binder. | `patches/patch_libhwui.py` drops that seal in the 64-bit and 32-bit libraries. `docs/BitmapParcelTest.java` reproduces the failure. |
| App audio plays for a few milliseconds, then stops | Treble Settings, Audio effects, attaches five effects to every playback session, including DAP and Dynamics Processing. | The ROM Tuner daemon keeps those switches off. |
| Bluetooth does not work with the default settings | The MediaTek controller reports an HCI command as unknown (opcode 0xc5a on Android 14), which the stock stack does not tolerate. | `persist.sys.bt.unsupported.commands=182` in `build.prop`, the same value the Treble Settings "MediaTek" option sets. |
| YouTube 1080p stutters | The vendor VP9 decoder is a software decoder. | The vendor VP9 entry is removed, so YouTube falls back to hardware H.264. |

`docs/ANALYSIS.md` has the logs and backtraces behind the boot hang.

## Install a prebuilt image

You need a computer with `adb` and `fastboot`, and a USB cable. Flashing wipes the tablet's data.

1. Download `system-a16-tb-x606v-vanilla.img.xz` from the Releases page and unpack it with `xz -d`. It is the LineageOS vanilla GSI plus the patches below, so it has no Google apps.
2. Switch the tablet off, then hold Power and Volume Down to enter the bootloader. Connect it to the computer.
3. Flash:
   ```sh
   ./flash.sh system-a16-tb-x606v-vanilla.img
   ```
   The script writes `system`, then formats userdata and erases metadata and cache. It never writes `recovery`, `boot` or `vbmeta`.
4. Before the first Android boot, boot into TWRP (hold Volume Up and Volume Down) and run `python3 patches/fix_vendor_codecs.py` from the computer. The script edits one file on your tablet's own vendor partition. Without it the tablet stays on the boot animation.
5. Reboot. The first boot takes several minutes.

Back up the stock partitions with TWRP before you flash, so you can return to Android 10 later. To go back, flash a saved `system.img` the same way.

## Build the image

Requirements: Python 3 and e2fsprogs (`debugfs`, `e2fsck`, `resize2fs`). On macOS e2fsprogs is keg-only, so pass `--e2fs /opt/homebrew/opt/e2fsprogs/sbin`.

```sh
# 1. Download LineageOS-23.2-20260524-VANILLA-EXT4-GSI.7z (or the GAPPS variant for Google apps) from the MisterZtr SourceForge project and extract the .img
# 2. Build
python3 build/build_image.py \
  --gsi LineageOS-23.2-20260524-VANILLA-EXT4-GSI.img \
  --out system.img \
  --with-tuner
```

The script copies the GSI, grows it to the partition size, checks the filesystem, and writes the patches inside the ext4 image with `debugfs`. It does not mount anything, so it runs on macOS and Linux without root.

| Option | Effect |
|---|---|
| `--with-tuner` | Installs ROM Tuner, its root daemon and the boot animations. ArrowOS is the default animation. |
| `--galaxy FILE` | Adds the generated galaxy animation as a choice (`rom-tuner/boot-animation/make_galaxy.py` renders it). |
| `--dolby DIR` | Installs Dolby Atmos from `DaxUI.apk`, `daxService.apk` and `com.dolby.daxservice.xml` in `DIR`. These files come from the stock Lenovo firmware and are not in this repository. |
| `--no-debug` | Keeps `ro.debuggable=0` and `ro.secure=1`. |
| `--no-bootdiag` | Leaves out the boot logging service. |
| `--e2fs DIR` | Directory that holds the e2fsprogs binaries. |

Each patch script checks the SHA-256 of the file it edits and refuses to run on a different build, because the byte offsets would be wrong. The patched files are identical in the vanilla and the GAPPS variant of the 2026-05-24 build.

### Continuous integration

`.github/workflows/build-rom.yml` downloads the vanilla GSI, verifies its checksum, renders the galaxy animation, builds the image with `--with-tuner`, compresses it and uploads it as an artifact. Start it from the Actions tab. Tick "publish" or push a `rom-v*` tag to attach the image to a release. The workflow output has no Google apps, no Dolby apps and no vendor image. To build with the GAPPS variant, change `GSI_URL`, `GSI_7Z_SHA256` and `GSI_IMG` in the workflow, and keep the result to yourself, because it contains Google's proprietary apps.

## Repository layout

| Path | Contents |
|---|---|
| `build/build_image.py` | Image builder |
| `flash.sh` | Fastboot flashing script |
| `patches/` | `patch_init.py`, `patch_libhwui.py`, `fix_vendor_codecs.py` |
| `rom-tuner/` | ROM Tuner app source, root daemon, boot animations and built APK. See its README. |
| `debug/` | Boot logging service that writes to `/cache/bootdiag` |
| `docs/` | Boot hang analysis, bitmap parcel test, release notes |
| `.github/workflows/` | Image build workflow |

## Troubleshooting

The tablet never reaches Android and adb sees nothing. Reboot into TWRP (Volume Up and Volume Down) and read `/cache/bootdiag/logcat.txt*` over adb. `/data` is encrypted and unreadable from TWRP, which is why the logging service writes to `/cache`. The kernel console of the failed boot is in `/sys/fs/pstore/console-ramoops`.

`/` and `/vendor` are read-only again after every boot. Run `adb root; adb shell mount -o remount,rw /` before pushing files, and replace system libraries with a new file followed by `mv`. Overwriting a mapped library in place crashes the processes that use it.

App audio cuts out. Open Treble Settings, Audio effects, and switch every effect off. The ROM Tuner daemon does this automatically unless "Allow Treble audio effects" is enabled.

## Licensing and credits

The scripts, the ROM Tuner app and the documentation are under the Apache License 2.0 (see `LICENSE`). The system image is the LineageOS GSI built by MisterZtr from LineageOS, TrebleDroid and phh-treble sources, with the patches from this repository applied. The boot animation `rom-tuner/boot-animation/bootanimation-arrow.zip` comes from ArrowOS. Dolby Atmos, Google apps and the Lenovo vendor image belong to their owners. This repository does not distribute them, and the `--dolby` build option only installs files you supply yourself.

This is an unofficial project and has no connection to Lenovo, Google, LineageOS or ArrowOS. You flash it at your own risk.
