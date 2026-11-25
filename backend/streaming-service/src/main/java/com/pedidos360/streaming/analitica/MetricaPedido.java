package com.pedidos360.streaming.analitica;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Métrica acumulada en tiempo real (una fila por clave): PEDIDOS_CREADOS, VENTAS_TOTALES,
 * ENTREGA:ENVIO, ESTADO:CONFIRMADO, MINUTO:2026-11-24T21:55 ...
 * valor = suma de montos, cantidad = cantidad de eventos.
 */
@Entity
@Table(name = "metricas_pedidos")
public class MetricaPedido {

    @Id
    @Column(length = 80)
    private String clave;

    @Column(nullable = false)
    private double valor;

    @Column(nullable = false)
    private long cantidad;

    private Instant actualizadoEn;

    protected MetricaPedido() {}

    public String getClave() { return clave; }
    public double getValor() { return valor; }
    public long getCantidad() { return cantidad; }
    public Instant getActualizadoEn() { return actualizadoEn; }
}
