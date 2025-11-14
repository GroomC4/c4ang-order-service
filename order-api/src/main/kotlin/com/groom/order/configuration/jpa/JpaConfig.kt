package com.groom.order.configuration.jpa

import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.Database
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Profile("!test")
@Configuration
@EnableJpaRepositories(
    basePackages = ["com.groom.order"],
    repositoryImplementationPostfix = "RepositoryImpl",
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager",
)
class JpaConfig {
    @Bean("transactionManager")
    fun transactionManager(): PlatformTransactionManager = JpaTransactionManager()

    @Primary
    @Bean("entityManagerFactory")
    fun entityManager(
        dataSource: DataSource,
        jpaProperties: JpaProperties,
        hibernateProperties: HibernateProperties,
    ): LocalContainerEntityManagerFactoryBean =
        LocalContainerEntityManagerFactoryBean().apply {
            this.dataSource = dataSource
            this.setPackagesToScan("com.groom.customer")
            this.jpaVendorAdapter =
                HibernateJpaVendorAdapter().apply {
                    setDatabase(Database.POSTGRESQL)
                    setGenerateDdl(false)
                }
            // application.yml의 모든 JPA/Hibernate 설정을 자동으로 적용
            val vendorProperties =
                hibernateProperties.determineHibernateProperties(
                    jpaProperties.properties,
                    HibernateSettings(),
                )
            this.setJpaPropertyMap(
                buildMap {
                    putAll(vendorProperties)
                    // 추가 속성이 필요하면 여기에 추가
                    put("hibernate.jdbc.lob.non_contextual_creation", true)
                },
            )
        }
}
