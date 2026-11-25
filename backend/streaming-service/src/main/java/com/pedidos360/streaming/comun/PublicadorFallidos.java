package com.pedidos360.streaming.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.streaming.config.KinesisProperties;
import com.pedidos360.streaming.eventos.EventoFallido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;

/**
 * Escribe un {@link EventoFallido} en el stream de mensajes fallidos (dead letter stream), con reintentos.
 * Clave de partición = aplicación que falló (el stream tiene 1 shard; la clave deja los fallidos de cada
 * aplicación en orden si en el futuro se agregan shards).
 */
@Component
public class PublicadorFallidos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorFallidos.class);

    private final KinesisAsyncClient kinesis;
    private final ObjectMapper mapper;
    private final KinesisProperties props;

    public PublicadorFallidos(KinesisAsyncClient kinesis, ObjectMapper mapper, KinesisProperties props) {
        this.kinesis = kinesis;
        this.mapper = mapper;
        this.props = props;
    }

    public boolean publicar(EventoFallido fallido) {
        final String json = serializar(fallido);
        if (json == null) {
            return false;
        }
        String stream = props.streams().fallidos();
        int max = Math.max(1, props.reintentos().maxIntentosFallidos());
        long espera = Math.max(1, props.reintentos().esperaInicialMs());
        for (int intento = 1; intento <= max; intento++) {
            try {
                String seq = kinesis.putRecord(r -> r.streamName(stream)
                                .partitionKey(fallido.aplicacion())
                                .data(SdkBytes.fromUtf8String(json)))
                        .join().sequenceNumber();
                log.warn("[fallidos] Enviado a {} eventIdOriginal={} aplicacion={} intentos={} causa=\"{}\" seq={}",
                        stream, fallido.eventIdOriginal(), fallido.aplicacion(), fallido.intentos(), fallido.causa(), seq);
                return true;
            } catch (RuntimeException e) {
                log.warn("[fallidos] No se pudo escribir en {} (intento {}/{}): {}", stream, intento, max, e.getMessage());
                dormir(espera);
                espera *= 2;
            }
        }
        return false;
    }

    private String serializar(EventoFallido fallido) {
        try {
            return mapper.writeValueAsString(fallido);
        } catch (Exception e) {
            log.error("[fallidos] No se pudo serializar el fallido {}: {}", fallido.eventId(), e.getMessage());
            return null;
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
