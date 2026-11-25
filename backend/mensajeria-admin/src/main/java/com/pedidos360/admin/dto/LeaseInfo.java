package com.pedidos360.admin.dto;

/**
 * Una fila de la tabla de coordinación (DynamoDB) de una aplicación consumidora: qué instancia tiene el shard,
 * hasta qué sequenceNumber llegó (checkpoint), cuántas veces se renovó el lease y el retraso informado por esa
 * instancia en su última lectura.
 */
public record LeaseInfo(
        String shardId,
        String instancia,
        String checkpoint,
        Long leaseCounter,
        Long cambiosDeDueno,
        Long millisBehindLatest,
        Long procesados,
        Long fallidos,
        String ultimaLectura) {}
