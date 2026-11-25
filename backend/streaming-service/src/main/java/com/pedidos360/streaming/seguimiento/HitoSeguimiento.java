package com.pedidos360.streaming.seguimiento;

import jakarta.persistence.*;

import java.time.Instant;

/** Cada evento recibido de un pedido (línea de tiempo), con el shard y la posición de donde se leyó. */
@Entity
@Table(name = "hitos_seguimiento")
public class HitoSeguimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pedidoId;

    @Column(nullable = false, length = 40)
    private String tipoEvento;

    @Column(length = 40)
    private String estado;

    @Column(nullable = false, length = 80)
    private String eventId;

    private Instant ocurridoEn;

    private Instant procesadoEn;

    private long latenciaMs;

    @Column(length = 80)
    private String shardId;

    @Column(length = 80)
    private String sequenceNumber;

    @Column(length = 120)
    private String instancia;

    protected HitoSeguimiento() {}

    public HitoSeguimiento(Long pedidoId, String tipoEvento, String estado, String eventId, Instant ocurridoEn,
                           long latenciaMs, String shardId, String sequenceNumber, String instancia) {
        this.pedidoId = pedidoId;
        this.tipoEvento = tipoEvento;
        this.estado = estado;
        this.eventId = eventId;
        this.ocurridoEn = ocurridoEn;
        this.procesadoEn = Instant.now();
        this.latenciaMs = latenciaMs;
        this.shardId = shardId;
        this.sequenceNumber = sequenceNumber;
        this.instancia = instancia;
    }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public String getTipoEvento() { return tipoEvento; }
    public String getEstado() { return estado; }
    public String getEventId() { return eventId; }
    public Instant getOcurridoEn() { return ocurridoEn; }
    public Instant getProcesadoEn() { return procesadoEn; }
    public long getLatenciaMs() { return latenciaMs; }
    public String getShardId() { return shardId; }
    public String getSequenceNumber() { return sequenceNumber; }
    public String getInstancia() { return instancia; }
}
