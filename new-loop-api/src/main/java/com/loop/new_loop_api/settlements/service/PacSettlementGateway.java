package com.loop.new_loop_api.settlements.service;

import com.loop.new_loop_api.integrations.pac.client.PacSettlementClient;
import com.loop.new_loop_api.integrations.pac.dto.PacSettlementResponse;
import com.loop.new_loop_api.settlements.domain.PacLookup;
import com.loop.new_loop_api.settlements.dto.SettlementFigures;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks PAC for one route's settlement. Answers are kept 10 seconds per route and day so a driver opening the
 * screen repeatedly does not hit PAC each time; failures are not kept, so a refresh retries at once.
 */
@Component
public class PacSettlementGateway {

    private static final Logger   log = LoggerFactory.getLogger(PacSettlementGateway.class);
    // Short: a deposit must show within seconds of the driver pressing "finalizar" (PAC throttles PIMS itself).
    static final Duration         CACHE_TTL = Duration.ofSeconds(10);
    private static final int      NOT_FOUND = 404;

    private final PacSettlementClient client;
    private final Clock               clock;
    private final String              username;
    private final String              password;
    private final Map<Key, Cached>    cache = new ConcurrentHashMap<>();

    public PacSettlementGateway(PacSettlementClient client, Clock clock,
                                @Value("${integrations.pac.username:}") String username,
                                @Value("${integrations.pac.password:}") String password) {
        this.client   = client;
        this.clock    = clock;
        this.username = username;
        this.password = password;
    }

    public PacLookup lookup(long routeNumber, LocalDate date) {
        if (!configured()) return new PacLookup.Unavailable("PAC integration credentials are not configured");
        var key    = new Key(routeNumber, date);
        var now    = clock.instant();
        var cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) return cached.lookup();

        var lookup = fetch(routeNumber, date);
        // Past days are never asked again: drop them so the map only ever holds today's routes.
        cache.keySet().removeIf(k -> k.date().isBefore(date));
        if (lookup instanceof PacLookup.Unavailable) {
            cache.remove(key);
        } else {
            cache.put(key, new Cached(lookup, now.plus(CACHE_TTL)));
        }
        return lookup;
    }

    private PacLookup fetch(long routeNumber, LocalDate date) {
        try {
            return new PacLookup.Found(toFigures(client.getSettlement(basicAuthorization(), routeNumber, date)));
        } catch (FeignException e) {
            if (e.status() == NOT_FOUND) return new PacLookup.NotLoaded();
            // status() is -1 for timeouts and connection errors. Feign's message may carry PAC's URL and a
            // truncated body, never the Authorization header.
            log.warn("PAC settlement lookup failed for route {} on {}: HTTP {}", routeNumber, date, e.status(), e);
            return new PacLookup.Unavailable("PAC answered HTTP " + e.status());
        } catch (RuntimeException e) {
            log.warn("PAC settlement lookup failed for route {} on {}", routeNumber, date, e);
            return new PacLookup.Unavailable("PAC could not be read");
        }
    }

    private boolean configured() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    private String basicAuthorization() {
        var raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static SettlementFigures toFigures(PacSettlementResponse r) {
        if (r == null) throw new IllegalStateException("PAC answered an empty body");
        return new SettlementFigures(r.expectedCash(), r.expectedChecks(), r.expectedWithholdings(), r.expectedTotal(),
                r.depositedCash(), r.confirmedChecks(), r.confirmedWithholdings(), r.totalReceived(),
                r.difference(), r.differenceType(), r.statusGroup(), r.expectedUpdatedAt(), r.lastDepositAt(),
                r.cashDifference(), r.depositsCheckedAt(), Boolean.TRUE.equals(r.depositsLive()));
    }

    private record Key(long routeNumber, LocalDate date) {}

    private record Cached(PacLookup lookup, Instant expiresAt) {}
}
