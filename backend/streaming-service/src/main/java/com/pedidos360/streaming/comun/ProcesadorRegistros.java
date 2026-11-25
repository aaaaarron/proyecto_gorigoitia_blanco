package com.pedidos360.streaming.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.streaming.config.KinesisProperties;
import com.pedidos360.streaming.eventos.EventoFallido;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Flujo común de procesamiento de UN registro, igual para todas las aplicaciones consumidoras:
 * <ol>
 *   <li>Deserializa el JSON en el DTO de la aplicación y lo valida (Bean Validation).
 *       Inválido → stream de fallidos sin reintentar.</li>
 *   <li>Si el eventId ya fue procesado por esta aplicación → se ignora (duplicado).</li>
 *   <li>Llama al dominio. Éxito → marca el eventId como procesado.</li>
 *   <li>{@link EventoInvalidoException} del dominio → fallidos sin reintentar.</li>
 *   <li>{@link ErrorTransitorio} u otro error → reintento con espera creciente; al agotar los intentos → fallidos.</li>
 * </ol>
 * NUNCA lanza excepción hacia la KCL: un evento defectuoso no detiene el consumo del shard. Cada log lleva
 * aplicación, shard, sequenceNumber, eventId, intento y causa (también en el MDC).
 *
 * <p>No conoce clases de la KCL: recibe el JSON y un {@link ContextoRegistro}, por eso se prueba sin AWS.</p>
 */
