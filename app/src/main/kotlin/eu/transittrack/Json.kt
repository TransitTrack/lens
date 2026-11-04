package eu.transittrack

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

val ProjectJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

@Configuration
class JsonConfiguration {
    @Bean
    fun json(): Json = ProjectJson
}
