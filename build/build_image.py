#!/usr/bin/env python3
"""Build a flashable Android 16 system image for the Lenovo TB-X606V from a LineageOS 23.2 GSI (arm64 A/B 'bgN' ext4, raw).

usage: build_image.py --gsi LineageOS-23.2-20260524-GAPPS-EXT4-GSI.img --out system.img [--e2fs DIR] [--no-debug] [--no-bootdiag]
                      [--with-tuner [--galaxy galaxy.zip]] [--dolby DIR]

Steps: copy -> grow to the 0xf3800000-byte system partition -> e2fsck/resize2fs -> patch inside the ext4 with debugfs
(no mount needed, works on macOS): init boots SELinux permissive, debug props, optional boot diagnostics service."""
import argparse, hashlib, os, shutil, subprocess, sys, tempfile
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
PART = 0xf3800000
ap = argparse.ArgumentParser()
ap.add_argument('--gsi', required=True); ap.add_argument('--out', required=True)
ap.add_argument('--e2fs', default='/opt/homebrew/opt/e2fsprogs/sbin', help='dir with debugfs, e2fsck, resize2fs')
ap.add_argument('--no-debug', action='store_true', help='keep ro.debuggable=0 / ro.secure=1')
ap.add_argument('--no-bootdiag', action='store_true')
ap.add_argument('--with-tuner', action='store_true', help='ROM Tuner app + root daemon + boot animations (ArrowOS default)')
ap.add_argument('--galaxy', help='bootanimation zip from rom-tuner/boot-animation/make_galaxy.py (optional extra choice)')
ap.add_argument('--dolby', help='dir with DaxUI.apk, daxService.apk, com.dolby.daxservice.xml (proprietary, not in this repo)')
a = ap.parse_args()
E = lambda t: os.path.join(a.e2fs, t)
def run(*c, ok=(0,)):
    r = subprocess.run(c, capture_output=True, text=True)
    if r.returncode not in ok: sys.exit(f'{c[0]} failed ({r.returncode}):\n{r.stdout}{r.stderr}')
    return r.stdout
def dbg_cat(path): return subprocess.run([E('debugfs'), '-R', f'cat {path}', a.out], capture_output=True).stdout

if open(a.gsi, 'rb').read(4) == bytes.fromhex('3aff26ed'): sys.exit('sparse image: convert to raw first (simg2img)')
print('copying GSI ...'); shutil.copyfile(a.gsi, a.out)
with open(a.out, 'r+b') as f: f.truncate(PART)
run(E('e2fsck'), '-fy', a.out, ok=(0, 1)); run(E('resize2fs'), a.out); run(E('e2fsck'), '-fn', a.out)

tmp = tempfile.mkdtemp(); cmds = []; lbl = {}
def label(name, s):
    p = os.path.join(tmp, name); open(p, 'wb').write(s.encode() + b'\0'); lbl[s] = p; return p
SYS, INIT = 'u:object_r:system_file:s0', 'u:object_r:init_exec:s0'
def put(src, dst, mode, gid, ctx):
    p = lbl.get(ctx) or label(ctx.split(':')[2], ctx)
    cmds.extend([f'rm {dst}', f'write {src} {dst}', f'set_inode_field {dst} mode 0100{mode}', f'set_inode_field {dst} uid 0',
                 f'set_inode_field {dst} gid {gid}', f'ea_set -f {p} {dst} security.selinux'])

# 1. init: permissive by default, original kept as init.enforcing
init = os.path.join(tmp, 'init'); open(init, 'wb').write(dbg_cat('/system/bin/init'))
init_p = os.path.join(tmp, 'init.p'); run(sys.executable, os.path.join(ROOT, 'patches', 'patch_init.py'), init, init_p)
put(init, '/system/bin/init.enforcing', '755', 0, SYS); put(init_p, '/system/bin/init', '755', 2000, INIT)

