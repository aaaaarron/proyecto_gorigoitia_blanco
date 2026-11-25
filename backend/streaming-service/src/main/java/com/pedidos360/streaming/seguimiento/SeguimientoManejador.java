package com.pedidos360.streaming.seguimiento;

import com.pedidos360.streaming.comun.ContextoRegistro;
import com.pedidos360.streaming.comun.ManejadorEventos;
import com.pedidos360.streaming.eventos.EventoPedidoStream;
import org.springframework.stereotype.Component;

/** Adaptador de la aplicación "seguimiento" (lee pedidos360-pedidos-stream). */
@Component
public class SeguimientoManejador implements ManejadorEventos<EventoPedidoStream> {

    private final SeguimientoService service;

    public SeguimientoManejador(SeguimientoService service) {
        this.service = service;
    }

    @Override
    public String aplicacion() {
        return "seguimiento";
    }

    @Override
    public Class<EventoPedidoStream> tipoEvento() {
        return EventoPedidoStream.class;
    }

    @Override
    public void manejar(EventoPedidoStream evento, ContextoRegistro contexto) {
        service.registrar(evento, contexto);
    }
}
