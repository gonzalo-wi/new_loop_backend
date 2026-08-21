package com.loop.new_loop_api.integrations.common.service;

import com.loop.new_loop_api.integrations.aguas.service.iService.AguasEquipmentService;
import com.loop.new_loop_api.integrations.aguas.service.iService.AguasIntegrationService;
import com.loop.new_loop_api.integrations.common.entity.IntegrationLog;
import com.loop.new_loop_api.integrations.common.entity.IntegrationName;
import com.loop.new_loop_api.integrations.common.exception.IntegrationLogNotFoundException;
import com.loop.new_loop_api.integrations.common.repository.IntegrationLogRepository;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooDispatchService;
import com.loop.new_loop_api.integrations.odoo.service.iService.OdooRepairService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationRetryDispatcherTest {

    @Mock private IntegrationLogRepository integrationLogRepository;
    @Mock private AguasIntegrationService  aguasIntegrationService;
    @Mock private AguasEquipmentService    aguasEquipmentService;
    @Mock private OdooRepairService        odooRepairService;
    @Mock private OdooDispatchService      odooDispatchService;

    @InjectMocks private IntegrationRetryDispatcher dispatcher;

    private IntegrationLog logWith(IntegrationName name, String operationType, String entityName) {
        return IntegrationLog.builder()
                .integrationName(name)
                .operationType(operationType)
                .entityName(entityName)
                .build();
    }

    @Test
    void should_routeToOdooRepair_when_odooLogIsRepairCreate() {
        var logId = UUID.randomUUID();
        when(integrationLogRepository.findById(logId))
                .thenReturn(Optional.of(logWith(IntegrationName.ODOO, "REPAIR_CREATE", "DispenserMovement")));

        dispatcher.retry(logId);

        verify(odooRepairService).retry(logId);
        verifyNoInteractions(odooDispatchService, aguasEquipmentService, aguasIntegrationService);
    }

    @Test
    void should_routeToOdooDispatch_when_odooLogIsDispatchCreate() {
        var logId = UUID.randomUUID();
        when(integrationLogRepository.findById(logId))
                .thenReturn(Optional.of(logWith(IntegrationName.ODOO, "DISPATCH_CREATE", "DispenserMovement")));

        dispatcher.retry(logId);

        verify(odooDispatchService).retry(logId);
        verifyNoInteractions(odooRepairService, aguasEquipmentService, aguasIntegrationService);
    }

    @Test
    void should_routeToAguasStockControl_when_aguasLogIsStockControl() {
        var logId = UUID.randomUUID();
        when(integrationLogRepository.findById(logId))
                .thenReturn(Optional.of(logWith(IntegrationName.AGUAS, "CONTROL", "StockControl")));

        dispatcher.retry(logId);

        verify(aguasIntegrationService).retry(logId);
        verifyNoInteractions(aguasEquipmentService, odooRepairService, odooDispatchService);
    }

    @Test
    void should_routeToAguasEquipment_when_aguasLogIsDispenserMovement() {
        var logId = UUID.randomUUID();
        when(integrationLogRepository.findById(logId))
                .thenReturn(Optional.of(logWith(IntegrationName.AGUAS, "MOVEMENT", "DispenserMovement")));

        dispatcher.retry(logId);

        verify(aguasEquipmentService).retry(logId);
        verifyNoInteractions(aguasIntegrationService, odooRepairService, odooDispatchService);
    }

    @Test
    void should_throw_when_logNotFound() {
        var logId = UUID.randomUUID();
        when(integrationLogRepository.findById(logId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dispatcher.retry(logId))
                .isInstanceOf(IntegrationLogNotFoundException.class);
    }
}
