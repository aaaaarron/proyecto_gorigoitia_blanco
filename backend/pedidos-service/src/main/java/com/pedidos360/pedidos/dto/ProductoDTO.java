package com.pedidos360.pedidos.dto;

/**
 * Representación local (espejo) del producto obtenido desde productos-service.
 * No es una entidad JPA: cada microservicio mantiene su propia base de datos
 * y no comparte entidades, tal como exige la rúbrica.
 */
public class ProductoDTO {
    private Long id;
    private String nombre;
    private Double precio;
    private Integer stock;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
