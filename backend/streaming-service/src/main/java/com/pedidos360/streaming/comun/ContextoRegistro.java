package com.pedidos360.streaming.comun;

/**
 * Datos de infraestructura del registro que se está procesando. La lógica de dominio los recibe solo
 * para trazabilidad (logs, latencia); no depende de la KCL.
 */
public record ContextoRegistro(
        String aplicacion,
        String stream,
        String shardId,
        String sequenceNumber,
        String partitionKey,
        String instancia,
        boolean enviarAFallidos) {}
