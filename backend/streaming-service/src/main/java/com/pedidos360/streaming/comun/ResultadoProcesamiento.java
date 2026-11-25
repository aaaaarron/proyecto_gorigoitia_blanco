package com.pedidos360.streaming.comun;

public enum ResultadoProcesamiento {
    /** Procesado con éxito. */
    OK,
    /** El eventId ya se había procesado en esta aplicación: se ignora (idempotencia). */
    DUPLICADO,
    /** Irrecuperable o agotó los reintentos: enviado al stream de fallidos. */
    FALLIDO
}
