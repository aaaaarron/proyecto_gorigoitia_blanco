package com.pedidos360.pedidos.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Se usa cuando streaming.habilitado=false (tests o ejecución sin AWS). Solo registra el evento. */
@Component
@ConditionalOnProperty(name = "streaming.habilitado", havingValue = "false")
public class NoopStreamPublisher implements StreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopStreamPublisher.class);

    @Override
    public void publicar(EventoPedidoStream evento) {
        log.info("[streaming deshabilitado] Evento {} del pedido {} no publicado en Kinesis", evento.tipoEvento(), evento.pedidoId());
    }
}
