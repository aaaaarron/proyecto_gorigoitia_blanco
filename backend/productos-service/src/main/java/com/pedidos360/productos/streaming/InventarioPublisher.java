package com.pedidos360.productos.streaming;

import java.util.List;

/**
 * Parcial 3: puerto de salida hacia el stream de inventario. ProductoService no conoce Kinesis.
 * Recibe una lista porque una reserva puede generar dos eventos (STOCK_RESERVADO + STOCK_BAJO),
 * que se envían juntos en un solo PutRecords.
 */
public interface InventarioPublisher {

    /** Nunca lanza excepción: un fallo de streaming no revierte la reserva de stock. */
    void publicar(List<EventoInventario> eventos);
}
