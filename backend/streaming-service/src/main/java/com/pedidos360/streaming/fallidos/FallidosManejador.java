package com.pedidos360.streaming.fallidos;

import com.pedidos360.streaming.comun.ContextoRegistro;
import com.pedidos360.streaming.comun.ManejadorEventos;
import com.pedidos360.streaming.eventos.EventoFallido;
import org.springframework.stereotype.Component;

/** Adaptador de la aplicación "fallidos" (lee pedidos360-fallidos-stream). */
@Component
public class FallidosManejador implements ManejadorEventos<EventoFallido> {

    private final FallidosService service;

    public FallidosManejador(FallidosService service) {
        this.service = service;
    }

    @Override
    public String aplicacion() {
        return "fallidos";
    }

    @Override
    public Class<EventoFallido> tipoEvento() {
        return EventoFallido.class;
    }

    @Override
    public void manejar(EventoFallido evento, ContextoRegistro contexto) {
        service.registrar(evento);
    }
}
