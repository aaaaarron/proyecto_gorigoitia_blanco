package com.pedidos360.admin.web;

import com.pedidos360.admin.config.StreamingProperties;
import com.pedidos360.admin.dto.*;
import com.pedidos360.admin.service.StreamAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Parcial 3: administración de streams de Kinesis. Toda la lógica está en {@link StreamAdminService}. */
@RestController
@RequestMapping("/api/admin/streams")
@Tag(name = "Streams", description = "Crear, listar, describir, modificar (shards y retención) y eliminar streams de Kinesis")
public class StreamsController {

    private final StreamAdminService streams;

    public StreamsController(StreamAdminService streams) {
        this.streams = streams;
    }

    @GetMapping
    @Operation(summary = "Lista los streams con estado, modo de capacidad, shards abiertos y retención")
    public List<EstadoStream> listar() {
        return streams.listar();
    }

    @GetMapping("/declarados")
    @Operation(summary = "Declaración de streams, aplicaciones consumidoras y alarma (application.yml) con su justificación")
    public StreamingProperties declarados() {
        return streams.declaracion();
    }

    @PostMapping("/aplicar")
    @Operation(summary = "Crea o ajusta en AWS los streams declarados y la alarma (idempotente)")
    public Map<String, Object> aplicar() {
        return Map.of("pasos", streams.aplicarDeclarados());
    }

    @GetMapping("/{nombre}")
    @Operation(summary = "Describe un stream con sus shards (rango de hash, abierto/cerrado, padre)")
    public EstadoStream describir(@PathVariable String nombre) {
        return streams.describir(nombre);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un stream (espera a que quede ACTIVE y aplica la retención)")
    public EstadoStream crear(@Valid @RequestBody CrearStreamRequest request) {
        return streams.crear(request);
    }

    @PutMapping("/{nombre}/shards")
    @Operation(summary = "Cambia la cantidad de shards (UNIFORM_SCALING: entre la mitad y el doble de los actuales)")
    public EstadoStream cambiarShards(@PathVariable String nombre, @Valid @RequestBody CambiarShardsRequest request) {
        return streams.cambiarShards(nombre, request.shards());
    }

    @PutMapping("/{nombre}/retencion")
    @Operation(summary = "Cambia la retención (24 a 8760 horas)")
    public EstadoStream cambiarRetencion(@PathVariable String nombre, @Valid @RequestBody CambiarRetencionRequest request) {
        return streams.cambiarRetencion(nombre, request.horas());
    }

    @DeleteMapping("/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina el stream (Kinesis cobra por shard-hora mientras exista)")
    public void eliminar(@PathVariable String nombre) {
        streams.eliminar(nombre);
    }

    @GetMapping("/{nombre}/retraso")
    @Operation(summary = "Retraso de consumo (GetRecords.IteratorAgeMilliseconds, máximo por minuto, últimos 15 min)")
    public List<Map<String, Object>> retraso(@PathVariable String nombre) {
        return streams.retraso(nombre);
    }

    @PostMapping("/{nombre}/prueba")
    @Operation(summary = "Publica un registro con contenido libre (p. ej. un evento defectuoso para la demo)")
    public ResultadoPublicacion prueba(@PathVariable String nombre, @Valid @RequestBody PublicarPruebaRequest request) {
        return streams.publicarPrueba(nombre, request.clave(), request.contenido());
    }

    @PostMapping("/{nombre}/rafaga")
    @Operation(summary = "Publica N eventos PEDIDO_CREADO de prueba en un lote y muestra en qué shard cayó cada uno")
    public ResultadoPublicacion rafaga(@PathVariable String nombre, @RequestParam(defaultValue = "20") int cantidad) {
        return streams.publicarRafaga(nombre, cantidad);
    }
}
