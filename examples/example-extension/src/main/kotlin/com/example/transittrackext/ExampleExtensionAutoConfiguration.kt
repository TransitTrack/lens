package com.example.transittrackext

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ExampleExtensionAutoConfiguration {
    @Bean
    fun exampleDecoder(): ExampleDecoder = ExampleDecoder()

    @Bean
    fun exampleValidator(): ExampleValidator = ExampleValidator()
}
