#!/bin/sh
# usage: ./flash.sh system.img [vendor.img]     (tablet in the bootloader: hold Power + Volume Down while it is off)
# Flashes `system` only. Never touches recovery (TWRP), boot, vendor or vbmeta. Wipes userdata, metadata and cache.
set -e
F=${FASTBOOT:-fastboot}
[ -f "$1" ] || { echo "usage: $0 system.img [vendor.img]"; exit 1; }
$F devices | grep -q fastboot || { echo "no device in fastboot mode"; exit 1; }
$F flash system "$1"
[ -f "$2" ] && $F flash vendor "$2"   # optional: a vendor image that already has the codec fix
$F format:ext4 userdata
$F erase metadata
$F erase cache
echo "done. Run patches/fix_vendor_codecs.py from TWRP (Volume Up + Volume Down) BEFORE the first Android boot, or it will boot-loop."
