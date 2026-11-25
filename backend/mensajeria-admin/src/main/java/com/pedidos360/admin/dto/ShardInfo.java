package com.pedidos360.admin.dto;

/** Un shard del stream: rango de hash que atiende (lo decide la clave de partición) y si sigue abierto. */
public record ShardInfo(String shardId, boolean abierto, String hashInicio, String hashFin,
                        String secuenciaInicial, String padre) {}
