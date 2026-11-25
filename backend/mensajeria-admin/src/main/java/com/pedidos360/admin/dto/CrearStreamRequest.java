package com.pedidos360.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CrearStreamRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{1,128}", message = "solo letras, números, '_', '-' y '.'") String nombre,
        @NotNull @Min(1) @Max(10) Integer shards,
        @NotNull @Min(24) @Max(8760) Integer retencionHoras,
        @Pattern(regexp = "PROVISIONED|ON_DEMAND") String modo) {}
