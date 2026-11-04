package com.loop.new_loop_api.stockcontrols.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Stock controls whose remitos are printed together; 100 covers a whole day of routes. */
public record RemitoBatchRequest(
        @NotEmpty(message = "ids must not be empty")
        @Size(max = 100, message = "at most 100 remitos can be printed at once")
        List<@NotNull(message = "ids must not contain null") UUID> ids) {
}
