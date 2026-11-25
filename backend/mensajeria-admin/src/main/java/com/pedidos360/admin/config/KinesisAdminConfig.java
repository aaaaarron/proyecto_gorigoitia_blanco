package com.pedidos360.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;

/**
 * Parcial 3: clientes que usa SOLO {@link com.pedidos360.admin.service.StreamAdminService}:
 * Kinesis (streams), DynamoDB (tablas de coordinación de las aplicaciones consumidoras) y CloudWatch
 * (retraso de consumo y alarma). Credenciales del Learner Lab (.env) o rol LabRole (EC2).
 */
@Configuration
public class KinesisAdminConfig {

    @Bean(destroyMethod = "close")
    public KinesisClient kinesisClient(StreamingProperties props) {
        return KinesisClient.builder().region(Region.of(props.region())).build();
    }

    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient(StreamingProperties props) {
        return DynamoDbClient.builder().region(Region.of(props.region())).build();
    }

    @Bean(destroyMethod = "close")
    public CloudWatchClient cloudWatchClient(StreamingProperties props) {
        return CloudWatchClient.builder().region(Region.of(props.region())).build();
    }
}
