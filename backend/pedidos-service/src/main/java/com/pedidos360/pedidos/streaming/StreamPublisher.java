package com.pedidos360.pedidos.streaming;

/**
 * Parcial 3: puerto de salida hacia el streaming de eventos. PedidoService solo conoce esta interfaz;
 * no sabe nada de Kinesis ni del SDK. Convive con {@link com.pedidos360.pedidos.messaging.EventoPublisher}
 * (SNS/SQS del Parcial 2), que sigue funcionando igual.
 */
public interface StreamPublisher {

    /** Publica el evento. Nunca lanza excepción: un fallo de streaming no revierte el pedido. */
    void publicar(EventoPedidoStream evento);
}
