package com.pedidos360.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.admin.config.DeclaracionStreams;
import com.pedidos360.admin.config.StreamingProperties;
import com.pedidos360.admin.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.ComparisonOperator;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.EnhancedMetrics;
import software.amazon.awssdk.services.kinesis.model.ListStreamsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;
import software.amazon.awssdk.services.kinesis.model.ResourceNotFoundException;
import software.amazon.awssdk.services.kinesis.model.ScalingType;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.StreamDescriptionSummary;
import software.amazon.awssdk.services.kinesis.model.StreamMode;
import software.amazon.awssdk.services.kinesis.model.UpdateShardCountResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Parcial 3: ÚNICA clase de la aplicación que usa el SDK para administrar la plataforma de streaming.
 * Los controladores (y por ellos el frontend) solo llaman a estos métodos de alto nivel.
 *
 * <ul>
 *   <li>Streams: crear, listar, describir (con sus shards), cambiar shards, cambiar retención y eliminar.</li>
 *   <li>Aplicar la declaración de application.yml (idempotente): crea lo que falta y ajusta lo que difiere.</li>
 *   <li>Aplicaciones consumidoras: lee su tabla de coordinación en DynamoDB (shard → instancia dueña →
 *       checkpoint) y la cruza con el retraso que informa cada instancia y con CloudWatch.</li>
 *   <li>Alarma de CloudWatch sobre el retraso de consumo.</li>
 * </ul>
 */
@Service
public class StreamAdminService {

    private static final Logger log = LoggerFactory.getLogger(StreamAdminService.class);
    private static final String METRICA_RETRASO = "GetRecords.IteratorAgeMilliseconds";

    private final KinesisClient kinesis;
    private final DynamoDbClient dynamo;
    private final CloudWatchClient cloudWatch;
    private final StreamingProperties props;
    private final StreamingConsultaService instancias;
    private final ObjectMapper mapper;

