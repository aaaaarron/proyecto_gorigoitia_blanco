package com.pedidos360.streaming.comun;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado en memoria de los shards que ESTA instancia procesa en cada aplicación: posición de lectura
 * (último sequenceNumber), retraso (millisBehindLatest que informa Kinesis en cada GetRecords) y contadores.
 * mensajeria-admin lo consulta en cada instancia y lo cruza con la tabla de coordinación de DynamoDB.
 */
@Component
public class EstadoShards {

    public record Vista(String aplicacion, String stream, String shardId, String instancia,
                        long procesados, long duplicados, long fallidos,
                        String ultimoSequenceNumber, Long millisBehindLatest,
                        String ultimaLectura, String asignadoEn) {}

    private static final class Estado {
        final String aplicacion;
        final String stream;
        final String shardId;
        final String instancia;
        final Instant asignadoEn = Instant.now();
        long procesados;
        long duplicados;
        long fallidos;
        String ultimoSequenceNumber;
        Long millisBehindLatest;
        Instant ultimaLectura;

        Estado(String aplicacion, String stream, String shardId, String instancia) {
            this.aplicacion = aplicacion;
            this.stream = stream;
            this.shardId = shardId;
            this.instancia = instancia;
        }
    }

    private final Map<String, Estado> estados = new LinkedHashMap<>();

    private static String clave(String aplicacion, String shardId) {
        return aplicacion + "|" + shardId;
    }

    public synchronized void asignado(String aplicacion, String stream, String shardId, String instancia) {
        estados.put(clave(aplicacion, shardId), new Estado(aplicacion, stream, shardId, instancia));
    }

    public synchronized void liberado(String aplicacion, String shardId) {
        estados.remove(clave(aplicacion, shardId));
    }

    public synchronized void registrarLote(String aplicacion, String shardId, int ok, int duplicados, int fallidos,
                                           String ultimoSequenceNumber, Long millisBehindLatest) {
        Estado e = estados.get(clave(aplicacion, shardId));
        if (e == null) {
            return;
        }
        e.procesados += ok;
        e.duplicados += duplicados;
        e.fallidos += fallidos;
        if (ultimoSequenceNumber != null) {
            e.ultimoSequenceNumber = ultimoSequenceNumber;
        }
        e.millisBehindLatest = millisBehindLatest;
        e.ultimaLectura = Instant.now();
    }

    public synchronized List<Vista> vistas() {
        List<Vista> lista = new ArrayList<>();
        for (Estado e : estados.values()) {
            lista.add(new Vista(e.aplicacion, e.stream, e.shardId, e.instancia, e.procesados, e.duplicados, e.fallidos,
                    e.ultimoSequenceNumber, e.millisBehindLatest,
                    e.ultimaLectura == null ? null : e.ultimaLectura.toString(), e.asignadoEn.toString()));
        }
        lista.sort(Comparator.comparing(Vista::aplicacion).thenComparing(Vista::shardId));
        return lista;
    }
}
