package com.loop.new_loop_api.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.dto.AuditLogResponse;
import com.loop.new_loop_api.audit.entity.AuditLog;
import com.loop.new_loop_api.audit.mapper.AuditLogMapper;
import com.loop.new_loop_api.audit.repository.AuditLogRepository;
import com.loop.new_loop_api.audit.repository.AuditLogSpecification;
import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    static final String LOGIN        = "LOGIN";
    static final String LOGIN_FAILED = "LOGIN_FAILED";
    static final String USER_ENTITY  = "User";

    // Whatever was typed in the username box is stored as-is on a failed login, so it is capped.
    static final int MAX_ATTEMPTED_USERNAME_LENGTH = 100;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper     auditLogMapper;
    private final ObjectMapper       objectMapper;

    @Override
    public void register(String action, String entityName, UUID entityId, Object oldValue, Object newValue) {
        register(action, entityName, entityId, oldValue, newValue, null);
    }

    @Override
    public void register(String action, String entityName, UUID entityId, Object oldValue, Object newValue, String reason) {
        var user = currentUser();
        auditLogRepository.save(withOrigin(AuditLog.builder())
                .userId(user != null ? user.id() : null)
                .username(user != null ? user.username() : null)
                .userRole(user != null ? user.role().name() : null)
                .action(action)
                .entityName(entityName)
                .entityId(entityId)
                .oldValue(serialize(oldValue))
                .newValue(serialize(newValue))
                .reason(reason)
                .build());
    }

    // The login request is not authenticated yet, so the actor comes from the caller, not the security context.
    @Override
    public void registerLogin(UUID userId, String username, String role) {
        auditLogRepository.save(withOrigin(AuditLog.builder())
                .userId(userId)
                .username(username)
                .userRole(role)
                .action(LOGIN)
                .entityName(USER_ENTITY)
                .entityId(userId)
                .build());
    }

    @Override
    public void registerFailedLogin(String attemptedUsername, String reason) {
        auditLogRepository.save(withOrigin(AuditLog.builder())
                .username(truncate(attemptedUsername, MAX_ATTEMPTED_USERNAME_LENGTH))
                .action(LOGIN_FAILED)
                .entityName(USER_ENTITY)
                .reason(reason)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAll(String entityName, String action, UUID entityId, UUID userId, String source,
                                         LocalDateTime from, LocalDateTime to, Pageable pageable) {
        var spec = AuditLogSpecification.withFilters(entityName, action, entityId, userId, source, from, to);
        return auditLogRepository.findAll(spec, pageable)
                .map(auditLogMapper::toResponse);
    }

    @Value("${app.audit.retention-days}")
    private int retentionDays;

    // Runs every day at midnight — deletes records older than the configured retention.
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "auditService.purgeOldLogs", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void purgeOldLogs() {
        auditLogRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(retentionDays));
    }

    private static AuditLog.AuditLogBuilder withOrigin(AuditLog.AuditLogBuilder builder) {
        var request = currentRequest();
        if (request == null) {
            return builder.source(AuditSource.SYSTEM);
        }
        return builder
                .source(AuditSource.resolve(request.getHeader(AuditSource.CLIENT_APP_HEADER),
                        request.getHeader(HttpHeaders.USER_AGENT)))
                .ipAddress(request.getRemoteAddr());
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private AuthenticatedUser currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private String serialize(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }
}
