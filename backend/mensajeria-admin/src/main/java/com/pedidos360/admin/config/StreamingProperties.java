package com.pedidos360.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Parcial 3: declaración de la plataforma de streaming (application.yml, prefijo "streaming").
 * Los parámetros de administración de cada stream (modo de capacidad, shards y retención) quedan declarados
 * aquí, en código versionado, y {@link com.pedidos360.admin.service.StreamAdminService} los aplica en AWS.
 */
@ConfigurationProperties(prefix = "streaming")
public record StreamingProperties(
        boolean aplicarAlIniciar,
        String region,
        String serviceUrls,
        boolean monitoreoPorShard,
        List<StreamDeclarado> streams,
        List<AplicacionDeclarada> aplicaciones,
        Alarma alarma) {

    /**
     * @param rol           NEGOCIO o FALLIDOS
     * @param modo          PROVISIONED u ON_DEMAND
     * @param justificacion por qué esos valores (se muestra en el panel y en GET /api/admin/streams/declarados)
     */
    public record StreamDeclarado(String nombre, String rol, String modo, int shards, int retencionHoras,
                                  String justificacion) {}

    /** Aplicación consumidora: su nombre es también el de su tabla de coordinación en DynamoDB. */
    public record AplicacionDeclarada(String nombre, String stream, String proposito) {}

    /** Alarma de CloudWatch sobre el retraso de consumo (GetRecords.IteratorAgeMilliseconds). */
    public record Alarma(String nombre, String stream, long umbralMs, int periodos, String snsTopicArn) {}

    public StreamDeclarado stream(String nombre) {
        return streams == null ? null : streams.stream().filter(s -> s.nombre().equals(nombre)).findFirst().orElse(null);
    }
}
