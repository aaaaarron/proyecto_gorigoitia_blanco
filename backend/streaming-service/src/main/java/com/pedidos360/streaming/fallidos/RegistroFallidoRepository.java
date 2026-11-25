package com.pedidos360.streaming.fallidos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroFallidoRepository extends JpaRepository<RegistroFallido, Long> {
    List<RegistroFallido> findTop50ByOrderByIdDesc();
}
