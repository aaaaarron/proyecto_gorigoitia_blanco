package com.pedidos360.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Retención en horas: mínimo 24 (1 día), máximo 8760 (365 días). */
public record CambiarRetencionRequest(@NotNull @Min(24) @Max(8760) Integer horas) {}
