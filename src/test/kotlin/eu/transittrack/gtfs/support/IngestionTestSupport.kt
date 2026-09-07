package eu.transittrack.gtfs.support

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider

import eu.transittrack.gtfs.ingest.IngestionPostProcessor
import eu.transittrack.gtfs.revision.GtfsRevisionRepository
import eu.transittrack.gtfs.revision.RevisionService
import eu.transittrack.schedule.derive.DerivationContext
import eu.transittrack.schedule.derive.DerivationService
import eu.transittrack.schedule.derive.DerivedGtfsWriter

/** An [ObjectProvider] yielding [pp] on every `stream()` call — for wiring `IngestionService` in tests. */
fun postProcessors(vararg pp: IngestionPostProcessor): ObjectProvider<IngestionPostProcessor> {
    val provider = mock<ObjectProvider<IngestionPostProcessor>>()
    whenever(provider.stream()).thenAnswer { pp.toList().stream() }
    whenever(provider.iterator()).thenAnswer { pp.toList().iterator() }
    return provider
}

/** An [ObjectProvider] whose `ifAvailable` yields [value] (or null). */
private inline fun <reified T : Any> singletonProvider(value: T?): ObjectProvider<T> {
    val provider = mock<ObjectProvider<T>>()
    whenever(provider.ifAvailable).thenReturn(value)
    return provider
}

/** A real [DerivationService] over [pp] — for wiring `IngestionService` in tests. */
fun derivationService(
    revisionService: RevisionService,
    revisions: GtfsRevisionRepository,
    vararg pp: IngestionPostProcessor,
    context: DerivationContext? = null,
    derivedGtfsWriter: DerivedGtfsWriter? = null,
): DerivationService =
    DerivationService(
        postProcessors(*pp),
        singletonProvider(context),
        singletonProvider(derivedGtfsWriter),
        revisionService,
        revisions,
    )
