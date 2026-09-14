package eu.transittrack

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

import eu.transittrack.support.PostgresPerMethodTest

@SpringBootTest
class ApplicationTests : PostgresPerMethodTest() {
    @Test
    fun contextLoads() {
    }
}
