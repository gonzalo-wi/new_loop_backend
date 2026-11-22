package com.loop.new_loop_api.settlements.service.iService;

import com.loop.new_loop_api.settlements.dto.ReceiptUpload;

public interface DriverReceiptService {

    /** Forwards the driver's receipt photo to Aguas, once. Returns normally only when Aguas accepted it. */
    void upload(ReceiptUpload upload);
}
