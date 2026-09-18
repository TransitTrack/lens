package eu.transittrack.extension

import kotlin.test.Test

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

import eu.transittrack.avl.ingest.AvlFeedDecoder

/** Only passes when run with `-Dloader.path` pointing at the built example-extension jar — see the
 * `extensionLoadingTest` Gradle task, which is excluded from the default `test` task. */
@SpringBootTest(classes = [eu.transittrack.Application::class])
class ExtensionLoadingTest(
    @Autowired val decoders: List<AvlFeedDecoder>,
) {
    @Test
    fun `an extension jar on loader path contributes its beans`() {
        assert(decoders.any { it.javaClass.name == "com.example.transittrackext.ExampleDecoder" })
    }
}
