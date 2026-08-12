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
import org.springframework.security.core.context.SecurityContextHolder;

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
}
