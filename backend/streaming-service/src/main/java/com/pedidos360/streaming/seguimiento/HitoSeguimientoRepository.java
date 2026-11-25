package com.pedidos360.streaming.seguimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HitoSeguimientoRepository extends JpaRepository<HitoSeguimiento, Long> {
    List<HitoSeguimiento> findByPedidoIdOrderByIdAsc(Long pedidoId);
}
