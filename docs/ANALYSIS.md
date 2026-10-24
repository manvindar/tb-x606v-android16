# Boot hang analysis (Android 16 on MT8768 / Android 10 vendor)

Captured with `debug/bootdiag.sh` (logcat to `/cache`), read from TWRP.

Symptoms: boot animation forever, no USB enumeration (USB debugging off after a wipe), `marker_boot_completed` never written.

logcat shows 21 `Entered the Android system server!` lines in ~5 min, each ended by
`Zygote: Process <pid> exited due to signal 6 (Aborted)` / `Exit zygote because system server has terminated`.

Tombstone of `system_server` (thread `StorageManagerS`):
```
Abort message: 'ubsan: sub-overflow by 0x000000757f323e14'
#02 libmedia_codeclist_capabilities.so __ubsan_handle_sub_overflow_minimal_abort
#03 libmedia_codeclist_capabilities.so android::AudioCapabilities::getDefaultFormat(...)
#05 libmedia_codeclist.so MediaCodecInfoWriter::BuildCodecCapabilities
#07 libstagefright.so OmxInfoBuilder::buildMediaCodecList
#11 libmedia_jni.so getCodecList
#17 services.jar StorageManagerService.isHevcDecoderSupported
```
`mediaserver32` aborts the same way. The last codec before each abort: `OMX.MTK.AUDIO.DECODER.MP2` with
`AudioCapabilities: Unsupported mediaType audio/mpeg-L2` (41 times in the log).

Other noise that is not the cause: `Could not find ...IAllocator/default in the VINTF manifest` (falls back to HIDL), binder ioctl -22 for
`BINDER_ENABLE_ONEWAY_SPAM_DETECTION` on the old kernel, cgroup `+memory` activation failure, missing `/dev/cpuset/foreground`.

Dead ends: SELinux boot cmdline override (ignored by this bootloader), `write /sys/fs/selinux/enforce` from init rc (denied), a `su`-domain
logging service under enforcing SELinux (never started). Patching init made SELinux permissive and let the logging service run.
