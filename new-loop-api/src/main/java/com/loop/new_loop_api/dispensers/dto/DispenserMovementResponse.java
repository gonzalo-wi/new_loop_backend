package com.loop.new_loop_api.dispensers.dto;

import com.loop.new_loop_api.dispensers.entity.DispenserMovementStatus;
import com.loop.new_loop_api.dispensers.entity.DispenserMovementType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DispenserMovementResponse {

    private UUID                    id;
    private DispenserMovementType   type;
    private String                  routeCode;
    private String                  technician;
    private Integer                 locationId;
    private Integer                 stateId;
    private LocalDate               movementDate;
    private DispenserMovementStatus status;
    private List<String>            serials;
    private String                  aguasMovementId;
    private String                  odooStatus;
    private Integer                 odooPickingId;
    private String                  odooPickingName;
    private UUID                    registeredBy;
    private String                  registeredByUsername;
    private LocalDateTime           createdAt;
    private LocalDateTime           updatedAt;
}
