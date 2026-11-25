package com.pedidos360.streaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Parcial 3: microservicio con las aplicaciones consumidoras (consumer groups) de Kinesis.
 * Cada aplicación es un Scheduler de la KCL con nombre propio y su propia tabla de coordinación en DynamoDB.
 * Varias instancias de este servicio se reparten los shards de cada stream.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StreamingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StreamingServiceApplication.class, args);
    }
}
