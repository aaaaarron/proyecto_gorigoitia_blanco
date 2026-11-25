package com.pedidos360.admin.dto;

import java.util.List;
import java.util.Map;

/** Resultado de un envío de prueba: cuántos registros entraron, cuántos fallaron y en qué shard cayó cada uno. */
public record ResultadoPublicacion(String stream, int enviados, int fallidos, Map<String, Integer> porShard,
                                   List<String> detalle) {}
