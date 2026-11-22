package com.loop.new_loop_api.settlements.dto;

import java.util.UUID;
import org.springframework.core.io.InputStreamSource;

/**
 * A receipt photo as the web layer read it. The content is opened later, after the size check, so a file over
 * the limit is never loaded into memory just to be rejected.
 */
public record ReceiptUpload(UUID routeId, String declaredType, long size, InputStreamSource content) {
}
