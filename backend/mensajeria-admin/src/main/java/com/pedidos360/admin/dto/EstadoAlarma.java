package com.pedidos360.admin.dto;

public record EstadoAlarma(String nombre, String stream, String estado, Double umbralMs, String razon, String actualizada) {}
