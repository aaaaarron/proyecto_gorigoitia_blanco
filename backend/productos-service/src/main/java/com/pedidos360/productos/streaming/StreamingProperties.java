package com.pedidos360.productos.streaming;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parcial 3: configuración centralizada del productor Kinesis de inventario (application.yml, prefijo "streaming").
 *
 * @param habilitado        false = se usa {@link NoopInventarioPublisher} (tests o ejecución sin AWS)
 * @param region            región del cliente Kinesis
 * @param streamInventario  stream de negocio de movimientos de stock
 * @param umbralStockBajo   si tras una reserva el stock queda en este valor o menos, se emite STOCK_BAJO
 * @param envio             reintentos y timeouts del envío
 */
@ConfigurationProperties(prefix = "streaming")
public record StreamingProperties(
        boolean habilitado,
        String region,
        String streamInventario,
        int umbralStockBajo,
        Envio envio) {

    public record Envio(int maxIntentos, long esperaInicialMs, long timeoutLlamadaMs) {}
}
