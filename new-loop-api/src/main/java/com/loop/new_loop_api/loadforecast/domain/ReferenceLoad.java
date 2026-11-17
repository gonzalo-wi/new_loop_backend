package com.loop.new_loop_api.loadforecast.domain;

import java.time.LocalDate;
import java.util.List;

/** What a route took on one reference day, read from that day's exit control. */
public record ReferenceLoad(LocalDate date, List<LoadedProduct> products) {
}
