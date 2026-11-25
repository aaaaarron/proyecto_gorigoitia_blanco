package com.pedidos360.productos.streaming;

import java.time.Instant;
import java.util.UUID;

/**
 * Parcial 3: DTO del evento que se publica en pedidos360-inventario-stream (JSON).
 *
 * <ul>
 *   <li>{@code eventId}: único por evento, para que el consumidor descarte duplicados.</li>
 *   <li>{@code tipoEvento}: STOCK_RESERVADO, STOCK_BAJO o STOCK_AJUSTADO.</li>
 *   <li>{@code productoId}: CLAVE DE PARTICIÓN. Los movimientos de un mismo producto quedan en el mismo shard
 *       y se procesan en orden, así el stock proyectado por el consumidor nunca "retrocede".</li>
 *   <li>{@code cantidad} / {@code stockRestante}: el consumidor no necesita consultar productos-service.</li>
 * </ul>
 */
public record EventoInventario(
        String eventId,
        String tipoEvento,
        int version,
        String ocurridoEn,
        String origen,
        Long productoId,
        String nombre,
        Integer cantidad,
        Integer stockRestante) {

    public static final String STOCK_RESERVADO = "STOCK_RESERVADO";
    public static final String STOCK_BAJO = "STOCK_BAJO";
    public static final String STOCK_AJUSTADO = "STOCK_AJUSTADO";

    public static EventoInventario de(String tipo, Long productoId, String nombre, Integer cantidad, Integer stockRestante) {
        return new EventoInventario(UUID.randomUUID().toString(), tipo, 1, Instant.now().toString(),
                "productos-service", productoId, nombre, cantidad, stockRestante);
    }

    public String claveParticion() {
        return String.valueOf(productoId);
    }
}
