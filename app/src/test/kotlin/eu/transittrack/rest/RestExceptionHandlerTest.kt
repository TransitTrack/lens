package eu.transittrack.rest

import kotlin.test.Test

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

import eu.transittrack.gtfs.feed.FeedConflictException
import eu.transittrack.gtfs.feed.FeedNotFoundException

class RestExceptionHandlerTest {
    @RestController
    class Throwing {
        @GetMapping("/boom/{kind}")
        fun boom(
            @PathVariable kind: String,
        ): String =
            when (kind) {
                "missing" -> throw FeedNotFoundException("f")
                "taken" -> throw FeedConflictException("feed 'f' exists")
                "state" -> throw IllegalStateException("feed 'f' has no active revision")
                else -> throw IllegalArgumentException("no feed 'f'")
            }
    }

    private val mvc = MockMvcBuilders.standaloneSetup(Throwing()).setControllerAdvice(RestExceptionHandler()).build()

    @Test
    fun `unknown feed is a 404 problem`() {
        mvc.perform(get("/boom/missing")).andExpect(status().isNotFound).andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `conflicts are 409`() {
        mvc.perform(get("/boom/taken")).andExpect(status().isConflict)
    }

    @Test
    fun `bad input and missing state are 400 with the reason`() {
        mvc
            .perform(get("/boom/state"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("feed 'f' has no active revision"))
        mvc.perform(get("/boom/arg")).andExpect(status().isBadRequest)
    }
}
