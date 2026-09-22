package com.pedidos360.pedidos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;

public class PedidoRequest {
    public static class Item {
        @NotNull(message = "El producto es obligatorio") private Long productoId;
        @NotNull(message = "La cantidad es obligatoria") @Positive(message = "La cantidad debe ser mayor a 0") private Integer cantidad;
        public Long getProductoId() { return productoId; }
        public void setProductoId(Long productoId) { this.productoId = productoId; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    }

    @Valid private List<Item> items;
    private Long productoId;
    private Integer cantidad;
    @NotBlank(message = "Selecciona retiro o envío") @Pattern(regexp = "RETIRO|ENVIO", message = "Tipo de entrega inválido") private String tipoEntrega = "RETIRO";
    private String direccion;
    public List<Item> getItems() {
        if (items != null && !items.isEmpty()) return items;
        if (productoId == null || cantidad == null) return items;
        Item legacy = new Item(); legacy.setProductoId(productoId); legacy.setCantidad(cantidad);
        return List.of(legacy);
    }
    public void setItems(List<Item> items) { this.items = items; }
    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    @AssertTrue(message = "Agrega al menos un producto con cantidad válida")
    public boolean isPedidoValido() {
        if (items != null && !items.isEmpty()) return items.stream().allMatch(i -> i != null && i.getProductoId() != null && i.getProductoId() > 0 && i.getCantidad() != null && i.getCantidad() > 0);
        return productoId != null && productoId > 0 && cantidad != null && cantidad > 0;
    }
    public String getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(String tipoEntrega) { this.tipoEntrega = tipoEntrega; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}
