package com.pedidos360.streaming.inventario;

import com.pedidos360.streaming.comun.ContextoRegistro;
import com.pedidos360.streaming.comun.ManejadorEventos;
import com.pedidos360.streaming.eventos.EventoInventario;
import org.springframework.stereotype.Component;

/** Adaptador de la aplicación "inventario" (lee pedidos360-inventario-stream). */
@Component
public class InventarioManejador implements ManejadorEventos<EventoInventario> {

    private final InventarioService service;

    public InventarioManejador(InventarioService service) {
        this.service = service;
    }

    @Override
    public String aplicacion() {
        return "inventario";
    }

    @Override
    public Class<EventoInventario> tipoEvento() {
        return EventoInventario.class;
    }

    @Override
    public void manejar(EventoInventario evento, ContextoRegistro contexto) {
        service.registrar(evento);
    }
}
