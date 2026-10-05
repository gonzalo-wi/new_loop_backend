package com.loop.new_loop_api.integrations.common.service;

import com.loop.new_loop_api.audit.service.iService.AuditService;
import com.loop.new_loop_api.integrations.common.dto.IntegrationLogResponse;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.exception.NothingToRetryException;
import com.loop.new_loop_api.integrations.common.mapper.IntegrationLogMapper;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationLogServiceImplTest {

    @Mock private IntegrationLogRepository   integrationLogRepository;
    @Mock private IntegrationLogMapper       integrationLogMapper;
    @Mock private IntegrationRetryDispatcher integrationRetryDispatcher;
    @Mock private AuditService               auditService;

    @InjectMocks private IntegrationLogServiceImpl service;

    @Test
    void manualRetryShouldRetryAndLeaveAnAuditTrail() {
        var logId = UUID.randomUUID();
        var after = logWithRetries(1);
        when(integrationLogRepository.findById(logId)).thenReturn(Optional.of(logWithRetries(0)), Optional.of(after));
        when(integrationLogMapper.toResponse(after)).thenReturn(IntegrationLogResponse.builder().id(logId).build());

        service.retryManually(logId);

        InOrder order = inOrder(integrationRetryDispatcher, auditService);
        order.verify(integrationRetryDispatcher).retry(logId);
        order.verify(auditService).register(eq("RETRY_INTEGRATION"), eq("IntegrationLog"), eq(logId), any(), any());
    }

    @Test
    void manualRetryThatDidNotAttemptAnythingShouldFailWithoutAuditing() {
        // Handlers return quietly when there is nothing to do (already sent, record gone, no serials...).
        var logId = UUID.randomUUID();
        when(integrationLogRepository.findById(logId)).thenReturn(Optional.of(logWithRetries(2)), Optional.of(logWithRetries(2)));

        assertThatThrownBy(() -> service.retryManually(logId)).isInstanceOf(NothingToRetryException.class);
        verifyNoInteractions(auditService);
    }

    private static IntegrationLog logWithRetries(int retries) {
        var log = new IntegrationLog();
        log.setRetryCount(retries);
        return log;
    }
}
