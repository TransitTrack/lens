package eu.transittrack.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class AsyncConfiguration

@Configuration
@EntityScan(basePackages = ["eu.transittrack"])
@EnableJpaRepositories(basePackages = ["eu.transittrack"])
class DatabaseConfiguration