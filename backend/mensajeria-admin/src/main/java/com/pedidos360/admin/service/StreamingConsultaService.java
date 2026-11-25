package com.pedidos360.admin.service;

import com.pedidos360.admin.config.StreamingProperties;
import com.pedidos360.admin.dto.EstadoInstanciaStreaming;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Parcial 3: consulta las instancias de streaming-service por HTTP (red interna), igual que
 * ConsumidorService hace con eventos-service. streaming-service no se publica en API Gateway:
 * el frontend lo ve solo a través de mensajeria-admin.
 */
@Service
public class StreamingConsultaService {

    private final RestClient http;
    private final List<String> instancias;

    public StreamingConsultaService(RestClient.Builder builder, StreamingProperties props) {
        this.http = builder.build();
        String urls = props.serviceUrls() == null ? "" : props.serviceUrls();
        this.instancias = Arrays.stream(urls.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Estado (shards asignados, posición de lectura, retraso) de CADA instancia. */
    public List<EstadoInstanciaStreaming> estados() {
        List<EstadoInstanciaStreaming> resultado = new ArrayList<>();
        for (String base : instancias) {
            try {
                EstadoInstanciaStreaming e = http.get().uri(base + "/api/streaming/estado").retrieve()
                        .body(EstadoInstanciaStreaming.class);
                if (e != null) resultado.add(e.conUrl(base));
            } catch (RestClientException ex) {
                resultado.add(EstadoInstanciaStreaming.noDisponible(base, "Instancia no disponible"));
            }
        }
        return resultado;
    }

    /**
     * Datos de dominio (métricas, seguimiento, stock, alertas, fallidos). Están en la base de datos compartida,
     * así que basta con la primera instancia que responda.
     */
    public Object consultar(String ruta) {
        RestClientException ultimo = null;
        for (String base : instancias) {
            try {
                return http.get().uri(base + "/api/streaming" + ruta).retrieve().body(Object.class);
            } catch (RestClientException ex) {
                ultimo = ex;
            }
        }
        throw new IllegalStateException("Ninguna instancia de streaming-service responde"
                + (ultimo != null ? ": " + ultimo.getMessage() : " (revisar streaming.service-urls)"));
    }

    /** Activa o desactiva el fallo simulado de la analítica en TODAS las instancias. */
    public List<Object> simulacion(boolean falloAnalitica) {
        List<Object> resultado = new ArrayList<>();
        for (String base : instancias) {
            try {
                resultado.add(http.post().uri(base + "/api/streaming/simulacion?falloAnalitica=" + falloAnalitica)
                        .retrieve().body(Object.class));
            } catch (RestClientException ex) {
                resultado.add(Map.of("url", base, "error", "Instancia no disponible"));
            }
        }
        return resultado;
    }
}
