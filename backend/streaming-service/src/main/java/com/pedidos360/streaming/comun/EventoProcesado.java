package com.pedidos360.streaming.comun;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Registro de idempotencia compartido por todas las instancias (restricción única aplicación + eventId).
 * Kinesis no borra el registro al leerlo: tras un reinicio o un cambio de dueño del shard, la nueva instancia
 * retoma desde el último checkpoint y puede volver a leer eventos ya procesados.
 */
@Entity
@Table(name = "eventos_procesados",
        uniqueConstraints = @UniqueConstraint(columnNames = {"aplicacion", "eventId"}))
public class EventoProcesado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String aplicacion;

    @Column(nullable = false, length = 80)
    private String eventId;

    @Column(nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {}

    public EventoProcesado(String aplicacion, String eventId) {
        this.aplicacion = aplicacion;
        this.eventId = eventId;
        this.procesadoEn = Instant.now();
    }

    public Long getId() { return id; }
    public String getAplicacion() { return aplicacion; }
    public String getEventId() { return eventId; }
    public Instant getProcesadoEn() { return procesadoEn; }
}