@Component
public class ProcesadorRegistros {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorRegistros.class);
    private static final int MAX_PAYLOAD = 3500;

    private final ObjectMapper mapper;
    private final Validator validator;
    private final ControlDuplicados duplicados;
    private final PublicadorFallidos fallidos;
    private final KinesisProperties props;

    public ProcesadorRegistros(ObjectMapper mapper, Validator validator, ControlDuplicados duplicados,
                               PublicadorFallidos fallidos, KinesisProperties props) {
        this.mapper = mapper;
        this.validator = validator;
        this.duplicados = duplicados;
        this.fallidos = fallidos;
        this.props = props;
    }

    public <T extends EventoStream> ResultadoProcesamiento procesar(ContextoRegistro ctx, String json,
                                                                    ManejadorEventos<T> manejador) {
        MDC.put("aplicacion", ctx.aplicacion());
        MDC.put("shardId", ctx.shardId());
        MDC.put("seq", ctx.sequenceNumber());
        try {
            T evento;
            try {
                evento = leerYValidar(json, manejador.tipoEvento());
            } catch (EventoInvalidoException e) {
                log.error("[{}] EVENTO INVÁLIDO shard={} seq={} eventId={} intento=1 causa=\"{}\" -> stream de fallidos",
                        ctx.aplicacion(), ctx.shardId(), ctx.sequenceNumber(), "desconocido", e.getMessage());
                enviarAFallidos(ctx, null, json, 1, "VALIDACION", e.getMessage());
                return ResultadoProcesamiento.FALLIDO;
            }
            MDC.put("eventId", evento.eventId());
            return procesarConReintentos(ctx, json, evento, manejador);
        } finally {
            MDC.remove("aplicacion");
            MDC.remove("shardId");
            MDC.remove("seq");
            MDC.remove("eventId");
        }
    }

    private <T extends EventoStream> ResultadoProcesamiento procesarConReintentos(ContextoRegistro ctx, String json,
                                                                                  T evento, ManejadorEventos<T> manejador) {
        KinesisProperties.Reintentos r = props.reintentos();
        int max = Math.max(1, r.maxIntentos());
        long espera = Math.max(0, r.esperaInicialMs());
        String eventId = evento.eventId();

        for (int intento = 1; ; intento++) {
            try {
                if (duplicados.yaProcesado(ctx.aplicacion(), eventId)) {
                    log.warn("[{}] DUPLICADO shard={} seq={} eventId={}: ya fue procesado, se ignora",
                            ctx.aplicacion(), ctx.shardId(), ctx.sequenceNumber(), eventId);
                    return ResultadoProcesamiento.DUPLICADO;
                }
                manejador.manejar(evento, ctx);
                if (!duplicados.registrar(ctx.aplicacion(), eventId)) {
                    log.warn("[{}] eventId={} registrado en paralelo por otra instancia", ctx.aplicacion(), eventId);
                }
                log.info("[{}] OK {} shard={} seq={} clave={} eventId={} intento={}/{}",
                        ctx.aplicacion(), evento.tipoEvento(), ctx.shardId(), ctx.sequenceNumber(),
                        ctx.partitionKey(), eventId, intento, max);
                return ResultadoProcesamiento.OK;

            } catch (EventoInvalidoException e) {
                log.error("[{}] EVENTO INVÁLIDO shard={} seq={} eventId={} intento={}/{} causa=\"{}\" -> stream de fallidos",
                        ctx.aplicacion(), ctx.shardId(), ctx.sequenceNumber(), eventId, intento, max, e.getMessage());
                enviarAFallidos(ctx, eventId, json, intento, "VALIDACION", e.getMessage());
                return ResultadoProcesamiento.FALLIDO;

            } catch (RuntimeException e) {
                String tipo = e instanceof ErrorTransitorio ? "TRANSITORIO" : "INESPERADO";
                if (intento >= max) {
                    log.error("[{}] ERROR {} shard={} seq={} eventId={} intento={}/{} causa=\"{}\" -> agotó los intentos, stream de fallidos",
                            ctx.aplicacion(), tipo, ctx.shardId(), ctx.sequenceNumber(), eventId, intento, max, e.getMessage());
                    enviarAFallidos(ctx, eventId, json, intento, tipo + "_AGOTADO",
                            "Agotó " + max + " intentos: " + e.getMessage());
                    return ResultadoProcesamiento.FALLIDO;
                }
                log.warn("[{}] ERROR {} shard={} seq={} eventId={} intento={}/{} causa=\"{}\" -> reintento en {} ms",
                        ctx.aplicacion(), tipo, ctx.shardId(), ctx.sequenceNumber(), eventId, intento, max, e.getMessage(), espera);
                dormir(espera);
                espera = (long) (espera * Math.max(1.0, r.multiplicador()));
            }
        }
    }

    private <T> T leerYValidar(String json, Class<T> tipo) {
        T evento;
        try {
            evento = mapper.readValue(json, tipo);
        } catch (Exception e) {
            throw new EventoInvalidoException("JSON inválido para " + tipo.getSimpleName() + ": " + resumen(e.getMessage()));
        }
        if (evento == null) {
            throw new EventoInvalidoException("Registro vacío");
        }
        Set<ConstraintViolation<T>> violaciones = validator.validate(evento);
        if (!violaciones.isEmpty()) {
            String detalle = violaciones.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new EventoInvalidoException("Contenido inválido: " + detalle);
        }
        return evento;
    }

    private void enviarAFallidos(ContextoRegistro ctx, String eventIdOriginal, String json, int intentos,
                                 String tipoError, String causa) {
        if (!ctx.enviarAFallidos()) {
            // Es la propia aplicación de fallidos: no se reenvía al mismo stream (evita un ciclo). Queda en el log.
            log.error("[{}] DESCARTADO (no se reenvía a fallidos) eventId={} causa=\"{}\" payload={}",
                    ctx.aplicacion(), eventIdOriginal, causa, recortar(json));
            return;
        }
        EventoFallido fallido = new EventoFallido(
                UUID.randomUUID().toString(), EventoFallido.TIPO, Instant.now().toString(),
                ctx.aplicacion(), ctx.stream(), ctx.shardId(), ctx.sequenceNumber(), ctx.partitionKey(),
                eventIdOriginal, intentos, tipoError, causa, recortar(json));
        if (!fallidos.publicar(fallido)) {
            // Último recurso: el contenido completo queda en el log para recuperarlo a mano.
            log.error("[{}] NO SE PUDO ENVIAR A FALLIDOS eventId={} shard={} seq={} causa=\"{}\" payload={}",
                    ctx.aplicacion(), eventIdOriginal, ctx.shardId(), ctx.sequenceNumber(), causa, json);
        }
    }

    private static String recortar(String s) {
        if (s == null) return null;
        return s.length() <= MAX_PAYLOAD ? s : s.substring(0, MAX_PAYLOAD) + "...";
    }

    private static String resumen(String s) {
        if (s == null) return "";
        String linea = s.split("\n")[0];
        return linea.length() <= 200 ? linea : linea.substring(0, 200);
    }

    private static void dormir(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
