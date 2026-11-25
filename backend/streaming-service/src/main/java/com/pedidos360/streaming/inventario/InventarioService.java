package com.pedidos360.streaming.inventario;

import com.pedidos360.streaming.comun.ErrorTransitorio;
import com.pedidos360.streaming.eventos.EventoInventario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Dominio: inventario en tiempo real (aplicación pedidos360-inventario-app). */
@Service
public class InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final StockProductoRepository stock;
    private final AlertaStockRepository alertas;

    public InventarioService(StockProductoRepository stock, AlertaStockRepository alertas) {
        this.stock = stock;
        this.alertas = alertas;
    }

    @Transactional
    public void registrar(EventoInventario e) {
        try {
            StockProducto p = stock.findById(e.productoId()).orElseGet(() -> new StockProducto(e.productoId()));
            p.actualizar(e.nombre(), e.stockRestante(), e.tipoEvento());
            stock.save(p);
            if ("STOCK_BAJO".equals(e.tipoEvento())) {
                alertas.save(new AlertaStock(e.productoId(), e.nombre(), e.stockRestante(), e.eventId()));
                log.warn("[inventario] ALERTA stock bajo: producto {} ({}) quedó en {}", e.productoId(), e.nombre(), e.stockRestante());
            } else {
                log.info("[inventario] Producto {} -> stock {} ({})", e.productoId(), e.stockRestante(), e.tipoEvento());
            }
        } catch (DataAccessException ex) {
            throw new ErrorTransitorio("No se pudo actualizar el inventario: " + ex.getMostSpecificCause().getMessage());
        }
    }

    public List<StockProducto> stock() {
        return stock.findAll(Sort.by("productoId"));
    }

    public List<AlertaStock> alertas() {
        return alertas.findTop20ByOrderByIdDesc();
    }
}
