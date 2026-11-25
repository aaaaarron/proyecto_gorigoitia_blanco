package com.pedidos360.pedidos.streaming;

import com.pedidos360.pedidos.entity.Pedido;
import com.pedidos360.pedidos.messaging.TipoEvento;

import java.time.Instant;
import java.util.UUID;

/**
 * Parcial 3: DTO del evento que se publica en el stream pedidos360-pedidos-stream (serializado en JSON).
 *
 * <ul>
 *   <li>{@code eventId}: único por evento. Los consumidores lo usan para descartar duplicados
 *       (Kinesis entrega "al menos una vez" y un registro puede leerse otra vez tras un reinicio).</li>
 *   <li>{@code tipoEvento} y {@code version}: permiten evolucionar el contrato sin romper consumidores.</li>
 *   <li>{@code ocurridoEn}: instante del hecho de negocio; el consumidor calcula la latencia del streaming.</li>
 *   <li>{@code origen}: microservicio que produjo el evento (trazabilidad).</li>
 *   <li>{@code pedidoId}: también es la CLAVE DE PARTICIÓN. Todos los eventos de un pedido caen en el mismo
 *       shard y se leen en el orden en que ocurrieron (creado → confirmado → listo...), mientras que pedidos
 *       distintos se reparten entre los 3 shards.</li>
 *   <li>Resto de campos: lo que necesitan los consumidores (analítica y seguimiento) sin consultar otra API.</li>
 * </ul>
 */
public record EventoPedidoStream(
        String eventId,
        String tipoEvento,
        int version,
        String ocurridoEn,
        String origen,
        Long pedidoId,
        String usuarioEmail,
        String estado,
        String tipoEntrega,
        Double total,
        Integer cantidadItems) {

    public static final int VERSION_ACTUAL = 1;

    public static EventoPedidoStream de(Pedido pedido, TipoEvento tipo) {
        return new EventoPedidoStream(
                UUID.randomUUID().toString(),
                tipo.name(),
                VERSION_ACTUAL,
                Instant.now().toString(),
                "pedidos-service",
                pedido.getId(),
                pedido.getUsuarioEmail(),
                pedido.getEstado() != null ? pedido.getEstado().name() : null,
                pedido.getTipoEntrega() != null ? pedido.getTipoEntrega() : "RETIRO",
                pedido.getTotal(),
                pedido.getCantidad());
    }

    /** Clave de partición: el id del pedido (orden garantizado por pedido dentro de su shard). */
    public String claveParticion() {
        return String.valueOf(pedidoId);
    }
}
