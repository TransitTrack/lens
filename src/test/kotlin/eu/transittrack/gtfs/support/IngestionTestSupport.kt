package eu.transittrack.gtfs.support

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

import eu.transittrack.gtfs.ingest.IngestionPostProcessor

/** An [ObjectProvider] yielding [pp] on every `stream()` call — for wiring `IngestionService` in tests. */
fun postProcessors(vararg pp: IngestionPostProcessor): ObjectProvider<IngestionPostProcessor> {
    val provider = mock<ObjectProvider<IngestionPostProcessor>>()
    whenever(provider.stream()).thenAnswer { pp.toList().stream() }
    whenever(provider.iterator()).thenAnswer { pp.toList().iterator() }
    return provider
}
