package com.pedidos360.streaming.kcl;

import com.pedidos360.streaming.comun.ContextoRegistro;
import com.pedidos360.streaming.comun.EstadoShards;
import com.pedidos360.streaming.comun.EventoStream;
import com.pedidos360.streaming.comun.ManejadorEventos;
import com.pedidos360.streaming.comun.ProcesadorRegistros;
import com.pedidos360.streaming.comun.ResultadoProcesamiento;
import com.pedidos360.streaming.config.KinesisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.exceptions.ThrottlingException;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.LeaseLostInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;
import software.amazon.kinesis.processor.RecordProcessorCheckpointer;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.retrieval.KinesisClientRecord;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Procesador de UN shard dentro de una aplicación consumidora (la KCL crea uno por cada shard cuyo lease
 * tiene esta instancia). Es solo infraestructura: convierte cada registro en texto y delega en
 * {@link ProcesadorRegistros}, que a su vez llama al dominio.
 *
 * <p>Checkpoint: se registra DESPUÉS de procesar el lote completo. Si la instancia cae antes, otra instancia
 * toma el lease y continúa desde el último checkpoint (los eventos repetidos se descartan por eventId).</p>
 */
public class ProcesadorShard<T extends EventoStream> implements ShardRecordProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorShard.class);
    private static final int INTENTOS_CHECKPOINT = 3;

    private final KinesisProperties.Aplicacion aplicacion;
    private final ManejadorEventos<T> manejador;
    private final ProcesadorRegistros procesador;
    private final EstadoShards estado;
    private final String instancia;
    private String shardId = "desconocido";

    public ProcesadorShard(KinesisProperties.Aplicacion aplicacion, ManejadorEventos<T> manejador,
                           ProcesadorRegistros procesador, EstadoShards estado, String instancia) {
        this.aplicacion = aplicacion;
        this.manejador = manejador;
        this.procesador = procesador;
        this.estado = estado;
        this.instancia = instancia;
    }

    @Override
    public void initialize(InitializationInput input) {
        this.shardId = input.shardId();
        estado.asignado(aplicacion.nombre(), aplicacion.stream(), shardId, instancia);
        log.info("[kcl] {} TOMA el shard {} en la instancia {} (desde checkpoint {})",
                aplicacion.nombre(), shardId, instancia, input.extendedSequenceNumber());
    }

    @Override
    public void processRecords(ProcessRecordsInput input) {
        int ok = 0;
        int duplicados = 0;
        int fallidos = 0;
        String ultimoSeq = null;
        try {
            for (KinesisClientRecord registro : input.records()) {
                ContextoRegistro ctx = new ContextoRegistro(aplicacion.nombre(), aplicacion.stream(), shardId,
                        registro.sequenceNumber(), registro.partitionKey(), instancia, aplicacion.enviarAFallidos());
                ResultadoProcesamiento r = procesador.procesar(ctx, texto(registro.data()), manejador);
                switch (r) {
                    case OK -> ok++;
                    case DUPLICADO -> duplicados++;
                    case FALLIDO -> fallidos++;
                }
                ultimoSeq = registro.sequenceNumber();
            }
        } catch (RuntimeException e) {
            // Red de seguridad: ProcesadorRegistros no lanza excepciones, pero si algo inesperado ocurre
            // se registra y el consumo del shard continúa (nunca se propaga a la KCL).
            log.error("[kcl] {} shard={} error inesperado en el lote: {}", aplicacion.nombre(), shardId, e.getMessage(), e);
        }
        estado.registrarLote(aplicacion.nombre(), shardId, ok, duplicados, fallidos, ultimoSeq, input.millisBehindLatest());
        if (!input.records().isEmpty()) {
            log.info("[kcl] {} shard={} lote={} ok={} duplicados={} fallidos={} retraso={} ms -> checkpoint en {}",
                    aplicacion.nombre(), shardId, input.records().size(), ok, duplicados, fallidos,
                    input.millisBehindLatest(), ultimoSeq);
            checkpoint(input.checkpointer(), "fin de lote");
        }
    }

    @Override
    public void leaseLost(LeaseLostInput input) {
        estado.liberado(aplicacion.nombre(), shardId);
        log.warn("[kcl] {} PIERDE el lease del shard {} (otra instancia lo tomó); no se hace checkpoint",
                aplicacion.nombre(), shardId);
    }

    @Override
    public void shardEnded(ShardEndedInput input) {
        // Ocurre al cambiar la cantidad de shards (resharding): el shard padre se cierra y hay que hacer
        // checkpoint para que la KCL empiece a leer los shards hijos.
        log.info("[kcl] {} shard {} CERRADO (resharding): checkpoint final", aplicacion.nombre(), shardId);
        checkpoint(input.checkpointer(), "fin de shard");
        estado.liberado(aplicacion.nombre(), shardId);
    }

    @Override
    public void shutdownRequested(ShutdownRequestedInput input) {
        log.info("[kcl] {} shard {}: apagado ordenado de la instancia, checkpoint", aplicacion.nombre(), shardId);
        checkpoint(input.checkpointer(), "apagado");
        estado.liberado(aplicacion.nombre(), shardId);
    }

    private void checkpoint(RecordProcessorCheckpointer checkpointer, String motivo) {
        for (int intento = 1; intento <= INTENTOS_CHECKPOINT; intento++) {
            try {
                checkpointer.checkpoint();
                return;
            } catch (ThrottlingException e) {
                log.warn("[kcl] {} shard={} checkpoint ({}) limitado por DynamoDB, intento {}/{}",
                        aplicacion.nombre(), shardId, motivo, intento, INTENTOS_CHECKPOINT);
                dormir(200L * intento);
            } catch (ShutdownException e) {
                log.info("[kcl] {} shard={} sin lease: el checkpoint lo hará la instancia que lo tomó",
                        aplicacion.nombre(), shardId);
                return;
            } catch (Exception e) {
                log.error("[kcl] {} shard={} no se pudo hacer checkpoint ({}): {}",
                        aplicacion.nombre(), shardId, motivo, e.getMessage());
                return;
            }
        }
    }

    private static String texto(ByteBuffer datos) {
        ByteBuffer copia = datos.duplicate();
        byte[] bytes = new byte[copia.remaining()];
        copia.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
