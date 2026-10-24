#!/system/bin/sh
# ROM Tuner root daemon. The app writes "key=value" lines to <app files>/request; this applies them and keeps the
# master state in /data/local/tmp/tuner.state (survives "clear data"). Modes: no args = daemon, args = inotifyd handler.
S=/data/local/tmp/tuner.state
APP=/data/data/org.tablet.tuner/files
LOCK=/data/local/tmp/tuner.lock
M=/system/product/media
BLACK=org.lineageos.overlay.customization.blacktheme
log() { echo "$(date +%T) $*" >> /data/local/tmp/tuner.log; }

# ---------- state ----------
ovl() { cmd overlay list --user 0 | grep -qE "[ .]$1\$"; }   # is this theme overlay installed on this ROM?
seed() {
cat > $S <<EOS
perf_profile=balanced
preset=balanced
gpu_freq=680000
governor=schedplus
oc=0
thermal=stock
io_sched=deadline
readahead=512
swappiness=100
tcp_cc=$(cat /proc/sys/net/ipv4/tcp_congestion_control)
cap_big=-1
cap_little=-1
night=yes
black_theme=1
palette_source=preset
accent=3E91FF
theme_style=TONAL_SPOT
font=$(ovl font.lato && echo lato || echo default)
icon_shape=$(ovl icon.squircle && echo squircle || echo default)
icon_pack=$(ovl icon_pack.rounded.android && echo rounded || echo default)
nav_mode=gestural
anim_scale=1.0
density=192
bootanim=arrow
logging=0
EOS
  for kv in $(presets smooth); do putkey ${kv%%=*} ${kv#*=}; done; putkey preset smooth
}
load() {
  . $S
  [ -z "$gpu_freq" ] && gpu_freq=$([ "$gpu_lock" = 0 ] && echo 0 || echo 680000)
  : ${fg_boost:=auto} ${ta_boost:=0} ${big_rotation:=0} ${vfs_cache:=50} ${dirty:=1} ${extra_free:=49152} ${page_cluster:=0}
  : ${phh_effects:=0} ${governor:=schedplus} ${oc:=0} ${thermal:=stock} ${io_sched:=deadline} ${readahead:=512} ${swappiness:=100} ${tcp_cc:=bic}
}
putkey() { grep -v "^$1=" $S > $S.t; echo "$1=$2" >> $S.t; mv $S.t $S; }
publish() {   # copy state (+ a status line) to the app; the file is pre-created with the app's owner/context
  [ -f $APP/state ] || return
  gpu_eff=0; [ "$gpu_freq" != 0 ] && [ "$perf_profile" != battery ] && gpu_eff=1
  { cat $S; echo "govs=$(tr ' ' , < /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors)"; echo "gpu_opps=$(sed -n 's/.*freq = \([0-9]*\),.*/\1/p' /proc/gpufreq/gpufreq_opp_dump | tr '\n' ,)"; echo "io_scheds=$(tr -d '[]' < /sys/block/mmcblk0/queue/scheduler | tr ' ' ,)"; echo "tcps=$(tr ' ' , < /proc/sys/net/ipv4/tcp_available_congestion_control)"; echo "fonts=$(cmd overlay list --user 0 | grep -oE "[A-Za-z0-9_.]*\\.font\\.[a-z0-9]+\$" | sed "s/.*font\\.//" | sort -u | tr "\\n" ,)"; ovl "icon\\.squircle" && echo has_shapes=1 || echo has_shapes=0; ovl "icon_pack\\.rounded\\.android" && echo has_packs=1 || echo has_packs=0; echo "status=$perf_profile, GPU $([ "$gpu_eff" = 1 ] && echo locked $((gpu_freq/1000)) MHz || echo auto)"; } > $APP/state
}

# ---------- appliers ----------
w() { echo "$2" > "$1" 2>/dev/null || log "FAIL $1"; }
calc() {
  case $perf_profile in
    battery)     pm=1 fg=0  minb=-1      minl=-1;;
    performance) pm=3 fg=40 minb=2009000 minl=1491000;;
    *)           pm=0 fg=20 minb=1484000 minl=1050000;;
  esac
  set -- $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies); topb=$1
  set -- $(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_available_frequencies); topl=$1
  [ "$oc" = 1 ] && { minb=$topb; minl=$topl; }
  [ "$fg_boost" != auto ] && fg=$fg_boost
  gpu_eff=0; [ "$gpu_freq" != 0 ] && [ "$perf_profile" != battery ] && gpu_eff=1
  [ "$cap_big" != -1 ]    && [ "$minb" -gt "$cap_big" ]    && minb=$cap_big
  [ "$cap_little" != -1 ] && [ "$minl" -gt "$cap_little" ] && minl=$cap_little
}
profile() {
  calc
  w /proc/cpufreq/cpufreq_power_mode $pm
  w /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost $fg
  w /proc/ppm/policy/userlimit_min_cpu_freq "0 -1"; w /proc/ppm/policy/userlimit_min_cpu_freq "1 -1"
  w /proc/ppm/policy/userlimit_max_cpu_freq "0 $cap_big"; w /proc/ppm/policy/userlimit_max_cpu_freq "1 $cap_little"
  w /proc/ppm/policy/userlimit_min_cpu_freq "0 $minb"; w /proc/ppm/policy/userlimit_min_cpu_freq "1 $minl"
  if [ "$gpu_eff" = 1 ]; then w /proc/gpufreq/gpufreq_opp_freq $gpu_freq; else w /proc/gpufreq/gpufreq_opp_freq 0; fi
}
ext() {   # $1 key, loaded state
  case $1 in
    screen_timeout) settings put system screen_off_timeout $screen_timeout;;
    font_scale) settings put system font_scale $font_scale;;
    stay_awake) settings put global stay_on_while_plugged_in $stay_awake;;
    auto_rotate) settings put system accelerometer_rotation $auto_rotate;;
    show_taps) settings put system show_touches $show_taps;;
    window_blur) settings put global disable_window_blurs $window_blur;;
    dns) case $dns in
           off) settings put global private_dns_mode off;;
           auto) settings put global private_dns_mode opportunistic;;
           *) case $dns in google) h=dns.google;; cloudflare) h=one.one.one.one;; adguard) h=dns.adguard-dns.com;; quad9) h=dns.quad9.net;; esac
              settings put global private_dns_specifier $h; settings put global private_dns_mode hostname;;
         esac;;
    bg_procs) if [ "$bg_procs" = default ]; then device_config delete activity_manager max_cached_processes; else device_config put activity_manager max_cached_processes $bg_procs; fi;;
    adb_tcp) if [ "$adb_tcp" = 1 ]; then setprop service.adb.tcp.port 5555; else setprop service.adb.tcp.port -1; fi; stop adbd; start adbd;;
    bt_work) if [ "$bt_work" = 1 ]; then setprop persist.sys.bt.unsupported.commands 182; else setprop persist.sys.bt.unsupported.commands ""; fi
             svc bluetooth disable; sleep 2; svc bluetooth enable;;
  esac
}
# first run: take the current system values as the state, so the app shows what is really set
seedlive() {
  has() { grep -q "^$1=" $S; }
  v() { [ "$1" = null ] || [ -z "$1" ] && echo "$2" || echo "$1"; }
  has screen_timeout || putkey screen_timeout "$(v "$(settings get system screen_off_timeout)" 60000)"
  has font_scale || putkey font_scale "$(v "$(settings get system font_scale)" 1.0)"
  has stay_awake || putkey stay_awake "$(v "$(settings get global stay_on_while_plugged_in)" 0)"
  has auto_rotate || putkey auto_rotate "$(v "$(settings get system accelerometer_rotation)" 1)"
  has show_taps || putkey show_taps "$(v "$(settings get system show_touches)" 0)"
  has window_blur || putkey window_blur "$(v "$(settings get global disable_window_blurs)" 0)"
  has dns || { m=$(settings get global private_dns_mode); case $m in off) d=off;; hostname) d=auto;; *) d=auto;; esac; putkey dns $d; }
  has bg_procs || putkey bg_procs "$(v "$(device_config get activity_manager max_cached_processes)" default)"
  has adb_tcp || putkey adb_tcp "$([ "$(getprop service.adb.tcp.port)" -gt 0 ] 2>/dev/null && echo 1 || echo 0)"
  has bt_work || putkey bt_work "$([ -n "$(getprop persist.sys.bt.unsupported.commands)" ] && echo 1 || echo 0)"
}
cpm() { case $1 in 0) echo 'Default(Normal) mode';; 1) echo 'Low Power mode';; 2) echo 'Just Make mode';; 3) echo 'Performance(Sports) mode';; esac; }
presets() {   # $1 name -> state keys
  case $1 in
    battery)     echo "perf_profile=battery gpu_freq=0 oc=0 governor=schedplus thermal=stock fg_boost=auto ta_boost=0 big_rotation=0 cap_big=1484000 cap_little=1050000 dirty=5 swappiness=100 vfs_cache=100 extra_free=24576";;
    balanced)    echo "perf_profile=balanced gpu_freq=680000 oc=0 governor=schedplus thermal=stock fg_boost=auto ta_boost=0 big_rotation=0 cap_big=-1 cap_little=-1 dirty=1 swappiness=100 vfs_cache=50 extra_free=49152";;
    smooth)      echo "perf_profile=performance gpu_freq=680000 oc=0 governor=schedplus thermal=stock fg_boost=60 ta_boost=30 big_rotation=0 cap_big=-1 cap_little=-1 dirty=10 swappiness=100 vfs_cache=50 extra_free=98304";;
    performance) echo "perf_profile=performance gpu_freq=680000 oc=0 governor=schedplus thermal=stock fg_boost=100 ta_boost=70 big_rotation=0 cap_big=-1 cap_little=-1 dirty=20 swappiness=60 vfs_cache=50 extra_free=98304";;
    extreme)     echo "perf_profile=performance gpu_freq=680000 oc=1 governor=performance thermal=off fg_boost=100 ta_boost=70 big_rotation=0 cap_big=-1 cap_little=-1 dirty=20 swappiness=60 vfs_cache=50 extra_free=98304 readahead=1024";;
  esac
}
tune() {   # governor, overclock, thermal, I/O, memory, network
  g=$governor; [ "$oc" = 1 ] && g=performance
  for p in 0 4; do w /sys/devices/system/cpu/cpu$p/cpufreq/scaling_governor $g; done
  w /sys/block/mmcblk0/queue/scheduler $io_sched
  w /sys/block/mmcblk0/queue/read_ahead_kb $readahead
  w /proc/sys/vm/swappiness $((swappiness > 100 ? 100 : swappiness))   # kernel 4.9 maximum
  w /proc/sys/net/ipv4/tcp_congestion_control $tcp_cc
  E=/proc/perfmgr/boost_ctrl/eas_ctrl
  w $E/perfserv_ta_boost $ta_boost; w $E/sched_big_task_rotation $big_rotation
  w /proc/sys/vm/vfs_cache_pressure $vfs_cache
  w /proc/sys/vm/dirty_ratio $dirty; w /proc/sys/vm/dirty_background_ratio $((dirty > 1 ? dirty / 2 : 1))
  w /proc/sys/vm/extra_free_kbytes $extra_free; w /proc/sys/vm/page-cluster $page_cluster
  if [ "$thermal" = off ]; then w /proc/ppm/policy_status "4 0"; else w /proc/ppm/policy_status "4 1"; fi
}
theme() {
  K=android.theme.customization
  if [ "$palette_source" = home_wallpaper ]; then
    J="{\"$K.color_both\":\"1\",\"$K.color_source\":\"home_wallpaper\""
  else
    J="{\"$K.color_both\":\"0\",\"$K.color_source\":\"preset\",\"$K.system_palette\":\"$accent\",\"$K.accent_color\":\"$accent\""
  fi
  J="$J,\"$K.theme_style\":\"$theme_style\""
  if [ "$font" != default ]; then
    fp=$(cmd overlay list --user 0 | grep -oE "[A-Za-z0-9_.]*\\.font\\.$font\$" | head -1)
    [ -n "$fp" ] && J="$J,\"$K.font\":\"$fp\""
  fi
  [ "$icon_shape" != default ] && ovl "icon\\.$icon_shape" && J="$J,\"$K.adaptive_icon_shape\":\"com.android.theme.icon.$icon_shape\""
  if [ "$icon_pack" != default ] && ovl "icon_pack\\.$icon_pack\\.android"; then
    for t in android launcher settings systemui; do J="$J,\"$K.icon_pack.$t\":\"com.android.theme.icon_pack.$icon_pack.$t\""; done
  fi
  settings put secure theme_customization_overlay_packages "$J,\"_applied_timestamp\":$(date +%s)000}"
  # the black overlay shares the palette category and gets dropped when the theme is rewritten
  ( sleep 6; black ) &
}
black() { cmd overlay $([ "$black_theme" = 1 ] && echo enable || echo disable) --user 0 $BLACK; }
nav() { cmd overlay enable-exclusive --user 0 --category android.theme.customization.navbar \
          com.android.internal.systemui.navbar.$([ "$nav_mode" = threebutton ] && echo threebutton || echo gestural); }
