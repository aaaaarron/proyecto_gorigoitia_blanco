package com.pedidos360.pedidos.client;

import com.pedidos360.pedidos.dto.ProductoDTO;
import com.pedidos360.pedidos.exception.ProductoServiceException;
import com.pedidos360.pedidos.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Cliente HTTP hacia productos-service. La comunicación entre microservicios reenvía el
 * mismo JWT del usuario autenticado (Authorization: Bearer ...) que llegó a pedidos-service,
 * de forma que productos-service valida el token igual que si el frontend llamara directo.
 */
@Component
public class ProductosClient {

    private final RestClient restClient;

    public ProductosClient(RestClient productosRestClient) {
        this.restClient = productosRestClient;
    }

    public ProductoDTO obtenerProducto(Long productoId) {
        try {
            return restClient.get()
                    .uri("/api/productos/{id}", productoId)
                    .header("Authorization", authorizationHeader())
                    .retrieve()
                    .body(ProductoDTO.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Producto no encontrado con id " + productoId);
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            throw new ProductoServiceException(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        }
    }

    public ProductoDTO reservarStock(Long productoId, Integer cantidad) {
        try {
            return restClient.post()
                    .uri("/api/productos/{id}/reservar-stock", productoId)
                    .header("Authorization", authorizationHeader())
                    .body(Map.of("cantidad", cantidad))
                    .retrieve()
                    .body(ProductoDTO.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Producto no encontrado con id " + productoId);
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            // Por ejemplo 400 cuando productos-service detecta stock insuficiente
            throw new ProductoServiceException(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        }
    }

    private String authorizationHeader() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No hay contexto de petición HTTP para reenviar el token");
        }
        HttpServletRequest request = attrs.getRequest();
        String header = request.getHeader("Authorization");
        if (header == null) {
            throw new IllegalStateException("Falta el header Authorization para llamar a productos-service");
        }
        return header;
    }
}
