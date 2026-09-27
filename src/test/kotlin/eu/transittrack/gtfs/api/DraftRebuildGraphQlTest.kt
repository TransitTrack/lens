package eu.transittrack.gtfs.api

import kotlin.test.Test

import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean

import eu.transittrack.gtfs.draft.DraftJob
import eu.transittrack.gtfs.draft.DraftJobService
import eu.transittrack.gtfs.draft.DraftService
import eu.transittrack.gtfs.draft.edit.DraftEditService
import eu.transittrack.gtfs.feed.GtfsFeedService

@GraphQlTest
@Import(GraphQlConfiguration::class, DraftController::class, DraftMapper::class, GtfsDtoMapper::class)
class DraftRebuildGraphQlTest(
    @Autowired val tester: GraphQlTester,
) {
    @MockitoBean lateinit var draftService: DraftService

    @MockitoBean lateinit var jobs: DraftJobService

    @MockitoBean lateinit var editService: DraftEditService

    @MockitoBean lateinit var feedService: GtfsFeedService

    @Test
    fun `rebuildDraft returns a RUNNING job`() {
        whenever(jobs.submitRebuild(9)).thenReturn(DraftJob("j1", DraftJob.State.RUNNING, DraftJob.Phase.DERIVING))
        tester
            .document("""mutation { rebuildDraft(id:"9") { id state phase } }""")
            .execute()
            .path("rebuildDraft.state")
            .entity(String::class.java)
            .isEqualTo("RUNNING")
            .path("rebuildDraft.phase")
            .entity(String::class.java)
            .isEqualTo("DERIVING")
    }

    @Test
    fun `draftJob looks up by id`() {
        whenever(jobs.get("j1")).thenReturn(DraftJob("j1", DraftJob.State.SUCCEEDED, DraftJob.Phase.DONE))
        tester
            .document("""{ draftJob(jobId:"j1") { state phase } }""")
            .execute()
            .path("draftJob.state")
            .entity(String::class.java)
            .isEqualTo("SUCCEEDED")
    }
}