anim() { for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global $k $anim_scale; done; }
density_() { if [ "$density" = reset ]; then wm density reset; else wm density $density; fi; }
bootanim() {
  mount -o remount,rw / 2>/dev/null
  cmp -s $M/bootanimation-$bootanim.zip $M/bootanimation.zip || { cp $M/bootanimation-$bootanim.zip $M/bootanimation.zip; chmod 644 $M/bootanimation.zip; restorecon $M/bootanimation.zip; }
}
logging() {
  if [ "$logging" = 1 ]; then setprop persist.log.tag ""; logcat -b all -G 1M
  else setprop persist.log.tag S; logcat -b all -G 64K; fi
}
night_() { cmd uimode night $([ "$night" = yes ] && echo yes || { [ "$night" = no ] && echo no || echo auto; }); }

# ---------- request handling ----------
handle() {
  while ! mkdir $LOCK 2>/dev/null; do sleep 0.3; done
  trap 'rmdir $LOCK' EXIT
  [ -f $APP/request ] || exit 0
  mv $APP/request $APP/request.work
  load; T=0; P=0; U=0
  while IFS== read -r k v; do
    case $v in ''|*[!A-Za-z0-9._-]*) log "reject $k"; continue;; esac
    log "req $k=$v"
    case $k in preset|restart_ui|reboot|night|black_theme|phh_effects|nav_mode|anim_scale|density|bootanim|logging|palette_source|accent|theme_style|font|icon_shape|icon_pack|screen_timeout|font_scale|stay_awake|auto_rotate|show_taps|window_blur|dns|bg_procs|adb_tcp|bt_work) ;; *) putkey preset custom;; esac
    case $k in
      perf_profile|gpu_lock|gpu_freq|oc|fg_boost|cap_big|cap_little) P=1; U=1; putkey $k $v;;
      palette_source|accent|theme_style|font|icon_shape|icon_pack) T=1; putkey $k $v;;
      governor|thermal|io_sched|readahead|swappiness|tcp_cc|ta_boost|big_rotation|vfs_cache|dirty|extra_free|page_cluster) U=1; putkey $k $v;;
      screen_timeout|font_scale|stay_awake|auto_rotate|show_taps|window_blur|dns|bg_procs|adb_tcp|bt_work) putkey $k $v; load; ext $k; continue;;
      night|black_theme|nav_mode|anim_scale|density|bootanim|logging) putkey $k $v;;
      preset) for kv in $(presets $v); do putkey ${kv%%=*} ${kv#*=}; done; putkey preset $v; P=1; U=1; load; continue;;
      restart_ui) pkill -f com.android.systemui; continue;;
      reboot) sync; reboot; continue;;
      *) log "unknown $k"; continue;;
    esac
    load
    case $k in
      night) night_;; black_theme) black;; nav_mode) nav;; anim_scale) anim;; density) density_;;
      bootanim) bootanim;; logging) logging;;
    esac
  done < $APP/request.work
  rm -f $APP/request.work
  [ $P = 1 ] && profile
  [ $U = 1 ] && tune
  [ $T = 1 ] && theme
  load; publish
}