    public StreamAdminService(KinesisClient kinesis, DynamoDbClient dynamo, CloudWatchClient cloudWatch,
                              StreamingProperties props, StreamingConsultaService instancias, ObjectMapper mapper) {
        this.kinesis = kinesis;
        this.dynamo = dynamo;
        this.cloudWatch = cloudWatch;
        this.props = props;
        this.instancias = instancias;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------------------------ streams

    public StreamingProperties declaracion() {
        return props;
    }

    public List<EstadoStream> listar() {
        List<String> nombres = new ArrayList<>();
        String desde = null;
        boolean hayMas = true;
        while (hayMas) {
            final String inicio = desde;
            ListStreamsResponse r = kinesis.listStreams(b -> {
                b.limit(100);
                if (inicio != null) b.exclusiveStartStreamName(inicio);
            });
            nombres.addAll(r.streamNames());
            hayMas = Boolean.TRUE.equals(r.hasMoreStreams()) && !r.streamNames().isEmpty();
            if (hayMas) desde = r.streamNames().get(r.streamNames().size() - 1);
        }
        List<EstadoStream> lista = new ArrayList<>();
        for (String n : nombres) {
            try {
                lista.add(resumen(n, false));
            } catch (ResourceNotFoundException e) {
                // se eliminó entre el listado y la descripción
            }
        }
        lista.sort(Comparator.comparing(EstadoStream::nombre));
        return lista;
    }

    public EstadoStream describir(String nombre) {
        return resumen(nombre, true);
    }

    public EstadoStream crear(CrearStreamRequest req) {
        String modo = req.modo() == null ? "PROVISIONED" : req.modo();
        if ("ON_DEMAND".equals(modo)) {
            kinesis.createStream(b -> b.streamName(req.nombre()).streamModeDetails(d -> d.streamMode(StreamMode.ON_DEMAND)));
        } else {
            kinesis.createStream(b -> b.streamName(req.nombre()).shardCount(req.shards())
                    .streamModeDetails(d -> d.streamMode(StreamMode.PROVISIONED)));
        }
        log.info("[streams] Creando {} ({} shards, {}), esperando a que quede ACTIVE...", req.nombre(), req.shards(), modo);
        esperarActivo(req.nombre());
        ajustarRetencion(req.nombre(), req.retencionHoras());
        habilitarMonitoreoPorShard(req.nombre());
        return describir(req.nombre());
    }

    /** Resharding uniforme. Mientras dura el stream queda UPDATING y sigue aceptando lecturas y escrituras. */
    public EstadoStream cambiarShards(String nombre, int shards) {
        UpdateShardCountResponse r = kinesis.updateShardCount(b -> b.streamName(nombre).targetShardCount(shards)
                .scalingType(ScalingType.UNIFORM_SCALING));
        log.info("[streams] {}: shards {} -> {} (UNIFORM_SCALING)", nombre, r.currentShardCount(), r.targetShardCount());
        return describir(nombre);
    }

    public EstadoStream cambiarRetencion(String nombre, int horas) {
        ajustarRetencion(nombre, horas);
        return describir(nombre);
    }

    public void eliminar(String nombre) {
        kinesis.deleteStream(b -> b.streamName(nombre).enforceConsumerDeletion(true));
        log.warn("[streams] Stream {} eliminado", nombre);
    }

    /** Aplica la declaración de application.yml en AWS. Idempotente: se puede ejecutar todas las veces que se quiera. */
    public List<String> aplicarDeclarados() {
        DeclaracionStreams.validar(props);
        List<String> pasos = new ArrayList<>();
        for (StreamingProperties.StreamDeclarado s : props.streams()) {
            StreamDescriptionSummary actual = resumenAws(s.nombre());
            if (actual == null) {
                crear(new CrearStreamRequest(s.nombre(), s.shards(), s.retencionHoras(), s.modo()));
                pasos.add("Creado " + s.nombre() + " (" + s.modo() + ", " + s.shards() + " shards, " + s.retencionHoras() + " h)");
                continue;
            }
            esperarActivo(s.nombre());
            if (!Objects.equals(actual.retentionPeriodHours(), s.retencionHoras())) {
                ajustarRetencion(s.nombre(), s.retencionHoras());
                pasos.add(s.nombre() + ": retención " + actual.retentionPeriodHours() + " -> " + s.retencionHoras() + " h");
            }
            if ("PROVISIONED".equals(s.modo()) && !Objects.equals(actual.openShardCount(), s.shards())) {
                cambiarShards(s.nombre(), s.shards());
                pasos.add(s.nombre() + ": shards " + actual.openShardCount() + " -> " + s.shards());
            }
            habilitarMonitoreoPorShard(s.nombre());
            pasos.add(s.nombre() + " al día");
        }
        if (props.alarma() != null) {
            crearAlarma();
            pasos.add("Alarma " + props.alarma().nombre() + " sobre " + METRICA_RETRASO + " de " + props.alarma().stream());
        }
        return pasos;
    }

    // --------------------------------------------------------------------- aplicaciones consumidoras

    public List<EstadoAplicacion> aplicaciones() {
        List<EstadoInstanciaStreaming> estados = instancias.estados();
        List<EstadoAplicacion> lista = new ArrayList<>();
        for (StreamingProperties.AplicacionDeclarada a : props.aplicaciones()) {
            lista.add(aplicacion(a, estados));
        }
        return lista;
    }

    public EstadoAplicacion aplicacion(String nombre) {
        StreamingProperties.AplicacionDeclarada a = props.aplicaciones().stream()
                .filter(x -> x.nombre().equals(nombre)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Aplicación consumidora no declarada: " + nombre));
        return aplicacion(a, instancias.estados());
    }

    private EstadoAplicacion aplicacion(StreamingProperties.AplicacionDeclarada a, List<EstadoInstanciaStreaming> estados) {
        List<Map<String, AttributeValue>> items;
        try {
            items = dynamo.scan(b -> b.tableName(a.nombre())).items();
        } catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException e) {
            return new EstadoAplicacion(a.nombre(), a.stream(), a.proposito(), a.nombre(), false, List.of(), List.of(), null,
                    "La tabla de coordinación aún no existe: la crea la KCL cuando la aplicación arranca por primera vez");
        }

        // Shards que cada instancia informa como propios (con su retraso y contadores)
        Map<String, EstadoInstanciaStreaming.ShardLocal> locales = new HashMap<>();
        for (EstadoInstanciaStreaming e : estados) {
            if (e.shards() == null) continue;
            for (EstadoInstanciaStreaming.ShardLocal s : e.shards()) {
                if (a.nombre().equals(s.aplicacion())) locales.put(s.shardId(), s);
            }
        }

        List<LeaseInfo> leases = new ArrayList<>();
        Set<String> duenos = new TreeSet<>();
        Long retrasoMax = null;
        for (Map<String, AttributeValue> item : items) {
            String shardId = texto(item, "leaseKey");
            if (shardId == null || !shardId.startsWith("shardId-")) continue; // KCL 3 guarda también otras filas de control
            String dueno = texto(item, "leaseOwner");
            if (dueno != null) duenos.add(dueno);
            EstadoInstanciaStreaming.ShardLocal local = locales.get(shardId);
            Long retraso = local != null ? local.millisBehindLatest() : null;
            if (retraso != null) retrasoMax = retrasoMax == null ? retraso : Math.max(retrasoMax, retraso);
            leases.add(new LeaseInfo(shardId, dueno, texto(item, "checkpoint"), numero(item, "leaseCounter"),
                    numero(item, "ownerSwitchesSinceCheckpoint"), retraso,
                    local != null ? local.procesados() : null, local != null ? local.fallidos() : null,
                    local != null ? local.ultimaLectura() : null));
        }
        leases.sort(Comparator.comparing(LeaseInfo::shardId));
        String mensaje = leases.size() + " shard(s) repartidos entre " + duenos.size() + " instancia(s)";
        return new EstadoAplicacion(a.nombre(), a.stream(), a.proposito(), a.nombre(), true,
                new ArrayList<>(duenos), leases, retrasoMax, mensaje);
    }

    // ------------------------------------------------------------------------------- CloudWatch

    /** Retraso de consumo del stream (máximo por minuto, últimos 15 minutos) según CloudWatch. */
    public List<Map<String, Object>> retraso(String stream) {
        Instant fin = Instant.now();
        List<Datapoint> puntos = cloudWatch.getMetricStatistics(b -> b
                .namespace("AWS/Kinesis")
                .metricName(METRICA_RETRASO)
                .dimensions(Dimension.builder().name("StreamName").value(stream).build())
                .startTime(fin.minus(Duration.ofMinutes(15)))
                .endTime(fin)
                .period(60)
                .statistics(Statistic.MAXIMUM)).datapoints();
        List<Map<String, Object>> serie = new ArrayList<>();
        puntos.stream().sorted(Comparator.comparing(Datapoint::timestamp)).forEach(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("minuto", p.timestamp().toString());
            m.put("retrasoMaxMs", p.maximum());
            serie.add(m);
        });
        return serie;
    }

