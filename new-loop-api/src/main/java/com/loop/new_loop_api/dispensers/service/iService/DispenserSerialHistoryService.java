package com.loop.new_loop_api.dispensers.service.iService;

import com.loop.new_loop_api.dispensers.dto.SerialHistoryResponse;

public interface DispenserSerialHistoryService {

    SerialHistoryResponse getHistory(String serial);
}