# ---------- daemon ----------
[ -n "$1" ] && { case $1 in *w*) [ "$3" = request ] && handle;; esac; exit 0; }
rmdir $LOCK 2>/dev/null
[ -f $S ] || seed
until [ -d $(dirname $APP) ]; do sleep 2; done; mkdir -p $APP; chown $(stat -c %u $(dirname $APP)):$(stat -c %u $(dirname $APP)) $APP; chmod 771 $APP; restorecon $APP
UID_=$(stat -c %u $APP)
for f in state request; do [ -f $APP/$f ] || touch $APP/$f; chown $UID_:$UID_ $APP/$f; chmod 660 $APP/$f; restorecon $APP/$f; done
rm -f $APP/request                          # the app recreates it; inotifyd watches the directory
w /proc/ppm/policy_status "2 0"; w /proc/ppm/policy_status "3 0"   # FORCE_LIMIT and PWR_THRO off
w /proc/sys/kernel/printk "1 4 1 7"
seedlive; load; profile; tune; night_; nav; anim; density_; bootanim; [ "$logging" = 1 ] && logging
( sleep 12; load; black; sleep 25; black ) &
# Treble Settings > Audio effects attaches DAP, Dynamics Processing, Loudness Enhancer, Visualizer and Downmix to every playback session; on this tablet
# that cuts app audio after a few ms. Keep them off unless the user allows them (phh_effects=1).
phhfx() {
  [ "$phh_effects" = 1 ] && return
  D=/data/data/me.phh.treble.app; [ -d $D ] || return
  P=$D/shared_prefs/me.phh.treble.app_preferences.xml
  if [ ! -f $P ]; then
    mkdir -p $D/shared_prefs
    { echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"; echo "<map>"
      for u in 93f04452-e4fe-41cc-91f9-e475b6d1d69f e0e6539b-1781-7261-676f-6d7573696340 d069d9e0-8329-11df-9168-0002a5d5c51b 9d4921da-8225-4f29-aefa-39537a04bcaa fa415329-2034-4bea-b5dc-5b381c8d1e2c; do
        echo "    <boolean name=\"audio_effect_$u\" value=\"false\" />"; done; echo "</map>"; } > $P
    o=$(stat -c %u $D); chown -R $o:$o $D/shared_prefs; chmod 660 $P; restorecon -R $D/shared_prefs
  fi
  grep -q 'audio_effect_[0-9a-f-]*" value="true"' $P && { am force-stop me.phh.treble.app; sed -i 's/\(audio_effect_[0-9a-f-]*" value=\)"true"/\1"false"/' $P; log "turned Treble audio effects off"; }
}
# The MTK power HAL rewrites the power mode, boosts and clock limits on touch boosts and screen on/off. Put ours back whenever they drift.
enforce() {
  load; calc; phhfx
  [ "$(cat /proc/cpufreq/cpufreq_power_mode)" != "$(cpm $pm)" ] && w /proc/cpufreq/cpufreq_power_mode $pm
  [ "$(cat /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost)" != "$fg" ] && w /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost $fg
  g=$governor; [ "$oc" = 1 ] && g=performance
  for p in 0 4; do [ "$(cat /sys/devices/system/cpu/cpu$p/cpufreq/scaling_governor)" != "$g" ] && w /sys/devices/system/cpu/cpu$p/cpufreq/scaling_governor $g; done
  [ "$perf_profile" = battery ] && return
  grep -q "^0: min_freq_idx = -1" /proc/ppm/policy/userlimit_min_cpu_freq && { w /proc/ppm/policy/userlimit_min_cpu_freq "0 $minb"; w /proc/ppm/policy/userlimit_min_cpu_freq "1 $minl"; }
  [ "$gpu_eff" = 1 ] && head -1 /proc/gpufreq/gpufreq_opp_freq | grep -q disabled && w /proc/gpufreq/gpufreq_opp_freq $gpu_freq
}
( while sleep 5; do enforce; done ) &
publish; log "daemon ready"
exec inotifyd /system/bin/tablet_tunerd.sh $APP:nw   # toybox mask parsing is quirky: single letters are rejected, "wn" drops w