    public void crearAlarma() {
        StreamingProperties.Alarma a = props.alarma();
        cloudWatch.putMetricAlarm(b -> {
            b.alarmName(a.nombre())
                    .alarmDescription("Retraso de consumo de " + a.stream() + " mayor a " + a.umbralMs()
                            + " ms: una aplicación consumidora está detenida o no da abasto")
                    .namespace("AWS/Kinesis")
                    .metricName(METRICA_RETRASO)
                    .dimensions(Dimension.builder().name("StreamName").value(a.stream()).build())
                    .statistic(Statistic.MAXIMUM)
                    .period(60)
                    .evaluationPeriods(a.periodos())
                    .threshold((double) a.umbralMs())
                    .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                    .treatMissingData("notBreaching");
            if (a.snsTopicArn() != null && !a.snsTopicArn().isBlank()) {
                b.alarmActions(a.snsTopicArn());
            }
        });
        log.info("[streams] Alarma {} creada/actualizada", a.nombre());
    }

    public EstadoAlarma alarma() {
        StreamingProperties.Alarma a = props.alarma();
        List<MetricAlarm> alarmas = cloudWatch.describeAlarms(b -> b.alarmNames(a.nombre())).metricAlarms();
        if (alarmas.isEmpty()) {
            return new EstadoAlarma(a.nombre(), a.stream(), "NO_EXISTE", (double) a.umbralMs(),
                    "Créala con POST /api/admin/streams/aplicar o infra/6-kinesis-streams.ps1", null);
        }
        MetricAlarm m = alarmas.get(0);
        return new EstadoAlarma(m.alarmName(), a.stream(), m.stateValueAsString(), m.threshold(), m.stateReason(),
                m.stateUpdatedTimestamp() != null ? m.stateUpdatedTimestamp().toString() : null);
    }

