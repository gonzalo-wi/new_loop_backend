package com.loop.new_loop_api.loadforecast.domain;

import java.util.UUID;

/** One product line of a past exit control: how many units went on the truck. */
public record LoadedProduct(UUID productId, String code, String name, Integer displayOrder, int quantity) {
}
