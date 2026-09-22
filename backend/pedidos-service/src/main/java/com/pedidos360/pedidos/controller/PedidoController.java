package com.pedidos360.pedidos.controller;

import com.pedidos360.pedidos.dto.EstadoPedidoRequest;
import com.pedidos360.pedidos.dto.PedidoDTO;
import com.pedidos360.pedidos.dto.PedidoRequest;
import com.pedidos360.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @GetMapping
    public List<PedidoDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public PedidoDTO obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoDTO crear(@Valid @RequestBody PedidoRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}/estado")
    public PedidoDTO actualizarEstado(@PathVariable Long id, @Valid @RequestBody EstadoPedidoRequest request) {
        return service.actualizarEstado(id, request.getEstado());
    }
}