    // ------------------------------------------------------------------------- publicación de prueba

    /** Publica un registro con contenido libre (p. ej. un evento defectuoso para la demo del stream de fallidos). */
    public ResultadoPublicacion publicarPrueba(String stream, String clave, String contenido) {
        PutRecordResponse r = kinesis.putRecord(b -> b.streamName(stream).partitionKey(clave)
                .data(SdkBytes.fromUtf8String(contenido)));
        return new ResultadoPublicacion(stream, 1, 0, Map.of(r.shardId(), 1),
                List.of("clave=" + clave + " -> " + r.shardId() + " seq=" + r.sequenceNumber()));
    }

    /**
     * Publica una ráfaga de eventos PEDIDO_CREADO sintéticos con PutRecords (lote) para mostrar cómo la clave
     * de partición (pedidoId) reparte la carga entre los shards. Verifica cada registro de la respuesta.
     */
    public ResultadoPublicacion publicarRafaga(String stream, int cantidad) {
        int n = Math.max(1, Math.min(cantidad, 500));
        long base = 900000L + (System.currentTimeMillis() % 90000L) * 10;
        Random azar = new Random();
        List<PutRecordsRequestEntry> entradas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long pedidoId = base + i;
            Map<String, Object> evento = new LinkedHashMap<>();
            evento.put("eventId", UUID.randomUUID().toString());
            evento.put("tipoEvento", "PEDIDO_CREADO");
            evento.put("version", 1);
            evento.put("ocurridoEn", Instant.now().toString());
            evento.put("origen", "mensajeria-admin (rafaga de prueba)");
            evento.put("pedidoId", pedidoId);
            evento.put("usuarioEmail", "demo@pedidos360.cl");
            evento.put("estado", "PENDIENTE");
            evento.put("tipoEntrega", i % 2 == 0 ? "ENVIO" : "RETIRO");
            evento.put("total", (double) (1000 + azar.nextInt(30000)));
            evento.put("cantidadItems", 1 + azar.nextInt(4));
            entradas.add(PutRecordsRequestEntry.builder()
                    .partitionKey(String.valueOf(pedidoId))
                    .data(SdkBytes.fromUtf8String(json(evento)))
                    .build());
        }
        PutRecordsResponse r = kinesis.putRecords(b -> b.streamName(stream).records(entradas));
        Map<String, Integer> porShard = new TreeMap<>();
        List<String> detalle = new ArrayList<>();
        int fallidos = 0;
        for (PutRecordsResultEntry e : r.records()) {
            if (e.errorCode() != null) {
                fallidos++;
                detalle.add("rechazado: " + e.errorCode() + " " + e.errorMessage());
            } else {
                porShard.merge(e.shardId(), 1, Integer::sum);
            }
        }
        return new ResultadoPublicacion(stream, n - fallidos, fallidos, porShard, detalle);
    }

    // ---------------------------------------------------------------------------------- auxiliares

    private EstadoStream resumen(String nombre, boolean conShards) {
        StreamDescriptionSummary s = kinesis.describeStreamSummary(b -> b.streamName(nombre)).streamDescriptionSummary();
        StreamingProperties.StreamDeclarado d = props.stream(nombre);
        List<String> metricas = new ArrayList<>();
        if (s.enhancedMonitoring() != null) {
            for (EnhancedMetrics em : s.enhancedMonitoring()) {
                metricas.addAll(em.shardLevelMetricsAsStrings());
            }
        }
        List<ShardInfo> shards = conShards ? shards(nombre) : List.of();
        String modo = s.streamModeDetails() != null ? s.streamModeDetails().streamModeAsString() : "PROVISIONED";
        return new EstadoStream(s.streamName(), s.streamStatusAsString(), modo, s.openShardCount(),
                s.retentionPeriodHours(), s.streamARN(),
                s.streamCreationTimestamp() != null ? s.streamCreationTimestamp().toString() : null,
                metricas, d != null, d != null ? d.rol() : null, d != null ? d.justificacion() : null, shards);
    }

    private List<ShardInfo> shards(String nombre) {
        List<ShardInfo> lista = new ArrayList<>();
        for (Shard sh : kinesis.listShards(b -> b.streamName(nombre)).shards()) {
            boolean abierto = sh.sequenceNumberRange() == null || sh.sequenceNumberRange().endingSequenceNumber() == null;
            lista.add(new ShardInfo(sh.shardId(), abierto,
                    sh.hashKeyRange() != null ? sh.hashKeyRange().startingHashKey() : null,
                    sh.hashKeyRange() != null ? sh.hashKeyRange().endingHashKey() : null,
                    sh.sequenceNumberRange() != null ? sh.sequenceNumberRange().startingSequenceNumber() : null,
                    sh.parentShardId()));
        }
        return lista;
    }

    private StreamDescriptionSummary resumenAws(String nombre) {
        try {
            return kinesis.describeStreamSummary(b -> b.streamName(nombre)).streamDescriptionSummary();
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    private void ajustarRetencion(String nombre, int horas) {
        StreamDescriptionSummary s = kinesis.describeStreamSummary(b -> b.streamName(nombre)).streamDescriptionSummary();
        int actual = s.retentionPeriodHours();
        if (horas > actual) {
            kinesis.increaseStreamRetentionPeriod(b -> b.streamName(nombre).retentionPeriodHours(horas));
        } else if (horas < actual) {
            kinesis.decreaseStreamRetentionPeriod(b -> b.streamName(nombre).retentionPeriodHours(horas));
        } else {
            return;
        }
        log.info("[streams] {}: retención {} h -> {} h", nombre, actual, horas);
        esperarActivo(nombre);
    }

    /** Métricas por shard en CloudWatch (IncomingRecords, OutgoingRecords, IteratorAge por shard). */
    private void habilitarMonitoreoPorShard(String nombre) {
        if (!props.monitoreoPorShard()) return;
        try {
            kinesis.enableEnhancedMonitoring(b -> b.streamName(nombre)
                    .shardLevelMetricsWithStrings("IncomingRecords", "OutgoingRecords", "IteratorAgeMilliseconds"));
            esperarActivo(nombre);
        } catch (RuntimeException e) {
            log.warn("[streams] No se pudo habilitar el monitoreo por shard de {}: {}", nombre, e.getMessage());
        }
    }

    /** Espera (máx. ~2 min) a que el stream quede ACTIVE: crear, cambiar retención o shards lo deja en UPDATING. */
    private void esperarActivo(String nombre) {
        for (int i = 0; i < 60; i++) {
            StreamDescriptionSummary s = kinesis.describeStreamSummary(b -> b.streamName(nombre)).streamDescriptionSummary();
            if ("ACTIVE".equals(s.streamStatusAsString())) return;
            dormir(2000);
        }
        log.warn("[streams] {} no quedó ACTIVE a tiempo; se continúa", nombre);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento de prueba", e);
        }
    }

    private static String texto(Map<String, AttributeValue> item, String atributo) {
        AttributeValue v = item.get(atributo);
        return v == null ? null : v.s();
    }

    private static Long numero(Map<String, AttributeValue> item, String atributo) {
        AttributeValue v = item.get(atributo);
        if (v == null || v.n() == null) return null;
        try {
            return Long.parseLong(v.n());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
