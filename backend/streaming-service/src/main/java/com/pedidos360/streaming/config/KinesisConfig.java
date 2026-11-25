package com.pedidos360.streaming.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.KinesisClientUtil;

/**
 * Clientes del AWS SDK que usa la KCL: Kinesis (lectura de registros), DynamoDB (tabla de coordinación:
 * leases y checkpoints) y CloudWatch (métricas de la KCL, p. ej. MillisBehindLatest).
 * Credenciales: variables de entorno del Learner Lab (local) o rol LabRole (EC2). Nunca en el repositorio.
 */
@Configuration
public class KinesisConfig {

    @Bean(destroyMethod = "close")
    public KinesisAsyncClient kinesisAsyncClient(KinesisProperties props) {
        return KinesisClientUtil.createKinesisAsyncClient(
                KinesisAsyncClient.builder().region(Region.of(props.region())));
    }

    @Bean(destroyMethod = "close")
    public DynamoDbAsyncClient dynamoDbAsyncClient(KinesisProperties props) {
        return DynamoDbAsyncClient.builder().region(Region.of(props.region())).build();
    }

    @Bean(destroyMethod = "close")
    public CloudWatchAsyncClient cloudWatchAsyncClient(KinesisProperties props) {
        return CloudWatchAsyncClient.builder().region(Region.of(props.region())).build();
    }
}
