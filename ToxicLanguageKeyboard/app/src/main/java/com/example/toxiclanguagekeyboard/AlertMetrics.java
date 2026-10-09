package com.example.toxiclanguagekeyboard;

import android.content.SharedPreferences;

/** One displayed alert can contribute one detection and at most one terminal action. */
public final class AlertMetrics {
    private final SharedPreferences prefs;
    public AlertMetrics(SharedPreferences prefs) { this.prefs = prefs; }
    public Event shown() {
        increment("count_detections");
        return new Event();
    }
    private synchronized void increment(String name) {
        prefs.edit().putInt(name, prefs.getInt(name, 0) + 1).apply();
    }
    public final class Event {
        private boolean resolved;
        public synchronized void edited(boolean success) {
            if (resolved) return;
            resolved = true;
            if (success) increment("count_edits");
        }
        public synchronized void continued() {
            if (resolved) return;
            resolved = true;
            increment("count_ignored");
        }
        public synchronized void dismissed() { resolved = true; }
    }
}
