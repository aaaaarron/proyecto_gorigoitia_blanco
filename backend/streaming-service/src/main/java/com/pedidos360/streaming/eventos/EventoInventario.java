package com.pedidos360.streaming.eventos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pedidos360.streaming.comun.EventoStream;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Mismo contrato JSON que publica productos-service (com.pedidos360.productos.streaming.EventoInventario). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoInventario(
        @NotBlank String eventId,
        @NotBlank @Pattern(regexp = "STOCK_RESERVADO|STOCK_BAJO|STOCK_AJUSTADO", message = "tipo de evento desconocido")
        String tipoEvento,
        @NotNull @Min(1) Integer version,
        @NotBlank String ocurridoEn,
        String origen,
        @NotNull @Positive Long productoId,
        String nombre,
        @PositiveOrZero Integer cantidad,
        @NotNull @PositiveOrZero Integer stockRestante) implements EventoStream {
}
