package com.pedidos360.pedidos.entity;

import jakarta.persistence.Embeddable;

@Embeddable
public class PedidoItem {
    private Long productoId;
    private String nombre;
    private Integer cantidad;
    private Double precioUnitario;
    public PedidoItem() {}
    public PedidoItem(Long productoId, String nombre, Integer cantidad, Double precioUnitario) {
        this.productoId = productoId; this.nombre = nombre; this.cantidad = cantidad; this.precioUnitario = precioUnitario;
    }
    public Long getProductoId() { return productoId; }
    public String getNombre() { return nombre; }
    public Integer getCantidad() { return cantidad; }
    public Double getPrecioUnitario() { return precioUnitario; }
}
