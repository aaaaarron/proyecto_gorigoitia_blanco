package com.pedidos360.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** Registro de prueba con contenido libre (sirve para demostrar un evento defectuoso). */
public record PublicarPruebaRequest(@NotBlank String clave, @NotBlank String contenido) {}
