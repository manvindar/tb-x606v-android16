#!/usr/bin/env python3
"""Make Android 16 (LineageOS 23.2 GSI, 2026-05-24) init boot SELinux permissive by default.
usage: patch_init.py <init-in> <init-out>
Changes `mov w21,#1` -> `mov w21,#0` at 0x116bc0, where init's inlined IsEnforcing() falls through to "enforcing".
Writing to /system/bin/init from TWRP (mount the system partition rw, `cat patched > init`) keeps its inode and label."""
import hashlib, sys
ORIG_SHA256 = 'f52af0f7cda88bf79bc4b334875259ae5b4f70bac2b0fb59096b5be044fbe60a'
d = bytearray(open(sys.argv[1], 'rb').read())
assert hashlib.sha256(d).hexdigest() == ORIG_SHA256, 'not the expected init build; the offset will differ'
assert d[0x116bc0:0x116bc4].hex() == '35008052'
d[0x116bc0:0x116bc4] = bytes.fromhex('15008052')
open(sys.argv[2], 'wb').write(d)
print('patched ->', sys.argv[2])
