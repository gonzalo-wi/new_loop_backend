package com.loop.new_loop_api.audit.service;

import java.util.Locale;

/** Which client an audited action came from. Values match what the admin panel already labels. */
public final class AuditSource {

    public static final String ADMIN_WEB  = "ADMIN_WEB";
    public static final String MOBILE_APP = "MOBILE_APP";
    /** No HTTP request behind the action: schedulers and async integration listeners. */
    public static final String SYSTEM     = "SYSTEM";
    public static final String UNKNOWN    = "UNKNOWN";

    /** Sent by our own clients ("web" / "mobile"); the user agent is only a fallback for older app builds. */
    public static final String CLIENT_APP_HEADER = "X-Client-App";

    // React Native's HTTP stack: okhttp/Dalvik on Android, CFNetwork on iOS.
    private static final String[] MOBILE_USER_AGENT_MARKERS = {"okhttp", "dalvik", "cfnetwork", "expo"};

    private AuditSource() {
    }

    public static String resolve(String clientAppHeader, String userAgent) {
        if (clientAppHeader != null) {
            return switch (clientAppHeader.trim().toLowerCase(Locale.ROOT)) {
                case "web"    -> ADMIN_WEB;
                case "mobile" -> MOBILE_APP;
                default       -> UNKNOWN;
            };
        }
        if (userAgent == null) return UNKNOWN;
        var agent = userAgent.toLowerCase(Locale.ROOT);
        for (var marker : MOBILE_USER_AGENT_MARKERS) {
            if (agent.contains(marker)) return MOBILE_APP;
        }
        return agent.startsWith("mozilla/") ? ADMIN_WEB : UNKNOWN;
    }
}
