package eu.transittrack.rest

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort

import eu.transittrack.support.PostgresPerClassTest

/** The REST contract is served next to the API it describes, for the web app's API reference. */
@SpringBootTest(classes = [eu.transittrack.Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiSpecResourceTest(
    @LocalServerPort val port: Int,
) : PostgresPerClassTest() {
    @Test
    fun `serves the OpenAPI contract at api v1 openapi yaml`() {
        val response =
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/openapi.yaml")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("operationId: routeStops")
    }
}
