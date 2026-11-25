package com.pedidos360.admin.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Valida que la declaración de streams cumpla las reglas del diseño antes de tocar AWS:
 * al menos dos streams de negocio, un stream de fallidos que retenga al menos tanto como los de negocio,
 * retención dentro del rango de Kinesis (24 a 8760 h), aplicaciones sobre streams declarados y
 * al menos un stream leído por dos aplicaciones distintas.
 */
public final class DeclaracionStreams {

    private DeclaracionStreams() {}

    public static void validar(StreamingProperties p) {
        List<String> errores = new ArrayList<>();
        List<StreamingProperties.StreamDeclarado> streams = p.streams() == null ? List.of() : p.streams();
        List<StreamingProperties.AplicacionDeclarada> apps = p.aplicaciones() == null ? List.of() : p.aplicaciones();

        long negocio = streams.stream().filter(s -> "NEGOCIO".equals(s.rol())).count();
        if (negocio < 2) errores.add("Se requieren al menos 2 streams de negocio (hay " + negocio + ")");

        List<StreamingProperties.StreamDeclarado> fallidos = streams.stream().filter(s -> "FALLIDOS".equals(s.rol())).toList();
        if (fallidos.size() != 1) errores.add("Debe haber exactamente 1 stream de fallidos (hay " + fallidos.size() + ")");

        int maxRetencionNegocio = streams.stream().filter(s -> "NEGOCIO".equals(s.rol()))
                .mapToInt(StreamingProperties.StreamDeclarado::retencionHoras).max().orElse(24);
        for (StreamingProperties.StreamDeclarado f : fallidos) {
            if (f.retencionHoras() < maxRetencionNegocio) {
                errores.add("El stream de fallidos " + f.nombre() + " debe retener al menos " + maxRetencionNegocio + " h");
            }
        }
        for (StreamingProperties.StreamDeclarado s : streams) {
            if (s.retencionHoras() < 24 || s.retencionHoras() > 8760) {
                errores.add(s.nombre() + ": la retención debe estar entre 24 y 8760 h");
            }
            if ("PROVISIONED".equals(s.modo()) && s.shards() < 1) {
                errores.add(s.nombre() + ": en modo PROVISIONED se requiere al menos 1 shard");
            }
            if (!"PROVISIONED".equals(s.modo()) && !"ON_DEMAND".equals(s.modo())) {
                errores.add(s.nombre() + ": modo de capacidad desconocido " + s.modo());
            }
        }
        for (StreamingProperties.AplicacionDeclarada a : apps) {
            if (p.stream(a.stream()) == null) {
                errores.add("La aplicación " + a.nombre() + " lee un stream no declarado: " + a.stream());
            }
        }
        Map<String, Long> appsPorStream = apps.stream()
                .collect(Collectors.groupingBy(StreamingProperties.AplicacionDeclarada::stream, Collectors.counting()));
        if (appsPorStream.values().stream().noneMatch(n -> n >= 2)) {
            errores.add("Al menos un stream debe ser leído por 2 aplicaciones consumidoras distintas");
        }
        if (!errores.isEmpty()) {
            throw new IllegalStateException("Declaración de streams inválida: " + String.join("; ", errores));
        }
    }
}
