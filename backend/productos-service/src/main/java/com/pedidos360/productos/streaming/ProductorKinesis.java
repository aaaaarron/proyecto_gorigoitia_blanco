package com.pedidos360.productos.streaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Parcial 3: envía registros a un stream con PutRecords y VERIFICA el resultado.
 *
 * <p>PutRecords puede responder 200 con parte de los registros rechazados (FailedRecordCount &gt; 0),
 * típicamente por ProvisionedThroughputExceededException cuando un shard supera 1 MB/s o 1.000 registros/s.
 * Por eso se revisa cada entrada de la respuesta y SOLO los registros fallidos se reenvían, con espera
 * creciente (exponencial). Si se agotan los intentos, quedan registrados en el log con su eventId y causa.</p>
 */
public class ProductorKinesis {

    private static final Logger log = LoggerFactory.getLogger(ProductorKinesis.class);

    /** Registro a enviar: id del evento (para el log), clave de partición y JSON. */
    public record Registro(String eventId, String claveParticion, String json) {}

    /** Resultado del envío de un lote. */
    public record Resultado(int enviados, int fallidos, int intentos) {
        public boolean completo() { return fallidos == 0; }
    }

    private final KinesisClient kinesis;
    private final int maxIntentos;
    private final long esperaInicialMs;

    public ProductorKinesis(KinesisClient kinesis, int maxIntentos, long esperaInicialMs) {
        this.kinesis = kinesis;
        this.maxIntentos = Math.max(1, maxIntentos);
        this.esperaInicialMs = Math.max(0, esperaInicialMs);
    }

    public Resultado enviar(String stream, List<Registro> registros) {
        List<Registro> pendientes = new ArrayList<>(registros);
        int enviados = 0;
        long espera = esperaInicialMs;

        for (int intento = 1; intento <= maxIntentos; intento++) {
            List<Registro> rechazados = new ArrayList<>();
            try {
                List<PutRecordsRequestEntry> entradas = pendientes.stream()
                        .map(r -> PutRecordsRequestEntry.builder()
                                .partitionKey(r.claveParticion())
                                .data(SdkBytes.fromUtf8String(r.json()))
                                .build())
                        .toList();
                PutRecordsResponse respuesta = kinesis.putRecords(b -> b.streamName(stream).records(entradas));

                List<PutRecordsResultEntry> resultados = respuesta.records();
                for (int i = 0; i < resultados.size(); i++) {
                    PutRecordsResultEntry res = resultados.get(i);
                    Registro reg = pendientes.get(i);
                    if (res.errorCode() != null) {
                        rechazados.add(reg);
                        log.warn("[kinesis] Registro rechazado stream={} eventId={} clave={} intento={}/{} causa=\"{}: {}\"",
                                stream, reg.eventId(), reg.claveParticion(), intento, maxIntentos, res.errorCode(), res.errorMessage());
                    } else {
                        enviados++;
                        log.info("[kinesis] Publicado stream={} eventId={} clave={} shard={} seq={} intento={}",
                                stream, reg.eventId(), reg.claveParticion(), res.shardId(), res.sequenceNumber(), intento);
                    }
                }
            } catch (SdkException e) {
                // Falla de toda la llamada (red, credenciales vencidas, stream inexistente): se reintenta el lote
                rechazados = pendientes;
                log.warn("[kinesis] Falló PutRecords stream={} registros={} intento={}/{} causa=\"{}\"",
                        stream, pendientes.size(), intento, maxIntentos, e.getMessage());
            }

            if (rechazados.isEmpty()) {
                return new Resultado(enviados, 0, intento);
            }
            if (intento == maxIntentos) {
                for (Registro r : rechazados) {
                    log.error("[kinesis] NO PUBLICADO tras {} intentos stream={} eventId={} clave={}",
                            maxIntentos, stream, r.eventId(), r.claveParticion());
                }
                return new Resultado(enviados, rechazados.size(), intento);
            }
            pendientes = rechazados;
            dormir(espera);
            espera = espera * 2;
        }
        return new Resultado(enviados, pendientes.size(), maxIntentos);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
