package com.pedidos360.streaming.web;

import com.pedidos360.streaming.analitica.AnaliticaService;
import com.pedidos360.streaming.analitica.MetricaPedido;
import com.pedidos360.streaming.comun.EstadoShards;
import com.pedidos360.streaming.comun.Simulacion;
import com.pedidos360.streaming.config.KinesisProperties;
import com.pedidos360.streaming.fallidos.FallidosService;
import com.pedidos360.streaming.fallidos.RegistroFallido;
import com.pedidos360.streaming.inventario.AlertaStock;
import com.pedidos360.streaming.inventario.InventarioService;
import com.pedidos360.streaming.inventario.StockProducto;
import com.pedidos360.streaming.kcl.ConsumidoresKcl;
import com.pedidos360.streaming.seguimiento.HitoSeguimiento;
import com.pedidos360.streaming.seguimiento.SeguimientoPedido;
import com.pedidos360.streaming.seguimiento.SeguimientoService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API interna de ESTA instancia de streaming-service. La consume mensajeria-admin (que es quien se publica
 * en API Gateway); este servicio no se expone directamente al frontend.
 */
@RestController
@RequestMapping("/api/streaming")
public class StreamingController {

    private final EstadoShards estado;
    private final KinesisProperties props;
    private final ObjectProvider<ConsumidoresKcl> consumidores;
    private final AnaliticaService analitica;
    private final SeguimientoService seguimiento;
    private final InventarioService inventario;
    private final FallidosService fallidos;
    private final Simulacion simulacion;

    public StreamingController(EstadoShards estado, KinesisProperties props, ObjectProvider<ConsumidoresKcl> consumidores,
                               AnaliticaService analitica, SeguimientoService seguimiento,
                               InventarioService inventario, FallidosService fallidos, Simulacion simulacion) {
        this.estado = estado;
        this.props = props;
        this.consumidores = consumidores;
        this.analitica = analitica;
        this.seguimiento = seguimiento;
        this.inventario = inventario;
        this.fallidos = fallidos;
        this.simulacion = simulacion;
    }

    /** Shards que procesa esta instancia en cada aplicación, con su posición de lectura y retraso. */
    @GetMapping("/estado")
    public Map<String, Object> estado() {
        ConsumidoresKcl kcl = consumidores.getIfAvailable();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("instancia", props.instanciaId());
        r.put("consumidoresActivos", kcl != null && kcl.isRunning());
        r.put("aplicaciones", kcl != null ? kcl.aplicacionesActivas() : List.of());
        r.put("posicionInicial", props.posicionInicial());
        r.put("falloAnaliticaSimulado", simulacion.falloAnalitica());
        r.put("shards", estado.vistas());
        return r;
    }

    @GetMapping("/metricas")
    public List<MetricaPedido> metricas() {
        return analitica.metricas();
    }

    @GetMapping("/seguimiento")
    public List<SeguimientoPedido> seguimiento() {
        return seguimiento.recientes();
    }

    @GetMapping("/seguimiento/{pedidoId}")
    public List<HitoSeguimiento> lineaDeTiempo(@PathVariable Long pedidoId) {
        return seguimiento.lineaDeTiempo(pedidoId);
    }

    @GetMapping("/stock")
    public List<StockProducto> stock() {
        return inventario.stock();
    }

    @GetMapping("/alertas")
    public List<AlertaStock> alertas() {
        return inventario.alertas();
    }

    @GetMapping("/fallidos")
    public List<RegistroFallido> fallidos() {
        return fallidos.recientes();
    }

    /** Activa/desactiva el error transitorio simulado de la analítica (demo de reintentos y fallidos). */
    @PostMapping("/simulacion")
    public Map<String, Object> simulacion(@RequestParam boolean falloAnalitica) {
        simulacion.falloAnalitica(falloAnalitica);
        return Map.of("instancia", props.instanciaId(), "falloAnaliticaSimulado", simulacion.falloAnalitica());
    }
}
