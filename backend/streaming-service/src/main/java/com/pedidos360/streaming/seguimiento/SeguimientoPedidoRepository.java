package com.pedidos360.streaming.seguimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeguimientoPedidoRepository extends JpaRepository<SeguimientoPedido, Long> {
    Optional<SeguimientoPedido> findByPedidoId(Long pedidoId);
    List<SeguimientoPedido> findTop20ByOrderByActualizadoEnDesc();
}
