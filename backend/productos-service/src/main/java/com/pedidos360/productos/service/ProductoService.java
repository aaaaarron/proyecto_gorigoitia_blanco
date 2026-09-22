package com.pedidos360.productos.service;

import com.pedidos360.productos.dto.ProductoDTO;
import com.pedidos360.productos.dto.ProductoRequest;
import com.pedidos360.productos.entity.Producto;
import com.pedidos360.productos.exception.InsufficientStockException;
import com.pedidos360.productos.exception.ResourceNotFoundException;
import com.pedidos360.productos.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
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
        return toDTO(repository.save(producto));
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
        return toDTO(repository.save(producto));
    }

    private ProductoDTO toDTO(Producto p) {
        return new ProductoDTO(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(), p.getStock());
    }
}
