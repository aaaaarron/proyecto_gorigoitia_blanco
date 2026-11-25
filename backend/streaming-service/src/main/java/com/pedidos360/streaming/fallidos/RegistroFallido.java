package com.pedidos360.streaming.fallidos;

import jakarta.persistence.*;

import java.time.Instant;

/** Evento leído del stream de fallidos, guardado para diagnóstico (lo muestra el panel Streaming). */
@Entity
@Table(name = "registros_fallidos")
public class RegistroFallido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String eventId;

    @Column(length = 80)
    private String eventIdOriginal;

    @Column(length = 80)
    private String aplicacion;

    @Column(length = 120)
    private String streamOrigen;

    @Column(length = 80)
    private String shardId;

    @Column(length = 80)
    private String sequenceNumber;

    private int intentos;

    @Column(length = 40)
    private String tipoError;

    @Column(length = 1000)
    private String causa;

    @Column(length = 4000)
    private String payloadOriginal;

    @Column(length = 40)
    private String fallidoEn;

    private Instant detectadoEn;

    protected RegistroFallido() {}

    public RegistroFallido(String eventId, String eventIdOriginal, String aplicacion, String streamOrigen, String shardId,
                           String sequenceNumber, int intentos, String tipoError, String causa,
                           String payloadOriginal, String fallidoEn) {
        this.eventId = eventId;
        this.eventIdOriginal = eventIdOriginal;
        this.aplicacion = aplicacion;
        this.streamOrigen = streamOrigen;
        this.shardId = shardId;
        this.sequenceNumber = sequenceNumber;
        this.intentos = intentos;
        this.tipoError = tipoError;
        this.causa = recortar(causa, 1000);
        this.payloadOriginal = recortar(payloadOriginal, 4000);
        this.fallidoEn = fallidoEn;
        this.detectadoEn = Instant.now();
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventIdOriginal() { return eventIdOriginal; }
    public String getAplicacion() { return aplicacion; }
    public String getStreamOrigen() { return streamOrigen; }
    public String getShardId() { return shardId; }
    public String getSequenceNumber() { return sequenceNumber; }
    public int getIntentos() { return intentos; }
    public String getTipoError() { return tipoError; }
    public String getCausa() { return causa; }
    public String getPayloadOriginal() { return payloadOriginal; }
    public String getFallidoEn() { return fallidoEn; }
    public Instant getDetectadoEn() { return detectadoEn; }
}
