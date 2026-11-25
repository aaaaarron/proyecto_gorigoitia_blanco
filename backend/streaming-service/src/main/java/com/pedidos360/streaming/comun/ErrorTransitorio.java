package com.pedidos360.streaming.comun;

/** Error temporal (BD o servicio externo no disponible, timeout): se reintenta con espera creciente. */
public class ErrorTransitorio extends RuntimeException {
    public ErrorTransitorio(String mensaje) { super(mensaje); }
}