# 2. build.prop: Bluetooth workaround default + (optionally) debug props so adb works from the first boot
# "Phh Treble Settings > Misc > Bluetooth workaround > MediaTek" only does setprop persist.sys.bt.unsupported.commands 182
# (found by disassembling TrebleApp). Shipping it as the persist default makes it the ROM default; a value stored in /data still wins.
bp = dbg_cat('/system/build.prop').decode()
bp += '\n# MediaTek controller: mark HCI command 182 unsupported (same as the Treble app MediaTek option)\npersist.sys.bt.unsupported.commands=182\n'
if not a.no_debug:
    for x, y in (('ro.secure=1', 'ro.secure=0'), ('ro.debuggable=0', 'ro.debuggable=1'), ('ro.force.debuggable=0', 'ro.force.debuggable=1')):
        assert x in bp, x; bp = bp.replace(x, y)
    bp += 'ro.adb.secure=0\npersist.sys.usb.config=adb\n'
p2 = os.path.join(tmp, 'build.prop'); open(p2, 'w').write(bp); put(p2, '/system/build.prop', '600', 0, SYS)

def mkdir(path, ctx=SYS):
    p = lbl.get(ctx) or label(ctx.split(':')[2], ctx)
    cmds.extend([f'mkdir {path}', f'set_inode_field {path} mode 040755', f'set_inode_field {path} uid 0', f'set_inode_field {path} gid 0',
                 f'ea_set -f {p} {path} security.selinux'])
LIB = 'u:object_r:system_lib_file:s0'

# 2b. libhwui: Android 16 seals bitmap memfds with F_SEAL_FUTURE_WRITE (Linux 5.1+); on kernel 4.9 every bitmap parcel over 16 KB fails
for lib in ('/system/lib64/libhwui.so', '/system/lib/libhwui.so'):
    src = os.path.join(tmp, lib.replace('/', '_')); open(src, 'wb').write(dbg_cat(lib))
    run(sys.executable, os.path.join(ROOT, 'patches', 'patch_libhwui.py'), src, src + '.p')
    put(src + '.p', lib, '644', 0, LIB)

if a.with_tuner:
    R = os.path.join(ROOT, 'rom-tuner'); media = '/system/product/media'
    mkdir('/system/app/RomTuner')
    put(os.path.join(R, 'RomTuner.apk'), '/system/app/RomTuner/RomTuner.apk', '644', 0, SYS)
    put(os.path.join(R, 'device', 'tablet_tunerd.sh'), '/system/bin/tablet_tunerd.sh', '755', 0, SYS)
    put(os.path.join(R, 'device', 'tablet_tunerd.rc'), '/system/etc/init/tablet_tunerd.rc', '644', 0, SYS)
    stock = os.path.join(tmp, 'stock.zip'); open(stock, 'wb').write(dbg_cat(media + '/bootanimation.zip'))
    arrow = os.path.join(R, 'boot-animation', 'bootanimation-arrow.zip')
    put(stock, media + '/bootanimation-stock.zip', '644', 0, SYS); put(arrow, media + '/bootanimation-arrow.zip', '644', 0, SYS)
    put(arrow, media + '/bootanimation.zip', '644', 0, SYS)          # ArrowOS animation is the default; the Tuner copies the chosen one over this file
    if a.galaxy: put(a.galaxy, media + '/bootanimation-galaxy.zip', '644', 0, SYS)

if a.dolby:
    for d_, f_ in (('/system/app/DaxUI', 'DaxUI.apk'), ('/system/product/priv-app/daxService', 'daxService.apk')):
        mkdir(d_); put(os.path.join(a.dolby, f_), d_ + '/' + f_, '644', 0, SYS)
    put(os.path.join(a.dolby, 'com.dolby.daxservice.xml'), '/system/product/etc/permissions/com.dolby.daxservice.xml', '644', 0, SYS)

# 3. boot diagnostics (logs to /cache/bootdiag, readable from TWRP)
if not a.no_bootdiag:
    put(os.path.join(ROOT, 'debug', 'bootdiag.sh'), '/system/bin/bootdiag.sh', '755', 0, SYS)
    put(os.path.join(ROOT, 'debug', 'zz_bootdiag.rc'), '/system/etc/init/zz_bootdiag.rc', '644', 0, SYS)

open(os.path.join(tmp, 'cmds'), 'w').write('\n'.join(cmds) + '\n')
out = run(E('debugfs'), '-w', '-f', os.path.join(tmp, 'cmds'), a.out)
run(E('e2fsck'), '-fn', a.out)
print('image:', a.out, os.path.getsize(a.out), 'bytes, sha256', hashlib.sha256(open(a.out, 'rb').read()).hexdigest())
print('next: ./flash.sh', a.out, '   then in TWRP: python3 patches/fix_vendor_codecs.py')
