package com.loop.new_loop_api.common.security;

import com.loop.new_loop_api.users.entity.Role;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read-only roles get an explicit allowlist instead of per-endpoint denials: an endpoint added later
 * stays closed to them until someone allows it here, so they can never change data by accident.
 */
public final class ReadOnlyRoleAccessPolicy {

    private static final String UUID_REGEX = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private static final Map<Role, List<Pattern>> ALLOWED_GET_PATHS = Map.of(
            Role.VISUALIZADOR, List.of(
                    Pattern.compile("/stock-controls"),
                    Pattern.compile("/stock-controls/pending-arrivals"),
                    Pattern.compile("/stock-controls/" + UUID_REGEX)
            ),
            Role.CLARKISTA, List.of(
                    Pattern.compile("/load-forecast")
            )
    );

    private ReadOnlyRoleAccessPolicy() {
    }

    /** Whether the role may call the endpoint; roles that are not read-only are not restricted here. */
    public static boolean allows(Role role, String method, String path) {
        if (!role.isReadOnly()) return true;
        if (!"GET".equalsIgnoreCase(method)) return false;
        var normalized = stripTrailingSlash(path);
        return ALLOWED_GET_PATHS.getOrDefault(role, List.of()).stream()
                .anyMatch(pattern -> pattern.matcher(normalized).matches());
    }

    private static String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
