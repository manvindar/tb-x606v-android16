#!/usr/bin/env python3
"""Fix "Could not copy bitmap to parcel blob" (YouTube and any app that sends a bitmap over 16 KB through binder crash) on kernel 4.9.

Android 16's libhwui seals the memfd behind a parceled bitmap with F_SEAL_SHRINK|F_SEAL_GROW|F_SEAL_FUTURE_WRITE. F_SEAL_FUTURE_WRITE needs
Linux 5.1, so on the TB-X606V's 4.9.190 kernel fcntl(F_ADD_SEALS) returns EINVAL and the parcel write fails. This drops that seal for immutable
bitmaps (the mutable case already used 6).

usage: patch_libhwui.py <libhwui.so in> <out>      (arm64 /system/lib64 or arm /system/lib; LineageOS 23.2 2026-05-24 build)
arm64: 0x5ba21c `mov w8,#0x16` -> `mov w8,#6`      arm (thumb): 0x17a02c `movs r2,#0x16` -> `movs r2,#6`
Write the result with `cp new libhwui.so.new; mv` (a new inode), never in place: running processes map the old file."""
import hashlib, sys
KNOWN = {  # sha256 of the unpatched library -> (offset, expected bytes, replacement)
    '40524b354fe4c14e42a2580dda763d0a9616d7f18f50f247d2e4404830e4ca58': (0x5ba21c, 'c8028052', 'c8008052'),  # arm64
    'f9b8cc723e00f19d1d2d934fa240851fab261e4b3e01618b8cfda96b6f1d31c3': (0x17a02c, '1622', '0622'),          # arm
}
d = bytearray(open(sys.argv[1], 'rb').read())
h = hashlib.sha256(d).hexdigest()
if h not in KNOWN: sys.exit('unknown libhwui build (sha256 %s): the patch offset would differ' % h)
off, old, new = KNOWN[h]
assert d[off:off + len(old) // 2].hex() == old
d[off:off + len(new) // 2] = bytes.fromhex(new)
open(sys.argv[2], 'wb').write(d)
print('patched', sys.argv[2])
