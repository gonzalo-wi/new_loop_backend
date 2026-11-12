package com.loop.new_loop_api.integrations.aguas.service.iService;

import com.loop.new_loop_api.integrations.aguas.dto.AguasEquipmentCheck;

import java.util.List;
import java.util.Set;

public interface AguasEquipmentLookupService {

    /** One check per distinct (trimmed) serial, in request order. A failed lookup yields {@code existe=null}. */
    List<AguasEquipmentCheck> check(List<String> serials);

    /** Serials Aguas positively reports as not existing, trimmed and upper-cased. Failed lookups are left out. */
    Set<String> findMissingSerials(List<String> serials);
}
