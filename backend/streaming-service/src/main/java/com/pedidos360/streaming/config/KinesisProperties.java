package com.pedidos360.streaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuración centralizada de Kinesis (application.yml, prefijo "kinesis"): región, nombres de streams,
 * nombres de las aplicaciones consumidoras, posición inicial, reintentos y parámetros de lectura.
 * Ningún nombre ni valor de Kinesis va escrito en el código.
 */
@ConfigurationProperties(prefix = "kinesis")
public record KinesisProperties(
        String region,
        boolean consumidoresHabilitados,
        String instanciaId,
        String posicionInicial,
        Streams streams,
        Map<String, Aplicacion> aplicaciones,
        Reintentos reintentos,
        Lectura lectura) {

    /** Nombres de los streams (deben coincidir con infra/6-kinesis-streams.ps1 y mensajeria-admin). */
    public record Streams(String pedidos, String inventario, String fallidos) {}

    /**
     * Aplicación consumidora (consumer group).
     *
     * @param nombre           nombre de la aplicación KCL = nombre de su tabla de coordinación en DynamoDB
     * @param stream           stream que lee
     * @param habilitada       si esta instancia ejecuta la aplicación
     * @param enviarAFallidos  si los eventos irrecuperables se envían al stream de fallidos
     *                         (false en la propia aplicación de fallidos, para no crear un ciclo)
     */
    public record Aplicacion(String nombre, String stream, boolean habilitada, boolean enviarAFallidos) {}

    /**
     * @param maxIntentos          intentos de procesar un evento ante errores transitorios
     * @param esperaInicialMs      espera antes del 2.º intento
     * @param multiplicador        factor de la espera creciente (200 ms → 400 ms → 800 ms...)
     * @param maxIntentosFallidos  intentos de escribir en el stream de fallidos
     */
    public record Reintentos(int maxIntentos, long esperaInicialMs, double multiplicador, int maxIntentosFallidos) {}

    /**
     * @param maxRegistros  máximo de registros por GetRecords
     * @param intervaloMs   espera entre lecturas de un shard (1 s respeta el límite de 5 GetRecords/s por shard
     *                      aunque haya varias aplicaciones leyendo el mismo stream)
     */
    public record Lectura(int maxRegistros, long intervaloMs) {}

    public Aplicacion aplicacion(String clave) {
        Aplicacion a = aplicaciones == null ? null : aplicaciones.get(clave);
        if (a == null) {
            throw new IllegalArgumentException("Aplicación consumidora no configurada: " + clave);
        }
        return a;
    }
}
