package com.pedidos360.admin.dto;

import java.util.List;

/** Estado de un stream en AWS, cruzado con su declaración (si está declarado). */
public record EstadoStream(
        String nombre,
        String estado,
        String modo,
        Integer shardsAbiertos,
        Integer retencionHoras,
        String arn,
        String creadoEn,
        List<String> metricasPorShard,
        boolean declarado,
        String rol,
        String justificacion,
        List<ShardInfo> shards) {}
