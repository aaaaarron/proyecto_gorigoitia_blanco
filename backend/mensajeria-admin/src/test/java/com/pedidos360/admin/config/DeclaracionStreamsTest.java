package com.pedidos360.admin.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeclaracionStreamsTest {

    private static final StreamingProperties.StreamDeclarado PEDIDOS =
            new StreamingProperties.StreamDeclarado("pedidos", "NEGOCIO", "PROVISIONED", 3, 48, "");
    private static final StreamingProperties.StreamDeclarado INVENTARIO =
            new StreamingProperties.StreamDeclarado("inventario", "NEGOCIO", "PROVISIONED", 3, 24, "");
    private static final StreamingProperties.StreamDeclarado FALLIDOS =
            new StreamingProperties.StreamDeclarado("fallidos", "FALLIDOS", "PROVISIONED", 1, 168, "");

    private static final List<StreamingProperties.AplicacionDeclarada> APPS = List.of(
            new StreamingProperties.AplicacionDeclarada("analitica", "pedidos", ""),
            new StreamingProperties.AplicacionDeclarada("seguimiento", "pedidos", ""),
            new StreamingProperties.AplicacionDeclarada("inventario", "inventario", ""));

    private static StreamingProperties props(List<StreamingProperties.StreamDeclarado> streams,
                                             List<StreamingProperties.AplicacionDeclarada> apps) {
        return new StreamingProperties(false, "us-east-1", "", false, streams, apps,
                new StreamingProperties.Alarma("a", "pedidos", 60000, 2, ""));
    }

    @Test
    void declaracionDelProyectoEsValida() {
        assertDoesNotThrow(() -> DeclaracionStreams.validar(props(List.of(PEDIDOS, INVENTARIO, FALLIDOS), APPS)));
    }

    @Test
    void exigeDosStreamsDeNegocio() {
        assertThrows(IllegalStateException.class,
                () -> DeclaracionStreams.validar(props(List.of(PEDIDOS, FALLIDOS), APPS.subList(0, 2))));
    }

    @Test
    void fallidosDebeRetenerAlMenosLoQueLosDeNegocio() {
        StreamingProperties.StreamDeclarado corto =
                new StreamingProperties.StreamDeclarado("fallidos", "FALLIDOS", "PROVISIONED", 1, 24, "");
        assertThrows(IllegalStateException.class,
                () -> DeclaracionStreams.validar(props(List.of(PEDIDOS, INVENTARIO, corto), APPS)));
    }

    @Test
    void exigeDosAplicacionesSobreUnMismoStream() {
        List<StreamingProperties.AplicacionDeclarada> unaPorStream = List.of(
                new StreamingProperties.AplicacionDeclarada("analitica", "pedidos", ""),
                new StreamingProperties.AplicacionDeclarada("inventario", "inventario", ""));
        assertThrows(IllegalStateException.class,
                () -> DeclaracionStreams.validar(props(List.of(PEDIDOS, INVENTARIO, FALLIDOS), unaPorStream)));
    }

    @Test
    void rechazaRetencionFueraDeRango() {
        StreamingProperties.StreamDeclarado invalido =
                new StreamingProperties.StreamDeclarado("pedidos", "NEGOCIO", "PROVISIONED", 3, 12, "");
        assertThrows(IllegalStateException.class,
                () -> DeclaracionStreams.validar(props(List.of(invalido, INVENTARIO, FALLIDOS), APPS)));
    }
}
