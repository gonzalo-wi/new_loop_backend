package com.loop.new_loop_api.common.security;

import com.loop.new_loop_api.users.entity.Role;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read-only roles get an explicit allowlist instead of per-endpoint denials: an endpoint added later
 * stays closed to them until someone allows it here, so they can never change data by accident.
 * The only writes allowed are marking remitos printed, which viewers do when they print them.
 */
public final class ReadOnlyRoleAccessPolicy {

    private static final String UUID_REGEX = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private record Endpoint(String method, Pattern path) {
        static Endpoint get(String regex) {
            return new Endpoint("GET", Pattern.compile(regex));
        }

        static Endpoint post(String regex) {
            return new Endpoint("POST", Pattern.compile(regex));
        }

        boolean matches(String requestMethod, String requestPath) {
            return method.equalsIgnoreCase(requestMethod) && path.matcher(requestPath).matches();
        }
    }

    private static final Map<Role, List<Endpoint>> ALLOWED_ENDPOINTS = Map.of(
            Role.VISUALIZADOR, List.of(
                    Endpoint.get("/stock-controls"),
                    Endpoint.get("/stock-controls/pending-arrivals"),
                    Endpoint.get("/stock-controls/" + UUID_REGEX),
                    Endpoint.get("/stock-controls/" + UUID_REGEX + "/remito"),
                    Endpoint.post("/stock-controls/" + UUID_REGEX + "/remito/printed"),
                    Endpoint.post("/stock-controls/remitos"),
                    Endpoint.post("/stock-controls/remitos/printed")
            ),
            Role.CLARKISTA, List.of(
                    Endpoint.get("/load-forecast")
            )
    );

    private ReadOnlyRoleAccessPolicy() {
    }

    /** Whether the role may call the endpoint; roles that are not read-only are not restricted here. */
    public static boolean allows(Role role, String method, String path) {
        if (!role.isReadOnly()) return true;
        var normalized = stripTrailingSlash(path);
        return ALLOWED_ENDPOINTS.getOrDefault(role, List.of()).stream()
                .anyMatch(endpoint -> endpoint.matches(method, normalized));
    }

    private static String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
