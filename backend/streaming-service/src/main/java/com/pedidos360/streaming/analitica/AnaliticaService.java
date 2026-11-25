package com.pedidos360.streaming.analitica;

import com.pedidos360.streaming.comun.ErrorTransitorio;
import com.pedidos360.streaming.comun.Simulacion;
import com.pedidos360.streaming.eventos.EventoPedidoStream;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Dominio: analítica de ventas en línea (aplicación pedidos360-analitica-app).
 * No conoce Kinesis: recibe el evento ya validado y actualiza las métricas acumuladas.
 * Todas las sumas de un evento van en UNA transacción: o se aplican todas o ninguna (así un reintento
 * no deja métricas a medias).
 */
@Service
public class AnaliticaService {

    private static final DateTimeFormatter MINUTO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC);

    private final MetricaPedidoRepository repository;
    private final Simulacion simulacion;

    public AnaliticaService(MetricaPedidoRepository repository, Simulacion simulacion) {
        this.repository = repository;
        this.simulacion = simulacion;
    }

    @Transactional
    public void registrar(EventoPedidoStream e) {
        if (simulacion.falloAnalitica()) {
            throw new ErrorTransitorio("Base de datos de analítica no disponible (fallo simulado)");
        }
        try {
            if ("PEDIDO_CREADO".equals(e.tipoEvento())) {
                repository.sumar("PEDIDOS_CREADOS", e.total(), 1);
                repository.sumar("VENTAS_TOTALES", e.total(), 1);
                repository.sumar("ENTREGA:" + (e.tipoEntrega() == null ? "RETIRO" : e.tipoEntrega()), e.total(), 1);
                repository.sumar("MINUTO:" + minuto(e.ocurridoEn()), e.total(), 1);
            } else {
                repository.sumar("ESTADO:" + e.estado(), e.total(), 1);
                if ("CANCELADO".equals(e.estado())) {
                    repository.sumar("VENTAS_CANCELADAS", e.total(), 1);
                }
            }
        } catch (DataAccessException ex) {
            throw new ErrorTransitorio("No se pudo actualizar la analítica: " + ex.getMostSpecificCause().getMessage());
        }
    }

    public List<MetricaPedido> metricas() {
        return repository.findAll(Sort.by("clave"));
    }

    private static String minuto(String instante) {
        try {
            return MINUTO.format(Instant.parse(instante));
        } catch (DateTimeParseException ex) {
            return MINUTO.format(Instant.now());
        }
    }
}
