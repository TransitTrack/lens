package eu.transittrack.support

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Minimal GET client for `RANDOM_PORT` end-to-end tests: asserts 200 and parses the JSON body. */
class RestGet(
    private val port: Int,
) {
    private val client = HttpClient.newHttpClient()

    operator fun invoke(path: String): JsonElement {
        val response =
            client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        check(response.statusCode() == 200) { "GET $path -> ${response.statusCode()}: ${response.body()}" }
        return Json.parseToJsonElement(response.body())
    }
}
