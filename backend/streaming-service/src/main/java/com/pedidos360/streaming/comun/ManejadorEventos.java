package com.pedidos360.streaming.comun;

/**
 * Puerto entre la infraestructura de streaming y el dominio. Cada aplicación consumidora tiene un manejador
 * que convierte el registro ya validado en una llamada a su servicio de dominio.
 *
 * <p>Para señalar errores, el dominio lanza {@link EventoInvalidoException} (irrecuperable: va al stream
 * de fallidos sin reintentar) o {@link ErrorTransitorio} (se reintenta con espera creciente).</p>
 */
public interface ManejadorEventos<T extends EventoStream> {

    /** Clave de la aplicación en kinesis.aplicaciones (p. ej. "analitica"). */
    String aplicacion();

    /** Clase del DTO en que se deserializa el JSON del registro. */
    Class<T> tipoEvento();

    void manejar(T evento, ContextoRegistro contexto);
}
