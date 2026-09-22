package com.pedidos360.pedidos.exception;

/** Envuelve errores devueltos por productos-service (404 producto inexistente, 400 sin stock, etc). */
public class ProductoServiceException extends RuntimeException {
    private final int status;

    public ProductoServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
