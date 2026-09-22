package com.pedidos360.productos.controller;

import com.pedidos360.productos.dto.ProductoDTO;
import com.pedidos360.productos.dto.ProductoRequest;
import com.pedidos360.productos.dto.ReservaStockRequest;
import com.pedidos360.productos.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    // USER y ADMIN pueden leer
    @GetMapping
    public List<ProductoDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ProductoDTO obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    // Solo ADMIN puede escribir (regla reforzada también en SecurityConfig)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoDTO crear(@Valid @RequestBody ProductoRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public ProductoDTO actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }

    // Endpoint interno usado por pedidos-service para validar/descontar stock
    @PostMapping("/{id}/reservar-stock")
    public ProductoDTO reservarStock(@PathVariable Long id, @Valid @RequestBody ReservaStockRequest request) {
        return service.verificarYDescontarStock(id, request.getCantidad());
    }
}
