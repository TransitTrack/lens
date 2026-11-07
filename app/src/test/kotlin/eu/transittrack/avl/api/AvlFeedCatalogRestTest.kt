package eu.transittrack.avl.api

import kotlin.test.Test

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import eu.transittrack.avl.read.AvlReadService
import eu.transittrack.rest.generated.model.AvlAssignmentMode
import eu.transittrack.rest.generated.model.AvlFeed
import eu.transittrack.rest.generated.model.AvlFormat

class AvlFeedCatalogRestTest {
    @Test
    fun `feed catalog applies pagination and returns total count`() {
        val service = mock<AvlReadService>()
        val first = AvlFeed("a", "A", "g", AvlFormat.GTFS_RT, 15, AvlAssignmentMode.FULL_INFERENCE, emptyList(), true)
        val second = first.copy(code = "b", name = "B")
        whenever(service.avlFeeds()).thenReturn(listOf(first, second))
        val mvc = MockMvcBuilders.standaloneSetup(AvlFeedCatalogRestController(service)).build()
        mvc
            .perform(get("/api/v1/avl-feeds").param("offset", "1").param("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(jsonPath("$[0].code").value("b"))
            .andExpect(jsonPath("$[1]").doesNotExist())
    }

    @Test
    fun `invalid pagination produces problem response`() {
        val mvc = MockMvcBuilders.standaloneSetup(AvlFeedCatalogRestController(mock<AvlReadService>())).build()
        mvc
            .perform(get("/api/v1/avl-feeds").param("limit", "501"))
            .andExpect(status().isBadRequest)
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .content()
                    .contentTypeCompatibleWith("application/problem+json"),
            ).andExpect(jsonPath("$.status").value(400))
    }
}
