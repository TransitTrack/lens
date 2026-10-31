package eu.transittrack.config

import kotlin.test.Test

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.springframework.boot.actuate.autoconfigure.context.properties.ConfigurationPropertiesReportEndpointAutoConfiguration
import org.springframework.boot.actuate.context.properties.ConfigurationPropertiesReportEndpoint
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

import eu.transittrack.ActuatorConfiguration
import eu.transittrack.FeedsProperties

class ActuatorConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesReportEndpointAutoConfiguration::class.java))
            .withUserConfiguration(ActuatorConfiguration::class.java, TestConfig::class.java)
            .withPropertyValues(
                "management.endpoints.web.exposure.include=configprops",
                "management.endpoint.configprops.show-values=always",
                "transittrack.feed.feeds[0].code=demo",
                "transittrack.feed.feeds[0].name=Demo",
                "transittrack.feed.feeds[0].url=https://example.org/gtfs.zip",
                "transittrack.feed.feeds[0].avl.url=https://user:hunter2@example.org/vp.pb",
                "transittrack.feed.feeds[0].avl.headers.Authorization=Bearer abc",
                "transittrack.feed.feeds[1].code=keyed",
                "transittrack.feed.feeds[1].name=Keyed",
                "transittrack.feed.feeds[1].url=https://example.org/gtfs.zip?lang=en&api_key=s3cret",
                "transittrack.feed.feeds[1].avl.url=https://example.org/api/v1/key/f00d/agency/4/vp",
                "spring.datasource.password=pw",
            )

    @Configuration
    @EnableConfigurationProperties(FeedsProperties::class, DataSourceLike::class)
    class TestConfig

    @org.springframework.boot.context.properties.ConfigurationProperties("spring.datasource")
    data class DataSourceLike(
        val url: String = "jdbc:postgresql://localhost/db",
        val password: String? = null,
    )

    private fun feed(
        props: Map<String, Any?>,
        index: Int,
    ): Map<*, *> = (props["feeds"] as List<*>)[index] as Map<*, *>

    @Test
    fun `configprops shows plain values but masks credentials, feed headers and secrets inside urls`() {
        runner.run { ctx ->
            val beans =
                ctx
                    .getBean(ConfigurationPropertiesReportEndpoint::class.java)
                    .configurationProperties()
                    .contexts.values
                    .single()!!
                    .beans.values
            val feeds = beans.single { it.prefix == "transittrack.feed" }.properties
            val datasource = beans.single { it.prefix == "spring.datasource" }.properties

            assertThat(feed(feeds, 0)["code"]).isEqualTo("demo")
            assertThat(feed(feeds, 0)["url"]).isEqualTo("https://example.org/gtfs.zip")
            val avl = feed(feeds, 0)["avl"] as Map<*, *>
            assertThat(avl["url"]).isEqualTo("https://user:$MASK@example.org/vp.pb")
            assertThat((avl["headers"] as Map<*, *>)["Authorization"]).isEqualTo(MASK)
            assertThat(feed(feeds, 1)["url"]).isEqualTo("https://example.org/gtfs.zip?lang=en&api_key=$MASK")
            assertThat((feed(feeds, 1)["avl"] as Map<*, *>)["url"])
                .isEqualTo("https://example.org/api/v1/key/$MASK/agency/4/vp")
            assertThat(datasource["url"]).isEqualTo("jdbc:postgresql://localhost/db")
            assertThat(datasource["password"]).isEqualTo(MASK)
        }
    }

    private companion object {
        const val MASK = "******"
    }
}
