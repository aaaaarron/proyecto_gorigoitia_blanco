package com.pedidos360.streaming.eventos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pedidos360.streaming.comun.EventoStream;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Mismo contrato JSON que publica pedidos-service (com.pedidos360.pedidos.streaming.EventoPedidoStream).
 * Las anotaciones definen qué es un evento válido: si alguna no se cumple, el evento es irrecuperable y va
 * al stream de fallidos sin reintentar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoPedidoStream(
        @NotBlank String eventId,
        @NotBlank @Pattern(regexp = "PEDIDO_CREADO|PEDIDO_ESTADO_ACTUALIZADO", message = "tipo de evento desconocido")
        String tipoEvento,
        @NotNull @Min(1) Integer version,
        @NotBlank String ocurridoEn,
        String origen,
        @NotNull @Positive Long pedidoId,
        String usuarioEmail,
        @NotBlank String estado,
        String tipoEntrega,
        @NotNull @PositiveOrZero Double total,
        Integer cantidadItems) implements EventoStream {
}
