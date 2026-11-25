package com.pedidos360.pedidos.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Parcial 3: publica los eventos del pedido en pedidos360-pedidos-stream, con clave de partición = pedidoId.
 * Si Kinesis falla, el pedido no se revierte (igual que con SNS): queda en el log con su eventId.
 */
@Component
@ConditionalOnProperty(name = "streaming.habilitado", havingValue = "true", matchIfMissing = true)
public class KinesisStreamPublisher implements StreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(KinesisStreamPublisher.class);

    private final ProductorKinesis productor;
    private final ObjectMapper mapper;
    private final StreamingProperties props;

    public KinesisStreamPublisher(ProductorKinesis productor, ObjectMapper mapper, StreamingProperties props) {
        this.productor = productor;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public void publicar(EventoPedidoStream evento) {
        try {
            String json = mapper.writeValueAsString(evento);
            ProductorKinesis.Resultado r = productor.enviar(props.streamPedidos(),
                    List.of(new ProductorKinesis.Registro(evento.eventId(), evento.claveParticion(), json)));
            if (!r.completo()) {
                log.error("[kinesis] Evento {} del pedido {} no se pudo publicar en {}",
                        evento.tipoEvento(), evento.pedidoId(), props.streamPedidos());
            }
        } catch (JsonProcessingException e) {
            log.error("[kinesis] No se pudo serializar el evento {}: {}", evento.eventId(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("[kinesis] Error inesperado publicando el evento {}: {}", evento.eventId(), e.getMessage());
        }
    }
}
