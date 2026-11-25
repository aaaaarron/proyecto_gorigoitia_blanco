package com.pedidos360.productos.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Se usa cuando streaming.habilitado=false (tests o ejecución sin AWS). */
@Component
@ConditionalOnProperty(name = "streaming.habilitado", havingValue = "false")
public class NoopInventarioPublisher implements InventarioPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopInventarioPublisher.class);

    @Override
    public void publicar(List<EventoInventario> eventos) {
        eventos.forEach(e -> log.info("[streaming deshabilitado] {} producto={} no publicado", e.tipoEvento(), e.productoId()));
    }
}
