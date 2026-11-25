package com.pedidos360.streaming.inventario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaStockRepository extends JpaRepository<AlertaStock, Long> {
    List<AlertaStock> findTop20ByOrderByIdDesc();
}
