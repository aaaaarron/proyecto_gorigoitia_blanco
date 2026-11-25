package com.pedidos360.streaming.analitica;

import com.pedidos360.streaming.comun.ContextoRegistro;
import com.pedidos360.streaming.comun.ManejadorEventos;
import com.pedidos360.streaming.eventos.EventoPedidoStream;
import org.springframework.stereotype.Component;

/** Adaptador de la aplicación "analitica" (lee pedidos360-pedidos-stream). */
@Component
public class AnaliticaManejador implements ManejadorEventos<EventoPedidoStream> {

    private final AnaliticaService service;

    public AnaliticaManejador(AnaliticaService service) {
        this.service = service;
    }

    @Override
    public String aplicacion() {
        return "analitica";
    }

    @Override
    public Class<EventoPedidoStream> tipoEvento() {
        return EventoPedidoStream.class;
    }

    @Override
    public void manejar(EventoPedidoStream evento, ContextoRegistro contexto) {
        service.registrar(evento);
    }
}
