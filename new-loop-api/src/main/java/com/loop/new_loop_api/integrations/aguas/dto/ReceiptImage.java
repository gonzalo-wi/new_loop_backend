package com.loop.new_loop_api.integrations.aguas.dto;

import com.loop.new_loop_api.settlements.domain.ReceiptImageFormat;

/** A receipt photo on its way to Aguas. {@code toString} never prints the bytes, so they cannot reach a log. */
public record ReceiptImage(ReceiptImageFormat format, byte[] bytes) {

    @Override
    public String toString() {
        return "ReceiptImage[format=" + format + ", size=" + bytes.length + "]";
    }
}
