#!/system/bin/sh
# Boot diagnostics: everything lands in /data/local/tmp/bootdiag (readable from TWRP / adb).
D=/data/local/tmp/bootdiag; mkdir -p $D; chmod 777 $D
logcat -b all -v threadtime -f $D/logcat.txt -r 16384 -n 4 &
cat /proc/kmsg >> $D/kmsg.txt 2>&1 &
while :; do
  getprop > $D/props.txt
  ps -A -o PID,PPID,STAT,NAME > $D/ps.txt
  dmesg 2>/dev/null | tail -300 > $D/dmesg.tail.txt
  sleep 5
done
