#!/usr/bin/env python3
"""Remove the OMX.MTK.AUDIO.DECODER.MP2 entry (type audio/mpeg-L2) from the Android 10 vendor codec list.
Android 16's AudioCapabilities does not know that type; system_server then aborts in AudioCapabilities::getDefaultFormat.

Run with the tablet in TWRP and adb connected:   python3 fix_vendor_codecs.py
Edits /vendor/etc/media_codecs_mediatek_audio.xml in place (keeps the SELinux label) and keeps a .orig-mp2 copy next to it."""
import re, subprocess, sys, tempfile, os
ADB = os.environ.get('ADB', 'adb')
XML = '/mnt/v/etc/media_codecs_mediatek_audio.xml'
def sh(*a): return subprocess.run([ADB, 'shell', *a], capture_output=True, text=True)
sh('mkdir -p /mnt/v; umount /mnt/v')
r = sh('mount -t ext4 -o rw /dev/block/by-name/vendor /mnt/v && echo ok')
if 'ok' not in r.stdout: sys.exit('cannot mount vendor rw (are you in TWRP?): ' + r.stderr)
text = sh('cat ' + XML).stdout
new, n = re.subn(r'\n\s*<MediaCodec name="OMX\.MTK\.AUDIO\.DECODER\.MP2"[^>]*>.*?</MediaCodec>\n', '\n', text, flags=re.S)
if n == 0: print('MP2 entry not present, nothing to do'); sh('umount /mnt/v'); sys.exit(0)
with tempfile.NamedTemporaryFile('w', suffix='.xml', delete=False) as f: f.write(new)
subprocess.run([ADB, 'push', f.name, '/tmp/codecs.xml'], check=True, capture_output=True)
sh(f'cp {XML} {XML}.orig-mp2; cat /tmp/codecs.xml > {XML}; sync; umount /mnt/v')
print('removed OMX.MTK.AUDIO.DECODER.MP2; backup at', XML + '.orig-mp2')
