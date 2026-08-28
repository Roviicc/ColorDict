package io.github.roviicc.colordict.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Persists per-dictionary user settings — display order, label color, and
 * enabled state — keyed by a stable dictionary id, as one JSON blob in
 * SharedPreferences.
 */
public final class DictRegistry {

    public static final class Entry {
        public int order;
        public int color;
        public boolean enabled;

        Entry(int order, int color, boolean enabled) {
            this.order = order;
            this.color = color;
            this.enabled = enabled;
        }
    }

    private static final String KEY = "dicts";

    private final SharedPreferences sp;
    private final Map<String, Entry> entries = new HashMap<>();

    public DictRegistry(Context context) {
        sp = context.getSharedPreferences("registry", Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        entries.clear();
        String json = sp.getString(KEY, "{}");
        try {
            JSONObject root = new JSONObject(json);
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String id = it.next();
                JSONObject o = root.getJSONObject(id);
                entries.put(id, new Entry(o.optInt("order"), o.optInt("color"),
                        o.optBoolean("enabled", true)));
            }
        } catch (JSONException ignored) {
            // Corrupt registry: start fresh; dictionaries re-register on scan.
        }
    }

    private void save() {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("order", e.getValue().order);
                o.put("color", e.getValue().color);
                o.put("enabled", e.getValue().enabled);
                root.put(e.getKey(), o);
            }
            sp.edit().putString(KEY, root.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    /** Registers newly discovered ids, assigning the next order and palette color. */
    public synchronized void register(List<String> presentIds) {
        int maxOrder = -1;
        for (Entry e : entries.values()) {
            maxOrder = Math.max(maxOrder, e.order);
        }
        boolean changed = false;
        for (String id : presentIds) {
            if (!entries.containsKey(id)) {
                maxOrder++;
                entries.put(id, new Entry(maxOrder, Palette.auto(entries.size()), true));
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    public synchronized Entry entryFor(String id) {
        Entry e = entries.get(id);
        if (e == null) {
            e = new Entry(entries.size(), Palette.auto(entries.size()), true);
            entries.put(id, e);
            save();
        }
        return e;
    }

    public synchronized void setEnabled(String id, boolean enabled) {
        entryFor(id).enabled = enabled;
        save();
    }

    public synchronized void setColor(String id, int color) {
        entryFor(id).color = color;
        save();
    }

    /** Rewrites the order of all ids to match the given list. */
    public synchronized void setOrder(List<String> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            entryFor(orderedIds.get(i)).order = i;
        }
        save();
    }

    public synchronized void remove(String id) {
        if (entries.remove(id) != null) {
            save();
        }
    }
}
