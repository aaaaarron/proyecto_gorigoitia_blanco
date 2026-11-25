package com.pedidos360.streaming.analitica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetricaPedidoRepository extends JpaRepository<MetricaPedido, String> {

    /** Upsert atómico en PostgreSQL: varias instancias pueden sumar a la misma métrica sin pisarse. */
    @Modifying
    @Query(value = """
            insert into metricas_pedidos (clave, valor, cantidad, actualizado_en)
            values (:clave, :valor, :cantidad, current_timestamp)
            on conflict (clave) do update set
              valor = metricas_pedidos.valor + excluded.valor,
              cantidad = metricas_pedidos.cantidad + excluded.cantidad,
              actualizado_en = current_timestamp
            """, nativeQuery = true)
    int sumar(@Param("clave") String clave, @Param("valor") double valor, @Param("cantidad") long cantidad);
}
