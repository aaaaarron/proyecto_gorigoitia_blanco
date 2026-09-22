package com.pedidos360.pedidos.dto;

import com.pedidos360.pedidos.entity.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public class EstadoPedidoRequest {

    @NotNull(message = "El estado es obligatorio")
    private EstadoPedido estado;

    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
}
