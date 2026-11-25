package com.pedidos360.admin.dto;

import java.util.List;

/** Estado de una aplicación consumidora (consumer group): reparto de shards, checkpoints y retraso. */
public record EstadoAplicacion(
        String nombre,
        String stream,
        String proposito,
        String tablaCoordinacion,
        boolean tablaExiste,
        List<String> instancias,
        List<LeaseInfo> leases,
        Long retrasoMaxMs,
        String mensaje) {}
