package com.pedidos360.streaming.seguimiento;

import jakarta.persistence.*;

import java.time.Instant;

/** Proyección en tiempo real del estado de cada pedido, construida solo a partir de los eventos del stream. */
@Entity
@Table(name = "seguimiento_pedidos")
public class SeguimientoPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long pedidoId;

    private String usuarioEmail;

    @Column(nullable = false, length = 40)
    private String estadoActual;

    @Column(length = 20)
    private String tipoEntrega;

    private Double total;

    private int eventos;

    @Column(length = 80)
    private String ultimoEventId;

    private Instant ultimoOcurridoEn;

    private Instant actualizadoEn;

    /** Milisegundos entre que ocurrió el hecho (pedidos-service) y que esta aplicación lo procesó. */
    private long latenciaMs;

    @Column(length = 80)
    private String shardId;

    protected SeguimientoPedido() {}

    public SeguimientoPedido(Long pedidoId) {
        this.pedidoId = pedidoId;
        this.estadoActual = "PENDIENTE";
    }

    public void aplicar(String estado, String usuarioEmail, String tipoEntrega, Double total, String eventId,
                        Instant ocurridoEn, long latenciaMs, String shardId) {
        this.estadoActual = estado;
        if (usuarioEmail != null) this.usuarioEmail = usuarioEmail;
        if (tipoEntrega != null) this.tipoEntrega = tipoEntrega;
        if (total != null) this.total = total;
        this.ultimoEventId = eventId;
        this.ultimoOcurridoEn = ocurridoEn;
        this.latenciaMs = latenciaMs;
        this.shardId = shardId;
        this.actualizadoEn = Instant.now();
    }

    public void contarEvento() {
        this.eventos++;
    }

    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public String getUsuarioEmail() { return usuarioEmail; }
    public String getEstadoActual() { return estadoActual; }
    public String getTipoEntrega() { return tipoEntrega; }
    public Double getTotal() { return total; }
    public int getEventos() { return eventos; }
    public String getUltimoEventId() { return ultimoEventId; }
    public Instant getUltimoOcurridoEn() { return ultimoOcurridoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public long getLatenciaMs() { return latenciaMs; }
    public String getShardId() { return shardId; }
}
