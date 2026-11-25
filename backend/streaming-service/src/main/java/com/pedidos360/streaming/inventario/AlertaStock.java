package com.pedidos360.streaming.inventario;

import jakarta.persistence.*;

import java.time.Instant;

/** Alerta generada al recibir STOCK_BAJO: el producto quedó en el umbral o bajo él. */
@Entity
@Table(name = "alertas_stock")
public class AlertaStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productoId;

    private String nombre;

    private Integer stockRestante;

    @Column(length = 80)
    private String eventId;

    private Instant creadaEn;

    protected AlertaStock() {}

    public AlertaStock(Long productoId, String nombre, Integer stockRestante, String eventId) {
        this.productoId = productoId;
        this.nombre = nombre;
        this.stockRestante = stockRestante;
        this.eventId = eventId;
        this.creadaEn = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProductoId() { return productoId; }
    public String getNombre() { return nombre; }
    public Integer getStockRestante() { return stockRestante; }
    public String getEventId() { return eventId; }
    public Instant getCreadaEn() { return creadaEn; }
}
