package com.groom.order.common.config

import com.groom.order.configuration.jpa.DataSourceType
import com.groom.order.configuration.jpa.DynamicRoutingDataSource
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

@Profile("test")
@Configuration
class TestDataSourceConfig {
    @Bean
    fun masterDataSource(): HikariDataSource =
        DataSourceBuilder
            .create()
            .type(HikariDataSource::class.java)
            .url(TestDockerComposeContainer.getMasterJdbcUrl())
            .driverClassName("org.postgresql.Driver")
            .username(TestDockerComposeContainer.POSTGRESQL_USERNAME)
            .password(TestDockerComposeContainer.POSTGRESQL_PASSWORD)
            .build()

    @Bean
    fun replicaDataSource(): HikariDataSource =
        DataSourceBuilder
            .create()
            .type(HikariDataSource::class.java)
            .url(TestDockerComposeContainer.getReplicaJdbcUrl())
            .driverClassName("org.postgresql.Driver")
            .username(TestDockerComposeContainer.POSTGRESQL_USERNAME)
            .password(TestDockerComposeContainer.POSTGRESQL_PASSWORD)
            .build()

    @Bean
    fun routingDataSource(
        @Qualifier("masterDataSource") masterDataSource: DataSource,
        @Qualifier("replicaDataSource") replicaDataSource: DataSource,
    ): DataSource {
        val dynamicRoutingDataSource = DynamicRoutingDataSource()
        val targetDataSources: Map<Any, Any> =
            mapOf(
                DataSourceType.MASTER to masterDataSource,
                DataSourceType.REPLICA to replicaDataSource,
            )
        dynamicRoutingDataSource.setTargetDataSources(targetDataSources)
        dynamicRoutingDataSource.setDefaultTargetDataSource(masterDataSource)

        return dynamicRoutingDataSource
    }

    @Primary
    @Bean
    fun dataSource(
        @Qualifier("routingDataSource") dataSource: DataSource,
    ): DataSource = LazyConnectionDataSourceProxy(dataSource)

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory =
        LettuceConnectionFactory(
            TestDockerComposeContainer.getRedisHost(),
            TestDockerComposeContainer.getRedisMappedPort(),
        )
}
