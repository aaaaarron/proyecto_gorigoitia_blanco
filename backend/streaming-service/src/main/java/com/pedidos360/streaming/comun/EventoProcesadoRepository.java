package com.pedidos360.streaming.comun;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, Long> {
    boolean existsByAplicacionAndEventId(String aplicacion, String eventId);
}
