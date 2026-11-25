package com.pedidos360.streaming.comun;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Tolerancia a duplicados. La marca se guarda DESPUÉS de procesar con éxito: si se guardara antes, un evento
 * cuyo procesamiento falló quedaría marcado y se perdería al releerse.
 */
@Component
public class ControlDuplicados {

    private final EventoProcesadoRepository repository;

    public ControlDuplicados(EventoProcesadoRepository repository) {
        this.repository = repository;
    }

    public boolean yaProcesado(String aplicacion, String eventId) {
        return repository.existsByAplicacionAndEventId(aplicacion, eventId);
    }

    /** false si otra instancia lo registró al mismo tiempo (restricción única). */
    public boolean registrar(String aplicacion, String eventId) {
        try {
            repository.save(new EventoProcesado(aplicacion, eventId));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}
