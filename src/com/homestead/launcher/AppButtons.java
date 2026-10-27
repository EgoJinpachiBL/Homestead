/*
 * Homestead - a replacement home screen for Fire TV.
 * Copyright (C) 2026 Homestead contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. It is distributed in
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.homestead.launcher;

/**
 * The four coloured app buttons on the Fire TV remote, told apart in the system log. The
 * system itself (uid 1000) starts the app as the key goes down, before any accessibility
 * service sees it, so the only handle is the launch that follows:
 *
 *   YouTube, Disney+  Amazon Appstore's AppLaunchActivity, then its ASIN (the app is not
 *                     installed from Amazon, so the Appstore page opens)
 *   Netflix           the installed app's launch intent
 *   Prime Video       the installed app's launch intent, right after AppLaunchActivity
 *
 * Seen on an AFTSS stick (Fire OS 7.7.1.6, EU remote). Another remote or Amazon update
 * may log something else; then nothing matches and the buttons behave as Amazon made them.
 */
public final class AppButtons {

    /** Index into TARGETS, sent from HomeKeyWatcher to HomeRedirectService. */
    public static final int YOUTUBE = 0, NETFLIX = 1, PRIME = 2, DISNEY = 3;

    /** What each button opens instead. */
    static final String[] TARGETS = {
        "org.smarttube.stable",           // YouTube  -> SmartTube
        "com.nuvio.tv",                   // Netflix  -> Nuvio
        "com.sportzx.live",               // Prime    -> SportzX
        "tv.twitch.android.viewer",       // Disney+  -> Twitch
    };

    private static final String YOUTUBE_ASIN = "B07T771SPH";
    private static final String DISNEY_ASIN = "B07Y8SJGCV";
    /** The ASIN and Prime lines follow the partner launch within a second; allow some slack. */
    private static final long WINDOW_MS = 3000L;

    private long partnerAt = -1L;
    /** The Appstore opens its details page about a second later, over the app we started. */
    private int followButton = -1;
    private long followAt;
    private static final long FOLLOW_MS = 5000L;

    /** The button this log line shows, or -1. {@code notBefore}: ignore lines older than this. */
    public int match(String line, long notBefore) {
        boolean start = line.indexOf("START u0 {") >= 0 && line.indexOf(" from uid 1000") >= 0;
        boolean asin = line.indexOf("Evaluating launch decision for asin=") >= 0;
        boolean details = line.indexOf("cmp=com.amazon.venezia/.details.AppDetailsActivity") >= 0;
        if (!start && !asin && !details) return -1;
        long at = lineTime(line);
        if (at < 0 || at < notBefore) return -1;

        if (details) {
            int b = followButton;
            followButton = -1;
            return b >= 0 && at - followAt <= FOLLOW_MS ? b : -1;
        }

        if (start) {
            if (line.indexOf("pdi.AppLaunchActivity") >= 0) { partnerAt = at; return -1; }
            if (line.indexOf("pkg=com.netflix.ninja") >= 0) return NETFLIX;
            if (line.indexOf("pkg=com.amazon.firebat") >= 0 && recent(at)) return done(PRIME, at, false);
            return -1;
        }
        if (!recent(at)) return -1;
        if (line.indexOf(YOUTUBE_ASIN) >= 0) return done(YOUTUBE, at, true);
        if (line.indexOf(DISNEY_ASIN) >= 0) return done(DISNEY, at, true);
        return -1;
    }

    private boolean recent(long at) { return partnerAt >= 0 && at - partnerAt <= WINDOW_MS; }

    /** One partner launch makes one button press. */
    private int done(int button, long at, boolean followed) {
        partnerAt = -1L;
        if (followed) { followButton = button; followAt = at; }
        return button;
    }

    /** Epoch ms from the start of a "logcat -v epoch" line, or -1. */
    static long lineTime(String line) {
        try {
            String t = line.trim();
            return (long) (Double.parseDouble(t.substring(0, t.indexOf(' '))) * 1000.0);
        } catch (Throwable ignored) { return -1L; }
    }
}
