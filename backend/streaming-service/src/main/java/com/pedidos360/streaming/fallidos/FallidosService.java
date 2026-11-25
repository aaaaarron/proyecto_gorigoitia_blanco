package com.pedidos360.streaming.fallidos;

import com.pedidos360.streaming.comun.ErrorTransitorio;
import com.pedidos360.streaming.eventos.EventoFallido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Dominio: monitor del stream de mensajes fallidos (aplicación pedidos360-fallidos-app).
 * Registra cada fallido en el log con el formato [FALLIDO] y en la tabla registros_fallidos.
 */
@Service
public class FallidosService {

    private static final Logger log = LoggerFactory.getLogger(FallidosService.class);

    private final RegistroFallidoRepository repository;

    public FallidosService(RegistroFallidoRepository repository) {
        this.repository = repository;
    }

    public void registrar(EventoFallido f) {
        log.error("[FALLIDO] aplicacion={} stream={} shard={} seq={} eventIdOriginal={} intentos={} tipo={} causa=\"{}\"",
                f.aplicacion(), f.streamOrigen(), f.shardId(), f.sequenceNumber(), f.eventIdOriginal(),
                f.intentos(), f.tipoError(), f.causa());
        try {
            repository.save(new RegistroFallido(f.eventId(), f.eventIdOriginal(), f.aplicacion(), f.streamOrigen(),
                    f.shardId(), f.sequenceNumber(), f.intentos(), f.tipoError(), f.causa(), f.payloadOriginal(),
                    f.fallidoEn()));
        } catch (DataAccessException ex) {
            throw new ErrorTransitorio("No se pudo guardar el fallido: " + ex.getMostSpecificCause().getMessage());
        }
    }

    public List<RegistroFallido> recientes() {
        return repository.findTop50ByOrderByIdDesc();
    }
}
