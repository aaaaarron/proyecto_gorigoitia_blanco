package com.pedidos360.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Kinesis solo permite entre la mitad y el doble de los shards abiertos actuales (UNIFORM_SCALING). */
public record CambiarShardsRequest(@NotNull @Min(1) @Max(10) Integer shards) {}
