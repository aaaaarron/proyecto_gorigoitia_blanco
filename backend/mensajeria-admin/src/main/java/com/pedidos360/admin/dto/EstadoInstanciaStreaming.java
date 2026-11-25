package com.pedidos360.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Lo que informa GET /api/streaming/estado de cada instancia de streaming-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EstadoInstanciaStreaming(
        String instancia,
        boolean consumidoresActivos,
        List<String> aplicaciones,
        String posicionInicial,
        boolean falloAnaliticaSimulado,
        List<ShardLocal> shards,
        String url,
        String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShardLocal(String aplicacion, String stream, String shardId, String instancia,
                             long procesados, long duplicados, long fallidos,
                             String ultimoSequenceNumber, Long millisBehindLatest,
                             String ultimaLectura, String asignadoEn) {}

    public EstadoInstanciaStreaming conUrl(String url) {
        return new EstadoInstanciaStreaming(instancia, consumidoresActivos, aplicaciones, posicionInicial,
                falloAnaliticaSimulado, shards, url, error);
    }

    public static EstadoInstanciaStreaming noDisponible(String url, String error) {
        return new EstadoInstanciaStreaming(null, false, List.of(), null, false, List.of(), url, error);
    }
}
