package com.pedidos360.streaming.eventos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pedidos360.streaming.comun.EventoStream;
import jakarta.validation.constraints.NotBlank;

/**
 * Evento que se escribe en pedidos360-fallidos-stream cuando un registro no se pudo procesar.
 * Conserva el contenido original y TODO el contexto para diagnosticar y reprocesar:
 * de qué stream, shard y posición (sequenceNumber) venía, qué aplicación falló, cuántos intentos y por qué.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoFallido(
        @NotBlank String eventId,
        @NotBlank String tipoEvento,
        String fallidoEn,
        @NotBlank String aplicacion,
        String streamOrigen,
        String shardId,
        String sequenceNumber,
        String partitionKey,
        String eventIdOriginal,
        int intentos,
        String tipoError,
        @NotBlank String causa,
        String payloadOriginal) implements EventoStream {

    public static final String TIPO = "EVENTO_FALLIDO";
}
