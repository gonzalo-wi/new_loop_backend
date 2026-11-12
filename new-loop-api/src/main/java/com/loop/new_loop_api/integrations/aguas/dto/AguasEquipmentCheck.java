package com.loop.new_loop_api.integrations.aguas.dto;

/**
 * Whether a scanned serial exists in Aguas. {@code existe} is null when Aguas could not be asked or gave an
 * ambiguous answer: callers must treat that as "unknown", never as missing.
 */
public record AguasEquipmentCheck(String serie, Boolean existe, String nroSerieAguas, String ubicacion,
                                  String estado) {}
