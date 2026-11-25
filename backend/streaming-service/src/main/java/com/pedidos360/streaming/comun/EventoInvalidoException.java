package com.pedidos360.streaming.comun;

/** Error irrecuperable (JSON inválido, campo faltante, valor imposible): reintentar no sirve. */
public class EventoInvalidoException extends RuntimeException {
    public EventoInvalidoException(String mensaje) { super(mensaje); }
}
