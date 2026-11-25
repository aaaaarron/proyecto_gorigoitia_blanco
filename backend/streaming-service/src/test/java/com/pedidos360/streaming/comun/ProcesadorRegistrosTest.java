package com.pedidos360.streaming.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.streaming.config.KinesisProperties;
import com.pedidos360.streaming.eventos.EventoFallido;
import com.pedidos360.streaming.eventos.EventoPedidoStream;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Validación, duplicados, reintentos con espera creciente y envío a fallidos, sin conectarse a AWS. */
class ProcesadorRegistrosTest {

    private static final String JSON_OK = """
            {"eventId":"ev-1","tipoEvento":"PEDIDO_CREADO","version":1,"ocurridoEn":"2026-11-24T21:00:00Z",
             "origen":"pedidos-service","pedidoId":10,"usuarioEmail":"a@b.cl","estado":"PENDIENTE",
             "tipoEntrega":"ENVIO","total":9990.0,"cantidadItems":2}""";

    private ControlDuplicados duplicados;
    private PublicadorFallidos fallidos;
    private ProcesadorRegistros procesador;
    private final ContextoRegistro ctx = new ContextoRegistro("pedidos360-analitica-app", "pedidos360-pedidos-stream",
            "shardId-000000000001", "4960000001", "10", "test", true);

    @BeforeEach
    void preparar() {
        duplicados = mock(ControlDuplicados.class);
        fallidos = mock(PublicadorFallidos.class);
        when(duplicados.registrar(anyString(), anyString())).thenReturn(true);
        when(fallidos.publicar(any())).thenReturn(true);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        KinesisProperties props = new KinesisProperties("us-east-1", false, "test", "TRIM_HORIZON",
                new KinesisProperties.Streams("p", "i", "f"), Map.of(),
                new KinesisProperties.Reintentos(3, 1, 2.0, 1),
                new KinesisProperties.Lectura(100, 1000));
        procesador = new ProcesadorRegistros(new ObjectMapper(), validator, duplicados, fallidos, props);
    }

    private static ManejadorEventos<EventoPedidoStream> manejador(BiConsumer<EventoPedidoStream, ContextoRegistro> logica) {
        return new ManejadorEventos<>() {
            @Override public String aplicacion() { return "analitica"; }
            @Override public Class<EventoPedidoStream> tipoEvento() { return EventoPedidoStream.class; }
            @Override public void manejar(EventoPedidoStream e, ContextoRegistro c) { logica.accept(e, c); }
        };
    }

    @Test
    void eventoValidoSeProcesaYSeMarca() {
        AtomicInteger llamadas = new AtomicInteger();
        ResultadoProcesamiento r = procesador.procesar(ctx, JSON_OK, manejador((e, c) -> llamadas.incrementAndGet()));

        assertEquals(ResultadoProcesamiento.OK, r);
        assertEquals(1, llamadas.get());
        verify(duplicados).registrar("pedidos360-analitica-app", "ev-1");
        verify(fallidos, never()).publicar(any());
    }

    @Test
    void jsonInvalidoVaAFallidosSinLlamarAlDominio() {
        AtomicInteger llamadas = new AtomicInteger();
        ResultadoProcesamiento r = procesador.procesar(ctx, "esto no es json", manejador((e, c) -> llamadas.incrementAndGet()));

        assertEquals(ResultadoProcesamiento.FALLIDO, r);
        assertEquals(0, llamadas.get());
        ArgumentCaptor<EventoFallido> captor = ArgumentCaptor.forClass(EventoFallido.class);
        verify(fallidos).publicar(captor.capture());
        assertEquals("VALIDACION", captor.getValue().tipoError());
        assertEquals("shardId-000000000001", captor.getValue().shardId());
        assertEquals("esto no es json", captor.getValue().payloadOriginal());
    }

    @Test
    void contenidoInvalidoVaAFallidos() {
        String sinPedido = JSON_OK.replace("\"pedidoId\":10,", "").replace("9990.0", "-5");
        ResultadoProcesamiento r = procesador.procesar(ctx, sinPedido, manejador((e, c) -> fail("no debe llamarse")));

        assertEquals(ResultadoProcesamiento.FALLIDO, r);
        ArgumentCaptor<EventoFallido> captor = ArgumentCaptor.forClass(EventoFallido.class);
        verify(fallidos).publicar(captor.capture());
        assertTrue(captor.getValue().causa().contains("pedidoId"));
        assertTrue(captor.getValue().causa().contains("total"));
    }

    @Test
    void duplicadoSeIgnora() {
        when(duplicados.yaProcesado("pedidos360-analitica-app", "ev-1")).thenReturn(true);
        AtomicInteger llamadas = new AtomicInteger();
        ResultadoProcesamiento r = procesador.procesar(ctx, JSON_OK, manejador((e, c) -> llamadas.incrementAndGet()));

        assertEquals(ResultadoProcesamiento.DUPLICADO, r);
        assertEquals(0, llamadas.get());
        verify(fallidos, never()).publicar(any());
    }

    @Test
    void errorTransitorioSeReintentaYLuegoFunciona() {
        AtomicInteger llamadas = new AtomicInteger();
        ResultadoProcesamiento r = procesador.procesar(ctx, JSON_OK, manejador((e, c) -> {
            if (llamadas.incrementAndGet() < 3) throw new ErrorTransitorio("BD caída");
        }));

        assertEquals(ResultadoProcesamiento.OK, r);
        assertEquals(3, llamadas.get());
        verify(fallidos, never()).publicar(any());
    }

    @Test
    void errorTransitorioQueAgotaLosIntentosVaAFallidos() {
        AtomicInteger llamadas = new AtomicInteger();
        ResultadoProcesamiento r = procesador.procesar(ctx, JSON_OK, manejador((e, c) -> {
            llamadas.incrementAndGet();
            throw new ErrorTransitorio("BD caída");
        }));

        assertEquals(ResultadoProcesamiento.FALLIDO, r);
        assertEquals(3, llamadas.get());
        ArgumentCaptor<EventoFallido> captor = ArgumentCaptor.forClass(EventoFallido.class);
        verify(fallidos).publicar(captor.capture());
        assertEquals("TRANSITORIO_AGOTADO", captor.getValue().tipoError());
        assertEquals(3, captor.getValue().intentos());
        assertEquals("ev-1", captor.getValue().eventIdOriginal());
        verify(duplicados, never()).registrar(anyString(), anyString());
    }

    @Test
    void laAplicacionDeFallidosNoReenviaASuPropioStream() {
        ContextoRegistro ctxFallidos = new ContextoRegistro("pedidos360-fallidos-app", "f", "s", "1", "k", "test", false);
        ResultadoProcesamiento r = procesador.procesar(ctxFallidos, "{roto", manejador((e, c) -> fail("no debe llamarse")));

        assertEquals(ResultadoProcesamiento.FALLIDO, r);
        verify(fallidos, never()).publicar(any());
    }
}
