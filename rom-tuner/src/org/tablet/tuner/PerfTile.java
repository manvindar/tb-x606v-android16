package org.tablet.tuner;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.util.Map;

public class PerfTile extends TileService {
    private static final String[] ORDER = {"battery", "balanced", "performance"};
    private static final String[] NAMES = {"Battery saver", "Balanced", "Performance"};

    private int index() {
        Map<String, String> st = Cfg.load(this);
        String p = st.containsKey("perf_profile") ? st.get("perf_profile") : "balanced";
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(p)) return i;
        return 1;
    }

    private void render(int i) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tuner));
        t.setLabel("Performance");
        t.setSubtitle(NAMES[i]);
        t.setState(i == 1 ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        t.updateTile();
    }

    @Override public void onStartListening() { render(index()); }

    @Override public void onClick() {
        int next = (index() + 1) % ORDER.length;
        Cfg.request(this, "perf_profile", ORDER[next]);
        render(next);
    }
}
