package com.pedidos360.streaming.seguimiento;

import com.pedidos360.streaming.comun.ContextoRegistro;
import com.pedidos360.streaming.comun.ErrorTransitorio;
import com.pedidos360.streaming.comun.EventoInvalidoException;
import com.pedidos360.streaming.eventos.EventoPedidoStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Dominio: seguimiento del pedido en tiempo real (aplicación pedidos360-seguimiento-app).
 * Lee el MISMO stream que la analítica pero con su propio avance (checkpoint independiente).
 * Como la clave de partición es pedidoId, los eventos de un pedido llegan en orden; aun así, si llegara
 * uno más antiguo que el último aplicado (p. ej. un reproceso), se guarda en la línea de tiempo
 * pero no retrocede el estado actual.
 */
@Service
public class SeguimientoService {

    private static final Logger log = LoggerFactory.getLogger(SeguimientoService.class);

    private final SeguimientoPedidoRepository seguimientos;
    private final HitoSeguimientoRepository hitos;

    public SeguimientoService(SeguimientoPedidoRepository seguimientos, HitoSeguimientoRepository hitos) {
        this.seguimientos = seguimientos;
        this.hitos = hitos;
    }

    @Transactional
    public void registrar(EventoPedidoStream e, ContextoRegistro ctx) {
        Instant ocurrido;
        try {
            ocurrido = Instant.parse(e.ocurridoEn());
        } catch (DateTimeParseException ex) {
            throw new EventoInvalidoException("ocurridoEn no es una fecha ISO-8601: " + e.ocurridoEn());
        }
        long latencia = Math.max(0, Duration.between(ocurrido, Instant.now()).toMillis());
        try {
            hitos.save(new HitoSeguimiento(e.pedidoId(), e.tipoEvento(), e.estado(), e.eventId(), ocurrido, latencia,
                    ctx.shardId(), ctx.sequenceNumber(), ctx.instancia()));

            SeguimientoPedido s = seguimientos.findByPedidoId(e.pedidoId()).orElseGet(() -> new SeguimientoPedido(e.pedidoId()));
            s.contarEvento();
            if (s.getUltimoOcurridoEn() != null && ocurrido.isBefore(s.getUltimoOcurridoEn())) {
                log.warn("[seguimiento] Pedido {}: evento {} más antiguo que el estado actual; no se retrocede el estado",
                        e.pedidoId(), e.eventId());
            } else {
                s.aplicar(e.estado(), e.usuarioEmail(), e.tipoEntrega(), e.total(), e.eventId(), ocurrido, latencia, ctx.shardId());
            }
            seguimientos.save(s);
            log.info("[seguimiento] Pedido {} -> {} (latencia {} ms, shard {})", e.pedidoId(), e.estado(), latencia, ctx.shardId());
        } catch (DataAccessException ex) {
            throw new ErrorTransitorio("No se pudo guardar el seguimiento: " + ex.getMostSpecificCause().getMessage());
        }
    }

    public List<SeguimientoPedido> recientes() {
        return seguimientos.findTop20ByOrderByActualizadoEnDesc();
    }

    public List<HitoSeguimiento> lineaDeTiempo(Long pedidoId) {
        return hitos.findByPedidoIdOrderByIdAsc(pedidoId);
    }
}
