package com.loop.new_loop_api.dispensers.service;

import com.loop.new_loop_api.dispensers.dto.SerialHistoryResponse;
import com.loop.new_loop_api.dispensers.entity.DispenserMovement;
import com.loop.new_loop_api.dispensers.repository.DispenserMovementRepository;
import com.loop.new_loop_api.dispensers.service.iService.DispenserSerialHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DispenserSerialHistoryServiceImpl implements DispenserSerialHistoryService {

    static final int MAX_ENTRIES = 100;

    private final DispenserMovementRepository dispenserMovementRepository;

    @Override
    @Transactional(readOnly = true)
    public SerialHistoryResponse getHistory(String serial) {
        var normalized = serial.trim();
        // One extra row tells whether the history was cut.
        var movements = dispenserMovementRepository.findBySerial(normalized, PageRequest.of(0, MAX_ENTRIES + 1));
        var truncated = movements.size() > MAX_ENTRIES;
        var entries   = movements.stream()
                .limit(MAX_ENTRIES)
                .map(movement -> toEntry(movement, normalized))
                .toList();
        return new SerialHistoryResponse(normalized, entries, truncated);
    }

    private static SerialHistoryResponse.Entry toEntry(DispenserMovement movement, String serial) {
        var delivery = SerialDeliveryResolver.resolve(movement, serial);
        return new SerialHistoryResponse.Entry(
                movement.getId(),
                movement.getType(),
                movement.getStatus(),
                movement.getRouteCode(),
                movement.getTechnician(),
                movement.getMovementDate(),
                movement.getRegisteredByUsername(),
                movement.getCreatedAt(),
                delivery.excluded(),
                delivery.exclusionReason(),
                delivery.aguas(),
                delivery.odoo(),
                delivery.odooPickingName());
    }
}
