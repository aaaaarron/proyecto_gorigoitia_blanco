package com.pedidos360.pedidos.dto;

import com.pedidos360.pedidos.entity.EstadoPedido;

import java.time.LocalDateTime;
import java.util.List;

public class PedidoDTO {
    private Long id;
    private String usuarioEmail;
    private Long productoId;
    private Integer cantidad;
    private Double precioUnitario;
    private Double total;
    private EstadoPedido estado;
    private LocalDateTime fechaCreacion;
    private List<Item> items;
    private String tipoEntrega;
    private String direccion;

    public static class Item {
        private Long productoId;
        private String nombre;
        private Integer cantidad;
        private Double precioUnitario;
        public Item(Long productoId, String nombre, Integer cantidad, Double precioUnitario) {
            this.productoId = productoId; this.nombre = nombre; this.cantidad = cantidad; this.precioUnitario = precioUnitario;
        }
        public Long getProductoId() { return productoId; }
        public String getNombre() { return nombre; }
        public Integer getCantidad() { return cantidad; }
        public Double getPrecioUnitario() { return precioUnitario; }
    }

    public PedidoDTO() {}

    public PedidoDTO(Long id, String usuarioEmail, Long productoId, Integer cantidad,
                      Double precioUnitario, Double total, EstadoPedido estado, LocalDateTime fechaCreacion) {
        this.id = id;
        this.usuarioEmail = usuarioEmail;
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.total = total;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsuarioEmail() { return usuarioEmail; }
    public void setUsuarioEmail(String usuarioEmail) { this.usuarioEmail = usuarioEmail; }
    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public Double getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(Double precioUnitario) { this.precioUnitario = precioUnitario; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public String getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(String tipoEntrega) { this.tipoEntrega = tipoEntrega; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}
