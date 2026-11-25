package com.pedidos360.pedidos.streaming;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parcial 3: configuración centralizada del productor Kinesis (application.yml, prefijo "streaming").
 * Ningún nombre de stream, región ni parámetro de reintento va escrito en el código.
 *
 * @param habilitado    false = se usa {@link NoopStreamPublisher} (tests o ejecución sin AWS)
 * @param region        región del cliente Kinesis
 * @param streamPedidos stream de negocio donde se publican los eventos del ciclo de vida del pedido
 * @param envio         reintentos y timeouts del envío
 */
@ConfigurationProperties(prefix = "streaming")
public record StreamingProperties(
        boolean habilitado,
        String region,
        String streamPedidos,
        Envio envio) {

    /**
     * @param maxIntentos      intentos de PutRecords para los registros que fallen (p. ej. por throttling del shard)
     * @param esperaInicialMs  espera antes del 2.º intento; se duplica en cada intento (espera creciente)
     * @param timeoutLlamadaMs tiempo máximo de cada llamada a Kinesis (la petición del usuario no puede quedar colgada)
     */
    public record Envio(int maxIntentos, long esperaInicialMs, long timeoutLlamadaMs) {}
}
