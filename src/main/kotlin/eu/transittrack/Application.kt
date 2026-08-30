package eu.transittrack.explorer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["eu.transittrack"])
@ConfigurationPropertiesScan("eu.transittrack")
@EnableScheduling
@EntityScan(basePackages = ["eu.transittrack"])
@EnableJpaRepositories(basePackages = ["eu.transittrack"])
class Application

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}
