package com.pedidos360.productos.streaming;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Parcial 3: publica los movimientos de stock en pedidos360-inventario-stream (clave = productoId). */
@Component
@ConditionalOnProperty(name = "streaming.habilitado", havingValue = "true", matchIfMissing = true)
public class KinesisInventarioPublisher implements InventarioPublisher {

    private static final Logger log = LoggerFactory.getLogger(KinesisInventarioPublisher.class);

    private final ProductorKinesis productor;
    private final ObjectMapper mapper;
    private final StreamingProperties props;

    public KinesisInventarioPublisher(ProductorKinesis productor, ObjectMapper mapper, StreamingProperties props) {
        this.productor = productor;
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public void publicar(List<EventoInventario> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return;
        }
        try {
            List<ProductorKinesis.Registro> registros = new ArrayList<>();
            for (EventoInventario e : eventos) {
                registros.add(new ProductorKinesis.Registro(e.eventId(), e.claveParticion(), mapper.writeValueAsString(e)));
            }
            ProductorKinesis.Resultado r = productor.enviar(props.streamInventario(), registros);
            if (!r.completo()) {
                log.error("[kinesis] {} de {} eventos de inventario no se pudieron publicar en {}",
                        r.fallidos(), eventos.size(), props.streamInventario());
            }
        } catch (JsonProcessingException e) {
            log.error("[kinesis] No se pudo serializar un evento de inventario: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("[kinesis] Error inesperado publicando inventario: {}", e.getMessage());
        }
    }
}
