package com.pedidos360.productos.streaming;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

import java.time.Duration;

/**
 * Parcial 3: cliente Kinesis. Las credenciales NO están en el repositorio: el SDK las toma de las variables
 * AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_SESSION_TOKEN (local, archivo .env) o del rol LabRole
 * de la instancia EC2 (DefaultCredentialsProvider).
 */
@Configuration
@EnableConfigurationProperties(StreamingProperties.class)
public class StreamingConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "streaming.habilitado", havingValue = "true", matchIfMissing = true)
    public KinesisClient kinesisClient(StreamingProperties props) {
        Duration timeout = Duration.ofMillis(props.envio().timeoutLlamadaMs());
        return KinesisClient.builder()
                .region(Region.of(props.region()))
                .overrideConfiguration(o -> o.apiCallTimeout(timeout).apiCallAttemptTimeout(timeout))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "streaming.habilitado", havingValue = "true", matchIfMissing = true)
    public ProductorKinesis productorKinesis(KinesisClient kinesisClient, StreamingProperties props) {
        return new ProductorKinesis(kinesisClient, props.envio().maxIntentos(), props.envio().esperaInicialMs());
    }
}
