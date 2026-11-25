package com.pedidos360.productos.service;

import com.pedidos360.productos.dto.ProductoDTO;
import com.pedidos360.productos.dto.ProductoRequest;
import com.pedidos360.productos.entity.Producto;
import com.pedidos360.productos.exception.InsufficientStockException;
import com.pedidos360.productos.exception.ResourceNotFoundException;
import com.pedidos360.productos.repository.ProductoRepository;
import com.pedidos360.productos.streaming.EventoInventario;
import com.pedidos360.productos.streaming.InventarioPublisher;
import com.pedidos360.productos.streaming.StreamingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository repository;
    private final InventarioPublisher inventarioPublisher;
    private final StreamingProperties streaming;

    public ProductoService(ProductoRepository repository, InventarioPublisher inventarioPublisher,
                           StreamingProperties streaming) {
        this.repository = repository;
        this.inventarioPublisher = inventarioPublisher;
        this.streaming = streaming;
    }

    public List<ProductoDTO> listar() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    public ProductoDTO obtener(Long id) {
        Producto producto = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id " + id));
        return toDTO(producto);
    }

    public ProductoDTO crear(ProductoRequest request) {
        Producto producto = new Producto(request.getNombre(), request.getDescripcion(),
                request.getPrecio(), request.getStock());
        return toDTO(repository.save(producto));
    }

    public ProductoDTO actualizar(Long id, ProductoRequest request) {
        Producto producto = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id " + id));
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setPrecio(request.getPrecio());
        producto.setStock(request.getStock());
        Producto guardado = repository.save(producto);
        // Parcial 3: un ajuste manual del stock también es un movimiento de inventario
        inventarioPublisher.publicar(List.of(EventoInventario.de(EventoInventario.STOCK_AJUSTADO,
                guardado.getId(), guardado.getNombre(), 0, guardado.getStock())));
        return toDTO(guardado);
    }

    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Producto no encontrado con id " + id);
        }
        repository.deleteById(id);
    }

    /** Usado por pedidos-service (vía HTTP) antes de crear un pedido. */
    public ProductoDTO verificarYDescontarStock(Long id, Integer cantidad) {
        Producto producto = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id " + id));
        if (producto.getStock() < cantidad) {
            throw new InsufficientStockException("Stock insuficiente para el producto " + producto.getNombre());
        }
        producto.setStock(producto.getStock() - cantidad);
        Producto guardado = repository.save(producto);

        // Parcial 3: el movimiento se publica en el stream de inventario (clave = productoId).
        // Si el stock queda bajo el umbral, se agrega STOCK_BAJO en el MISMO lote (un solo PutRecords).
        List<EventoInventario> eventos = new ArrayList<>();
        eventos.add(EventoInventario.de(EventoInventario.STOCK_RESERVADO, guardado.getId(), guardado.getNombre(),
                cantidad, guardado.getStock()));
        if (guardado.getStock() <= streaming.umbralStockBajo()) {
            eventos.add(EventoInventario.de(EventoInventario.STOCK_BAJO, guardado.getId(), guardado.getNombre(),
                    cantidad, guardado.getStock()));
        }
        inventarioPublisher.publicar(eventos);
        return toDTO(guardado);
    }

    private ProductoDTO toDTO(Producto p) {
        return new ProductoDTO(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(), p.getStock());
    }
}
