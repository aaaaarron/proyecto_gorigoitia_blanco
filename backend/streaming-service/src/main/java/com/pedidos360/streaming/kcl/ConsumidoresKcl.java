package com.pedidos360.streaming.kcl;

import com.pedidos360.streaming.comun.EstadoShards;
import com.pedidos360.streaming.comun.EventoStream;
import com.pedidos360.streaming.comun.ManejadorEventos;
import com.pedidos360.streaming.comun.ProcesadorRegistros;
import com.pedidos360.streaming.config.KinesisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.InitialPositionInStream;
import software.amazon.kinesis.common.InitialPositionInStreamExtended;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Arranca y detiene las aplicaciones consumidoras (un Scheduler de la KCL por aplicación).
 *
 * <ul>
 *   <li>El nombre de la aplicación (kinesis.aplicaciones.*.nombre) es el nombre de su tabla de coordinación
 *       en DynamoDB, que la KCL crea si no existe. Cada aplicación lleva su propio avance (checkpoint por shard)
 *       aunque lea el mismo stream que otra.</li>
 *   <li>Cada instancia de este servicio se registra como un "worker" distinto. Los workers de una misma aplicación
 *       se reparten los leases (shards); si uno se detiene, sus leases vencen y los toman los demás.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "kinesis.consumidores-habilitados", havingValue = "true", matchIfMissing = true)
public class ConsumidoresKcl implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsumidoresKcl.class);

    private final KinesisAsyncClient kinesis;
    private final DynamoDbAsyncClient dynamo;
    private final CloudWatchAsyncClient cloudWatch;
    private final KinesisProperties props;
    private final ProcesadorRegistros procesador;
    private final EstadoShards estado;
    private final Map<String, ManejadorEventos<?>> manejadores = new HashMap<>();
    private final List<Scheduler> schedulers = new ArrayList<>();
    private volatile boolean corriendo;

    public ConsumidoresKcl(KinesisAsyncClient kinesis, DynamoDbAsyncClient dynamo, CloudWatchAsyncClient cloudWatch,
                           KinesisProperties props, ProcesadorRegistros procesador, EstadoShards estado,
                           List<ManejadorEventos<?>> listaManejadores) {
        this.kinesis = kinesis;
        this.dynamo = dynamo;
        this.cloudWatch = cloudWatch;
        this.props = props;
        this.procesador = procesador;
        this.estado = estado;
        for (ManejadorEventos<?> m : listaManejadores) {
            manejadores.put(m.aplicacion(), m);
        }
    }

    @Override
    public synchronized void start() {
        for (Map.Entry<String, KinesisProperties.Aplicacion> entrada : props.aplicaciones().entrySet()) {
            KinesisProperties.Aplicacion app = entrada.getValue();
            if (!app.habilitada()) {
                log.info("[kcl] Aplicación {} deshabilitada en esta instancia", app.nombre());
                continue;
            }
            ManejadorEventos<?> manejador = manejadores.get(entrada.getKey());
            if (manejador == null) {
                log.error("[kcl] No hay manejador para la aplicación '{}'", entrada.getKey());
                continue;
            }
            Scheduler scheduler = crearScheduler(app, manejador);
            Thread hilo = new Thread(scheduler, "kcl-" + app.nombre());
            hilo.setDaemon(true);
            hilo.start();
            schedulers.add(scheduler);
            log.info("[kcl] Aplicación {} iniciada sobre {} (instancia {}, posición inicial {})",
                    app.nombre(), app.stream(), props.instanciaId(), props.posicionInicial());
        }
        corriendo = true;
    }

    private <T extends EventoStream> Scheduler crearScheduler(KinesisProperties.Aplicacion app,
                                                              ManejadorEventos<T> manejador) {
        String worker = props.instanciaId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        ConfigsBuilder configs = new ConfigsBuilder(app.stream(), app.nombre(), kinesis, dynamo, cloudWatch, worker,
                new FabricaProcesadorShard<>(app, manejador, procesador, estado, props.instanciaId()));

        PollingConfig polling = new PollingConfig(app.stream(), kinesis);
        polling.maxRecords(props.lectura().maxRegistros());
        polling.idleTimeBetweenReadsInMillis(props.lectura().intervaloMs());

        InitialPositionInStreamExtended posicion = InitialPositionInStreamExtended.newInitialPosition(
                InitialPositionInStream.valueOf(props.posicionInicial()));

        return new Scheduler(
                configs.checkpointConfig(),
                configs.coordinatorConfig(),
                configs.leaseManagementConfig(),
                configs.lifecycleConfig(),
                configs.metricsConfig(),
                configs.processorConfig(),
                configs.retrievalConfig()
                        .initialPositionInStreamExtended(posicion)
                        .retrievalSpecificConfig(polling));
    }

    @Override
    public synchronized void stop() {
        for (Scheduler s : schedulers) {
            try {
                // Apagado ordenado: cada procesador hace checkpoint y libera su lease para que otra instancia
                // lo tome de inmediato (sin esperar a que venza).
                Future<Boolean> fin = s.startGracefulShutdown();
                fin.get(20, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[kcl] Apagado ordenado incompleto ({}); se fuerza", e.getMessage());
                s.shutdown();
            }
        }
        schedulers.clear();
        corriendo = false;
    }

    @Override
    public boolean isRunning() {
        return corriendo;
    }

    public List<String> aplicacionesActivas() {
        List<String> activas = new ArrayList<>();
        props.aplicaciones().values().stream()
                .filter(KinesisProperties.Aplicacion::habilitada)
                .forEach(a -> activas.add(a.nombre()));
        return activas;
    }
}
