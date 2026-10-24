package org.tablet.tuner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private Map<String, String> st;
    private LinearLayout col;
    private boolean night;
    private boolean building;
    private float dp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTitle("ROM Tuner");
    }

    @Override protected void onResume() {
        super.onResume();
        st = Cfg.load(this);
        build();
    }

    // ---------- state helpers ----------
    private String get(String k, String def) { String v = st.get(k); return v == null || v.isEmpty() ? def : v; }

    private void set(String k, String v) {
        st.put(k, v);
        Cfg.request(this, k, v);
        Toast.makeText(this, "Applying…", Toast.LENGTH_SHORT).show();
    }

    // ---------- view helpers ----------
    private int px(float v) { return Math.round(v * dp); }
    private int accent() { return getColor(night ? android.R.color.system_accent1_300 : android.R.color.system_accent1_600); }

    private TextView text(String s, float sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        if (color != 0) t.setTextColor(color);
        return t;
    }

    private void section(String title) {
        TextView t = text(title.toUpperCase(Locale.US), 12.5f, true, accent());
        t.setLetterSpacing(0.08f);
        t.setPadding(px(8), px(20), 0, px(8));
        col.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(night ? 0xFF1C1C1E : 0xFFF2F2F7);
        g.setCornerRadius(px(24));
        c.setBackground(g);
        c.setPadding(px(6), px(6), px(6), px(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(4);
        col.addView(c, lp);
        return c;
    }

    private LinearLayout row(LinearLayout card, String title, String sub, View control) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(16), px(12), px(16), px(12));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, false, 0));
        if (sub != null) {
            TextView s = text(sub, 13, false, night ? 0xFF9A9AA0 : 0xFF6C6C70);
            texts.addView(s);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (control != null) r.addView(control);
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private void rowSwitch(LinearLayout card, String title, String sub, final String key, String def) {
        Switch s = new Switch(this);
        s.setChecked("1".equals(get(key, def)));
        s.setOnCheckedChangeListener((v, on) -> { if (!building) set(key, on ? "1" : "0"); });
        LinearLayout r = row(card, title, sub, s);
        r.setOnClickListener(v -> s.toggle());
    }

    private void rowChoice(LinearLayout card, String title, String sub, final String key, final String[] labels, final String[] vals, String def, final boolean rebuild) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        String cur = get(key, def);
        int pos = 0;
        for (int i = 0; i < vals.length; i++) if (vals[i].equals(cur)) pos = i;
        sp.setSelection(pos, false);
        final int start = pos;
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            int last = start;
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (building || i == last) return;
                last = i;
                set(key, vals[i]);
                if (rebuild) build();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        row(card, title, sub, sp);
    }

    private void rowButton(LinearLayout card, String title, String sub, final Intent i) {
        LinearLayout r = row(card, title, sub, null);
        r.setOnClickListener(v -> {
            try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "Not available on this build", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void rowAction(LinearLayout card, String title, String sub, final Runnable run) {
        row(card, title, sub, null).setOnClickListener(v -> run.run());
    }

    private Intent comp(String pkg, String cls) {
        return new Intent().setComponent(new ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private String[] list(String key, String fallback) {
        String v = get(key, "");
        return (v.isEmpty() ? fallback : v).split(",");
    }

    private String[] gpuValues() {
        String[] o = list("gpu_opps", "680000,500000,400000");
        String[] r = new String[o.length + 1]; r[0] = "0";
        System.arraycopy(o, 0, r, 1, o.length);
        return r;
    }

    private String[] gpuLabels() {
        String[] v = gpuValues(), r = new String[v.length]; r[0] = "Auto";
        for (int i = 1; i < v.length; i++) r[i] = (Integer.parseInt(v[i]) / 1000) + " MHz";
        return r;
    }

    private static String fontLabel(String f) {
        switch (f) {
            case "lato": return "Lato";
            case "rubik": return "Rubik";
            case "notoserifsource": return "Noto Serif";
            case "googlesansflex": return "Google Sans Flex";
            default: return f.substring(0, 1).toUpperCase(Locale.US) + f.substring(1);
        }
    }

    // ---------- UI ----------
    private void build() {
        building = true;
        ScrollView sv = new ScrollView(this);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), px(24), px(16), px(40));
        outer.addView(col, new LinearLayout.LayoutParams(Math.min(px(720), getResources().getDisplayMetrics().widthPixels), -2));
        sv.addView(outer);

        col.addView(text("ROM Tuner", 30, true, 0));
        String prof = get("perf_profile", "balanced");
        col.addView(text(get("status", "waiting for the tuner daemon…"), 13, false, night ? 0xFF9A9AA0 : 0xFF6C6C70));

        // ---- Presets ----
        section("Presets");
        LinearLayout pc = card();
        String[][] presets = {
            {"battery", "Battery saver", "Low power mode, clocks capped, GPU auto."},
            {"balanced", "Balanced", "Everyday default: moderate clock floor, GPU at 680 MHz."},
            {"smooth", "Smooth", "Performance profile with stronger app boosts and more free memory. Measured: Settings scroll jank 2.8% to 1.7%."},
            {"performance", "Performance", "Maximum app boosts, bigger write buffer. Measured: lowest scroll jank, 1.1%."},
            {"extreme", "Extreme", "Everything on: top clocks pinned, performance governor, thermal limit off. Hot and short on battery; no UI gain over Performance in tests."}};
        String curPreset = get("preset", "balanced");
        LinearLayout chips2 = new LinearLayout(this);
        chips2.setOrientation(LinearLayout.HORIZONTAL);
        for (final String[] pr : presets) {
            Button b = new Button(this);
            b.setText(pr[1]);
            b.setAllCaps(false);
            b.setAlpha(pr[0].equals(curPreset) ? 1f : 0.55f);
            b.setOnClickListener(v -> {
                set("preset", pr[0]);
                col.postDelayed(() -> { st = Cfg.load(this); build(); }, 2500);
            });
            chips2.addView(b);
        }
        HorizontalScrollView phs = new HorizontalScrollView(this);
        phs.addView(chips2);
        phs.setPadding(px(10), px(6), px(10), 0);
        pc.addView(phs);
        String desc = "Custom: you changed individual settings.";
        for (String[] pr : presets) if (pr[0].equals(curPreset)) desc = pr[2];
        TextView dt = text(desc, 13, false, night ? 0xFF9A9AA0 : 0xFF6C6C70);
        dt.setPadding(px(16), px(10), px(16), px(12));
        pc.addView(dt);

        // ---- Performance ----
        section("Performance");
        LinearLayout c = card();
        RadioGroup rg = new RadioGroup(this);
        String[][] profiles = {
            {"battery", "Battery saver", "Low power mode, no minimum clocks, GPU unlocked"},
            {"balanced", "Balanced", "Moderate boost and clock floor (default)"},
            {"performance", "Performance", "Sports mode, high clock floor, strongest UI boost"}};
        for (String[] p : profiles) {
            RadioButton rb = new RadioButton(this);
            rb.setText(p[1] + "\n" + p[2]);
            rb.setTextSize(15);
            rb.setPadding(px(8), px(10), px(8), px(10));
            rb.setId(View.generateViewId());
            rb.setTag(p[0]);
            rb.setChecked(p[0].equals(prof));
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g, id) -> {
            if (building) return;
            RadioButton rb = g.findViewById(id);
            set("perf_profile", (String) rb.getTag());
        });
        LinearLayout.LayoutParams rgp = new LinearLayout.LayoutParams(-1, -2);
        rgp.setMargins(px(12), px(4), px(12), px(4));
        c.addView(rg, rgp);
        rowChoice(c, "GPU clock", "Lock the PowerVR GPU to one clock (ignored in Battery saver)", "gpu_freq", gpuLabels(), gpuValues(), "680000", false);
        rowChoice(c, "Fast cores: maximum clock", "Cap the four fast cores", "cap_big",
            new String[]{"No limit", "2.0 GHz", "1.75 GHz", "1.5 GHz", "1.2 GHz", "0.9 GHz"},
            new String[]{"-1", "2009000", "1750000", "1484000", "1218000", "900000"}, "-1", false);
        rowChoice(c, "Efficiency cores: maximum clock", "Cap the four slower cores", "cap_little",
            new String[]{"No limit", "1.5 GHz", "1.2 GHz", "1.05 GHz", "0.75 GHz"},
            new String[]{"-1", "1491000", "1226000", "1050000", "745000"}, "-1", false);

        // ---- Tuning ----
        section("Tuning");
        c = card();
        rowSwitch(c, "Overclock", "Pins both CPU clusters at their top clock with the performance governor. Runs hot and drains the battery.", "oc", "0");
        rowChoice(c, "CPU governor", "Used when Overclock is off", "governor", list("govs", "schedplus,ondemand,interactive,performance,powersave"), list("govs", "schedplus,ondemand,interactive,performance,powersave"), "schedplus", false);
        rowChoice(c, "Thermal limits", "Off removes the MediaTek thermal clock limit. Not recommended while charging.", "thermal",
            new String[]{"Stock", "Off"}, new String[]{"stock", "off"}, "stock", false);
        rowChoice(c, "I/O scheduler", "Storage request scheduling", "io_sched", list("io_scheds", "noop,deadline,cfq"), list("io_scheds", "noop,deadline,cfq"), "deadline", false);
        rowChoice(c, "Read-ahead", "Prefetch size when reading storage", "readahead",
            new String[]{"128 KB", "256 KB", "512 KB", "1 MB", "2 MB"}, new String[]{"128", "256", "512", "1024", "2048"}, "512", false);
        rowChoice(c, "Swappiness", "Higher keeps more apps in memory by compressing them", "swappiness",
            new String[]{"20", "40", "60", "80", "100"}, new String[]{"20", "40", "60", "80", "100"}, "100", false);
        rowChoice(c, "TCP congestion control", null, "tcp_cc", list("tcps", "bic,cubic,reno"), list("tcps", "bic,cubic,reno"), "bic", false);

        rowChoice(c, "Foreground app boost", "CPU boost for the visible app. Auto follows the profile.", "fg_boost",
            new String[]{"Auto", "0", "10", "20", "30", "40", "60", "80", "100"}, new String[]{"auto", "0", "10", "20", "30", "40", "60", "80", "100"}, "auto", false);
        rowChoice(c, "Top-app boost", "Extra boost for the app you are touching", "ta_boost",
            new String[]{"0", "10", "20", "30", "50", "70"}, new String[]{"0", "10", "20", "30", "50", "70"}, "0", false);
        rowSwitch(c, "Big-task rotation", "Moves heavy threads between fast cores to spread heat", "big_rotation", "0");
        rowChoice(c, "Cache pressure", "Lower keeps file caches longer", "vfs_cache",
            new String[]{"25", "50", "100", "150"}, new String[]{"25", "50", "100", "150"}, "50", false);
        rowChoice(c, "Write-back threshold", "Share of memory that can hold unsaved writes. The vendor default is 1%, which makes large writes slow.", "dirty",
            new String[]{"1%", "5%", "10%", "20%", "30%"}, new String[]{"1", "5", "10", "20", "30"}, "1", false);
        rowChoice(c, "Free memory reserve", "Memory kept free so new apps start without waiting", "extra_free",
            new String[]{"0", "24 MB", "48 MB", "96 MB"}, new String[]{"0", "24576", "49152", "98304"}, "49152", false);
        rowChoice(c, "Swap read-ahead", "Pages read back from compressed memory at once", "page_cluster",
            new String[]{"1 (stock)", "2", "4", "8"}, new String[]{"0", "1", "2", "3"}, "0", false);

        // ---- Behaviour ----
        section("Behaviour");
        c = card();
        rowChoice(c, "Screen timeout", null, "screen_timeout",
            new String[]{"15 seconds", "30 seconds", "1 minute", "2 minutes", "5 minutes", "10 minutes", "30 minutes"},
            new String[]{"15000", "30000", "60000", "120000", "300000", "600000", "1800000"}, "60000", false);
        rowChoice(c, "Font size", null, "font_scale",
            new String[]{"Small", "Default", "Large", "Largest"}, new String[]{"0.85", "1.0", "1.15", "1.3"}, "1.0", false);
        rowChoice(c, "Stay awake while charging", null, "stay_awake",
            new String[]{"Never", "On AC and USB", "On AC, USB and wireless"}, new String[]{"0", "3", "7"}, "0", false);
        rowSwitch(c, "Auto-rotate", null, "auto_rotate", "1");
        rowSwitch(c, "Show taps", "Draws a dot where you touch the screen", "show_taps", "0");
        rowSwitch(c, "Turn off window blur", "Cheaper to draw; shade and dialogs lose the blur", "window_blur", "0");
        rowChoice(c, "Private DNS", null, "dns",
            new String[]{"Off", "Automatic", "Google", "Cloudflare", "AdGuard (blocks ads)", "Quad9"},
            new String[]{"off", "auto", "google", "cloudflare", "adguard", "quad9"}, "auto", false);
        rowChoice(c, "Cached app limit", "How many background apps stay in memory", "bg_procs",
            new String[]{"System default", "8", "16", "32", "64"}, new String[]{"default", "8", "16", "32", "64"}, "default", false);
        rowSwitch(c, "Allow Treble audio effects", "Treble Settings > Audio effects. Left on, app audio can cut out after a few milliseconds.", "phh_effects", "0");
        rowSwitch(c, "ADB over network", "Port 5555 on Wi-Fi. Anyone on the network can connect.", "adb_tcp", "0");
        rowSwitch(c, "Bluetooth MediaTek workaround", "Same as Treble Settings > Misc > Bluetooth. Restarts Bluetooth.", "bt_work", "0");

        // ---- Appearance ----
        section("Appearance");
        c = card();
        rowChoice(c, "Dark mode", null, "night",
            new String[]{"Automatic", "Light", "Dark"}, new String[]{"auto", "no", "yes"}, "yes", false);
        rowSwitch(c, "Pure black surfaces", "True black backgrounds (OLED-style, One UI-like)", "black_theme", "1");
        // accent chips
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[][] colors = {{"3E91FF", "Blue"}, {"7C5CFF", "Violet"}, {"00B8A9", "Teal"}, {"34C759", "Green"}, {"FF9500", "Orange"}, {"FF3B30", "Red"}, {"FF4F9A", "Pink"}};
        boolean fromWall = "home_wallpaper".equals(get("palette_source", "preset"));
        String curAcc = get("accent", "3E91FF");
        for (final String[] cc : colors) {
            View chip = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF000000 | Integer.parseInt(cc[0], 16));
            if (!fromWall && cc[0].equalsIgnoreCase(curAcc)) g.setStroke(px(3), night ? 0xFFFFFFFF : 0xFF000000);
            chip.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(36), px(36));
            lp.setMargins(px(4), 0, px(4), 0);
            chip.setOnClickListener(v -> { set("palette_source", "preset"); set("accent", cc[0]); build(); });
            chips.addView(chip, lp);
        }
        Button wall = new Button(this);
        wall.setText(fromWall ? "Wallpaper ✓" : "Wallpaper");
        wall.setAllCaps(false);
        wall.setOnClickListener(v -> { set("palette_source", "home_wallpaper"); build(); });
        chips.addView(wall);
        row(c, "Accent colour", "Applies system-wide (Material You palette)", null);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(chips);
        hs.setPadding(px(12), 0, px(12), px(10));
        c.addView(hs);
        rowChoice(c, "Palette style", null, "theme_style",
            new String[]{"Tonal spot", "Vibrant", "Expressive", "Spritz", "Rainbow", "Fruit salad", "Monochrome"},
            new String[]{"TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "SPRITZ", "RAINBOW", "FRUIT_SALAD", "MONOCHROMATIC"}, "TONAL_SPOT", false);
        // fonts and icon styles come from the overlays this ROM ships (the daemon reports them)
        String[] fl = get("fonts", "").split(",");
        java.util.ArrayList<String> fLabels = new java.util.ArrayList<>(), fVals = new java.util.ArrayList<>();
        fLabels.add("System default"); fVals.add("default");
        for (String f : fl) if (!f.isEmpty()) { fVals.add(f); fLabels.add(fontLabel(f)); }
        if (fVals.size() > 1) rowChoice(c, "Font", null, "font", fLabels.toArray(new String[0]), fVals.toArray(new String[0]), "default", false);
        if ("1".equals(get("has_shapes", "0")))
            rowChoice(c, "Icon shape", null, "icon_shape",
                new String[]{"Default (circle)", "Squircle", "Rounded square", "Teardrop", "Pebble", "Vessel", "Tapered", "Square"},
                new String[]{"default", "squircle", "roundedrect", "teardrop", "pebble", "vessel", "taperedrect", "square"}, "default", false);
        if ("1".equals(get("has_packs", "0")))
            rowChoice(c, "System icon pack", null, "icon_pack",
                new String[]{"Default", "Rounded", "Filled", "Kai", "Sam", "Victor", "Circular"},
                new String[]{"default", "rounded", "filled", "kai", "sam", "victor", "circular"}, "default", false);

        // ---- Display & system UI ----
        section("Display & system UI");
        c = card();
        rowChoice(c, "Navigation", null, "nav_mode",
            new String[]{"Swipe gestures", "Three buttons"}, new String[]{"gestural", "threebutton"}, "gestural", false);
        // animation speed
        final TextView animVal = text("", 14, false, 0);
        SeekBar sb = new SeekBar(this);
        sb.setMax(20);
        float cur = 1.0f;
        try { cur = Float.parseFloat(get("anim_scale", "1.0")); } catch (Exception ignored) { }
        sb.setProgress(Math.round(cur * 10));
        animVal.setText(String.format(Locale.US, "%.1fx", cur));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { animVal.setText(String.format(Locale.US, "%.1fx", p / 10f)); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { if (!building) set("anim_scale", String.format(Locale.US, "%.1f", s.getProgress() / 10f)); }
        });
        LinearLayout ar = row(c, "Animation speed", "Window, transition and animator scale (lower is faster)", animVal);
        c.addView(sb, new LinearLayout.LayoutParams(-1, -2));
        ((LinearLayout.LayoutParams) sb.getLayoutParams()).setMargins(px(16), 0, px(16), px(8));
        rowChoice(c, "Display size", "Smaller shows more content (changes density)", "density",
            new String[]{"Stock (240)", "Compact (192)", "Small (213)", "Large (260)", "Largest (280)"},
            new String[]{"reset", "192", "213", "260", "280"}, "192", false);
        rowChoice(c, "Boot animation", null, "bootanim",
            new String[]{"ArrowOS", "Galaxy", "Aurora ring", "Stock LineageOS"}, new String[]{"arrow", "galaxy", "aurora", "stock"}, "arrow", false);

        // ---- System ----
        section("System");
        c = card();
        rowButton(c, "Status bar", "Clock, battery, network speed, brightness slider", comp("org.lineageos.lineageparts", "org.lineageos.lineageparts.statusbar.StatusBarSettings"));
        rowButton(c, "Buttons", "Navigation and hardware key actions", comp("org.lineageos.lineageparts", "org.lineageos.lineageparts.input.ButtonSettings"));
        rowButton(c, "Display rotation", "Allowed rotation angles", comp("org.lineageos.lineageparts", "org.lineageos.lineageparts.hardware.DisplayRotation"));
        rowButton(c, "Charging control", "Limit charge level", comp("org.lineageos.lineageparts", "org.lineageos.lineageparts.health.ChargingControlSettings"));
        rowButton(c, "Dolby Atmos", "Sound modes", comp("com.dolby.daxappui", "com.dolby.daxappui.MainActivity"));
        rowButton(c, "Wallpaper & style", null, new Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        rowButton(c, "Developer options", null, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        rowSwitch(c, "System logging", "Off by default for speed; turn on to debug", "logging", "0");

        section("Actions");
        c = card();
        rowAction(c, "Restart System UI", "Re-applies theme changes without rebooting", () -> { Cfg.request(this, "restart_ui", "1"); Toast.makeText(this, "Restarting System UI…", Toast.LENGTH_SHORT).show(); });
        rowAction(c, "Reboot", "Needed for some changes to fully apply", () ->
            new AlertDialog.Builder(this).setMessage("Reboot the tablet now?").setNegativeButton("Cancel", null)
                .setPositiveButton("Reboot", (d, w) -> Cfg.request(this, "reboot", "1")).show());

        setContentView(sv);
        building = false;
    }
}
