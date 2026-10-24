package org.tablet.tuner;

import android.content.Context;
import java.io.*;
import java.util.*;

/** State is written by the root daemon (state file); changes are requested through the request file. */
final class Cfg {
    static Map<String, String> load(Context c) {
        Map<String, String> m = new HashMap<>();
        File f = new File(c.getFilesDir(), "state");
        if (!f.exists()) return m;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) {
                int i = l.indexOf('=');
                if (i > 0) m.put(l.substring(0, i), l.substring(i + 1));
            }
        } catch (IOException ignored) { }
        return m;
    }

    static void request(Context c, String key, String value) {
        File f = new File(c.getFilesDir(), "request");
        try (FileWriter w = new FileWriter(f, true)) {
            w.write(key + "=" + value + "\n");
        } catch (IOException ignored) { }
    }
}
