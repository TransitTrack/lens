package eu.transittrack

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["eu.transittrack"])
@ConfigurationPropertiesScan("eu.transittrack")
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
