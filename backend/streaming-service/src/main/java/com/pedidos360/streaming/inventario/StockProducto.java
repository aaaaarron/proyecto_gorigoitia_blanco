package com.pedidos360.streaming.inventario;

import jakarta.persistence.*;

import java.time.Instant;

/** Stock proyectado de cada producto, según el último movimiento recibido por el stream de inventario. */
@Entity
@Table(name = "stock_productos")
public class StockProducto {

    @Id
    private Long productoId;

    private String nombre;

    private Integer stock;

    @Column(length = 40)
    private String ultimoMovimiento;

    private Instant actualizadoEn;

    protected StockProducto() {}

    public StockProducto(Long productoId) {
        this.productoId = productoId;
    }

    public void actualizar(String nombre, Integer stock, String movimiento) {
        if (nombre != null) this.nombre = nombre;
        this.stock = stock;
        this.ultimoMovimiento = movimiento;
        this.actualizadoEn = Instant.now();
    }

    public Long getProductoId() { return productoId; }
    public String getNombre() { return nombre; }
    public Integer getStock() { return stock; }
    public String getUltimoMovimiento() { return ultimoMovimiento; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
