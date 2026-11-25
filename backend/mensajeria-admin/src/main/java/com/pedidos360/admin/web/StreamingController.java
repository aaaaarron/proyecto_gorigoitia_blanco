package com.pedidos360.admin.web;

import com.pedidos360.admin.dto.EstadoAlarma;
import com.pedidos360.admin.dto.EstadoAplicacion;
import com.pedidos360.admin.dto.EstadoInstanciaStreaming;
import com.pedidos360.admin.service.StreamAdminService;
import com.pedidos360.admin.service.StreamingConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Parcial 3: inspección de las aplicaciones consumidoras y datos derivados del streaming.
 * Los datos de dominio vienen de streaming-service (red interna), nunca del SDK.
 */
@RestController
@RequestMapping("/api/admin/streaming")
@Tag(name = "Aplicaciones consumidoras", description = "Reparto de shards, checkpoints, retraso, alarma y datos en tiempo real")
public class StreamingController {

    private final StreamAdminService streams;
    private final StreamingConsultaService consulta;

    public StreamingController(StreamAdminService streams, StreamingConsultaService consulta) {
        this.streams = streams;
        this.consulta = consulta;
    }

    @GetMapping("/aplicaciones")
    @Operation(summary = "Cada aplicación consumidora: shards asignados a cada instancia, checkpoint y retraso")
    public List<EstadoAplicacion> aplicaciones() {
        return streams.aplicaciones();
    }

    @GetMapping("/aplicaciones/{nombre}")
    @Operation(summary = "Una aplicación consumidora (lee su tabla de coordinación en DynamoDB)")
    public EstadoAplicacion aplicacion(@PathVariable String nombre) {
        return streams.aplicacion(nombre);
    }

    @GetMapping("/instancias")
    @Operation(summary = "Estado informado por cada instancia de streaming-service")
    public List<EstadoInstanciaStreaming> instancias() {
        return consulta.estados();
    }

    @GetMapping("/alarma")
    @Operation(summary = "Estado de la alarma de CloudWatch sobre el retraso de consumo")
    public EstadoAlarma alarma() {
        return streams.alarma();
    }

    @GetMapping("/metricas")
    @Operation(summary = "Analítica en tiempo real (aplicación pedidos360-analitica-app)")
    public Object metricas() {
        return consulta.consultar("/metricas");
    }

    @GetMapping("/seguimiento")
    @Operation(summary = "Últimos pedidos según la aplicación de seguimiento, con latencia del streaming")
    public Object seguimiento() {
        return consulta.consultar("/seguimiento");
    }

    @GetMapping("/seguimiento/{pedidoId}")
    @Operation(summary = "Línea de tiempo de un pedido (cada evento con su shard y sequenceNumber)")
    public Object lineaDeTiempo(@PathVariable Long pedidoId) {
        return consulta.consultar("/seguimiento/" + pedidoId);
    }

    @GetMapping("/stock")
    @Operation(summary = "Stock proyectado por la aplicación de inventario")
    public Object stock() {
        return consulta.consultar("/stock");
    }

    @GetMapping("/alertas")
    @Operation(summary = "Alertas de stock bajo")
    public Object alertas() {
        return consulta.consultar("/alertas");
    }

    @GetMapping("/fallidos")
    @Operation(summary = "Eventos que terminaron en el stream de fallidos, con causa e intentos")
    public Object fallidos() {
        return consulta.consultar("/fallidos");
    }

    @PostMapping("/simulacion")
    @Operation(summary = "Activa/desactiva un error transitorio simulado en la analítica (demo de reintentos)")
    public List<Object> simulacion(@RequestParam boolean falloAnalitica) {
        return consulta.simulacion(falloAnalitica);
    }
}
