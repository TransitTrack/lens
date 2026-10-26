<script setup lang="ts">
import { useRoutesQuery, useExploreCalendarQuery } from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import AppPage from '~/components/AppPage.vue'
import PageState from '~/components/PageState.vue'

const route = useRoute()
const { selectedFeedCode, feedPath } = useFeeds()

const draftId = computed(() => String(route.params.draftId))

const { result: routesResult, loading: routesLoading } = useRoutesQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const { result: calendarResult, loading: calendarLoading } = useExploreCalendarQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

const routes = computed(() => routesResult.value?.routes ?? [])
const services = computed(() => {
  const ids = new Set<string>()
  for (const c of calendarResult.value?.calendars ?? []) ids.add(c.serviceId)
  for (const d of calendarResult.value?.calendarDates ?? []) ids.add(d.serviceId)
  return [...ids].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
})

const ready = computed(() => !routesLoading.value && !calendarLoading.value)
const noRoutes = computed(() => ready.value && routes.value.length === 0)

let redirected = false
watchEffect(() => {
  if (redirected || !ready.value || noRoutes.value) return
  const firstRouteId = [...routes.value].sort((a, b) =>
    (a.routeShortName ?? a.routeId).localeCompare(b.routeShortName ?? b.routeId, undefined, {
      numeric: true,
    }),
  )[0]!.routeId
  const service = services.value[0]
  redirected = true
  navigateTo(
    feedPath('/drafts/' + draftId.value + '/' + encodeURIComponent(firstRouteId)) +
      '?dir=0' +
      (service ? '&service=' + encodeURIComponent(service) : ''),
    { replace: true },
  )
})
</script>

<template>
  <AppPage title="Draft editor">
    <PageState
      v-if="noRoutes"
      state="empty"
      title="This draft has no routes"
      description="Return to drafts to select or create a schedule draft with route data."
      icon="i-lucide-route-off"
    >
      <template #action>
        <UButton variant="soft" label="Back to drafts" :to="feedPath('/drafts')" />
      </template>
    </PageState>
    <PageState v-else state="loading" title="Loading editor" />
  </AppPage>
</template>
