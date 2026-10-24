Patched system image for the Lenovo TB-X606V (Tab M10 FHD Plus, MT8768, Android 10 vendor, kernel 4.9.190): Android 16 from the LineageOS 23.2 GSI (build 2026-05-24, VANILLA, no Google apps).

Status: this pre-release has not been booted on a tablet yet. It is built by the same scripts, with byte-identical patched files, as the GAPPS build that was tested on a TB-X606V.

Changes on top of the GSI: init boots SELinux permissive, a `libhwui` fix for the bitmap parcel crash, the Bluetooth MediaTek workaround as the default, rooted adb from the first boot, ROM Tuner with its root daemon, and the ArrowOS boot animation (the stock and galaxy animations are selectable).

Install (system only; wipes userdata; never flash recovery):
1. Unpack with `xz -d`, then put the tablet in the bootloader (Power and Volume Down while it is off).
2. `./flash.sh system-a16-tb-x606v-vanilla.img` from the repository.
3. Before the first Android boot, boot TWRP (Volume Up and Volume Down) and run `python3 patches/fix_vendor_codecs.py`. Without it system_server aborts on the vendor's MP2 codec entry and the boot animation never ends.
4. Open Treble Settings, Audio effects, and leave every effect off, or app audio cuts out after a few milliseconds. The ROM Tuner daemon does this for you.

Source for the base system: https://github.com/LineageOS. This image contains no Google apps, Dolby apps or Lenovo files.
