package com.pedidos360.admin.service;

import com.pedidos360.admin.config.DeclaracionStreams;
import com.pedidos360.admin.config.StreamingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Parcial 3: al arrancar valida la declaración de streams y, si streaming.aplicar-al-iniciar=true, la aplica
 * en AWS en segundo plano (crear un stream tarda ~30 s y no debe bloquear la API del admin).
 */
@Component
public class StreamsInicializador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StreamsInicializador.class);

    private final StreamAdminService streams;
    private final StreamingProperties props;

    public StreamsInicializador(StreamAdminService streams, StreamingProperties props) {
        this.streams = streams;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            DeclaracionStreams.validar(props);
        } catch (IllegalStateException e) {
            log.error("[streams] {}", e.getMessage());
            return;
        }
        if (!props.aplicarAlIniciar()) {
            log.info("[streams] aplicar-al-iniciar=false: los streams se crean con POST /api/admin/streams/aplicar "
                    + "o infra/6-kinesis-streams.ps1");
            return;
        }
        Thread hilo = new Thread(() -> {
            try {
                streams.aplicarDeclarados().forEach(p -> log.info("[streams] {}", p));
                log.info("[streams] Declaración de streams aplicada");
            } catch (RuntimeException e) {
                log.error("[streams] No se pudo aplicar la declaración de streams: {}", e.getMessage());
            }
        }, "aplicar-streams");
        hilo.setDaemon(true);
        hilo.start();
    }
}
