package com.pedidos360.streaming.comun;

/** Contrato mínimo de todo evento que viaja por los streams: un id único y un tipo. */
public interface EventoStream {
    String eventId();
    String tipoEvento();
}
