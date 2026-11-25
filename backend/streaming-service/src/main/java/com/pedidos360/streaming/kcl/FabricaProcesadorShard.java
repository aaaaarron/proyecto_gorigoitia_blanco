package com.pedidos360.streaming.kcl;

import com.pedidos360.streaming.comun.EstadoShards;
import com.pedidos360.streaming.comun.EventoStream;
import com.pedidos360.streaming.comun.ManejadorEventos;
import com.pedidos360.streaming.comun.ProcesadorRegistros;
import com.pedidos360.streaming.config.KinesisProperties;
import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;

/** La KCL pide un procesador nuevo cada vez que esta instancia obtiene el lease de un shard. */
public class FabricaProcesadorShard<T extends EventoStream> implements ShardRecordProcessorFactory {

    private final KinesisProperties.Aplicacion aplicacion;
    private final ManejadorEventos<T> manejador;
    private final ProcesadorRegistros procesador;
    private final EstadoShards estado;
    private final String instancia;

    public FabricaProcesadorShard(KinesisProperties.Aplicacion aplicacion, ManejadorEventos<T> manejador,
                                  ProcesadorRegistros procesador, EstadoShards estado, String instancia) {
        this.aplicacion = aplicacion;
        this.manejador = manejador;
        this.procesador = procesador;
        this.estado = estado;
        this.instancia = instancia;
    }

    @Override
    public ShardRecordProcessor shardRecordProcessor() {
        return new ProcesadorShard<>(aplicacion, manejador, procesador, estado, instancia);
    }
}
