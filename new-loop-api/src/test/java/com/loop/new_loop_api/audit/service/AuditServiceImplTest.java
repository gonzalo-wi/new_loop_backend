package com.loop.new_loop_api.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.new_loop_api.audit.entity.AuditLog;
import com.loop.new_loop_api.audit.mapper.AuditLogMapper;
import com.loop.new_loop_api.audit.repository.AuditLogRepository;
import com.loop.new_loop_api.common.security.AuthenticatedUser;
import com.loop.new_loop_api.users.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AuditLogMapper     auditLogMapper;

    private AuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditServiceImpl(auditLogRepository, auditLogMapper, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(String clientApp, String userAgent) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        if (clientApp != null) request.addHeader(AuditSource.CLIENT_APP_HEADER, clientApp);
        if (userAgent != null) request.addHeader("User-Agent", userAgent);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AuditLog savedLog() {
        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_recordMobileSourceAndIp_when_requestComesFromTheMobileApp() {
        bindRequest("mobile", null);

        auditService.register("CREATE_DISPENSER_MOVEMENT", "DispenserMovement", UUID.randomUUID(), null, null);

        var saved = savedLog();
        assertThat(saved.getSource()).isEqualTo("MOBILE_APP");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.7");
    }

    @Test
    void should_recordWebSource_when_requestComesFromTheAdminPanel() {
        bindRequest("web", null);

        auditService.register("UPDATE_USER", "User", UUID.randomUUID(), null, null);

        assertThat(savedLog().getSource()).isEqualTo("ADMIN_WEB");
    }

    @Test
    void should_recordSystemSourceWithoutIp_when_thereIsNoHttpRequest() {
        auditService.register("SEND_DISPENSER_TO_ODOO", "DispenserMovement", UUID.randomUUID(), null, null);

        var saved = savedLog();
        assertThat(saved.getSource()).isEqualTo("SYSTEM");
        assertThat(saved.getIpAddress()).isNull();
    }

    @Test
    void should_recordLoginWithTheUserThatLoggedIn() {
        bindRequest("web", null);
        var userId = UUID.randomUUID();

        auditService.registerLogin(userId, "supervisor1", "SUPERVISOR");

        var saved = savedLog();
        assertThat(saved.getAction()).isEqualTo("LOGIN");
        assertThat(saved.getEntityName()).isEqualTo("User");
        assertThat(saved.getEntityId()).isEqualTo(userId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getUsername()).isEqualTo("supervisor1");
        assertThat(saved.getUserRole()).isEqualTo("SUPERVISOR");
        assertThat(saved.getSource()).isEqualTo("ADMIN_WEB");
    }

    @Test
    void should_recordFailedLoginWithTheAttemptedUsernameAndReason() {
        bindRequest(null, "okhttp/4.12.0");

        auditService.registerFailedLogin("chofer9", "BAD_CREDENTIALS");

        var saved = savedLog();
        assertThat(saved.getAction()).isEqualTo("LOGIN_FAILED");
        assertThat(saved.getEntityName()).isEqualTo("User");
        assertThat(saved.getEntityId()).isNull();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getUsername()).isEqualTo("chofer9");
        assertThat(saved.getReason()).isEqualTo("BAD_CREDENTIALS");
        assertThat(saved.getSource()).isEqualTo("MOBILE_APP");
    }

    @Test
    void should_truncateAttemptedUsername_when_failedLoginUsernameIsTooLong() {
        auditService.registerFailedLogin("x".repeat(500), "BAD_CREDENTIALS");

        assertThat(savedLog().getUsername()).hasSize(AuditServiceImpl.MAX_ATTEMPTED_USERNAME_LENGTH);
    }

    private void authenticateAs(AuthenticatedUser user) {
        var authentication = new UsernamePasswordAuthenticationToken(user, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // 5. The 6-arg overload with reason persists the reason in the AuditLog
    @Test
    void should_persistReason_when_registerCalledWithReason() {
        var user = new AuthenticatedUser(UUID.randomUUID(), "supervisor1", "Super Visor", Role.SUPERVISOR, UUID.randomUUID());
        authenticateAs(user);
        var entityId = UUID.randomUUID();

        auditService.register("CORRECT_STOCK_CONTROL", "StockControl", entityId,
                Map.of("observations", "old"), Map.of("observations", "new"), "Cantidad mal cargada");

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.getReason()).isEqualTo("Cantidad mal cargada");
        assertThat(saved.getAction()).isEqualTo("CORRECT_STOCK_CONTROL");
        assertThat(saved.getEntityName()).isEqualTo("StockControl");
        assertThat(saved.getEntityId()).isEqualTo(entityId);
        assertThat(saved.getUsername()).isEqualTo("supervisor1");
        assertThat(saved.getUserRole()).isEqualTo("SUPERVISOR");
        assertThat(saved.getOldValue()).contains("\"old\"");
        assertThat(saved.getNewValue()).contains("\"new\"");
    }

    // 5b. The 5-arg overload delegates with reason = null
    @Test
    void should_saveNullReason_when_registerCalledWithoutReason() {
        var user = new AuthenticatedUser(UUID.randomUUID(), "picker1", "Picker One", Role.PICKER, UUID.randomUUID());
        authenticateAs(user);
        var entityId = UUID.randomUUID();

        auditService.register("CREATE_STOCK_CONTROL", "StockControl", entityId, null, "{\"id\":\"x\"}");

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getReason()).isNull();
        assertThat(captor.getValue().getOldValue()).isNull();
    }

    @Test
    void should_saveNullUserFields_when_noAuthenticatedUserInContext() {
        var entityId = UUID.randomUUID();

        auditService.register("CREATE_STOCK_CONTROL", "StockControl", entityId, null, "{\"id\":\"x\"}");

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getUsername()).isNull();
        assertThat(saved.getUserRole()).isNull();
    }

    @Test
    void should_purgeLogsOlderThanConfiguredRetention() {
        ReflectionTestUtils.setField(auditService, "retentionDays", 365);
        var before = LocalDateTime.now().minusDays(365);

        auditService.purgeOldLogs();

        var captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).deleteByCreatedAtBefore(captor.capture());
        assertThat(captor.getValue()).isBetween(before, LocalDateTime.now().minusDays(365));
    }
}
