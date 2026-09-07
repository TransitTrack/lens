<script setup lang="ts">
import { useRoutesQuery, useExploreCalendarQuery } from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import NavbarActions from '~/components/NavbarActions.vue'

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
  const firstRouteId = [...routes.value]
    .sort((a, b) =>
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
  <UDashboardPanel id="draft-editor-redirect">
    <template #header>
      <UDashboardNavbar title="Draft editor">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <NavbarActions />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div
        v-if="noRoutes"
        class="flex flex-col items-center gap-3 py-16 text-center"
      >
        <UIcon name="i-lucide-route-off" class="size-8 text-dimmed" />
        <p class="text-sm text-muted">This draft has no routes.</p>
        <UButton variant="soft" label="Back to drafts" :to="feedPath('/drafts')" />
      </div>
      <div v-else class="flex items-center gap-3 py-16 justify-center text-muted">
        <UIcon name="i-lucide-loader-circle" class="size-5 animate-spin" />
        <span class="text-sm">Loading editor…</span>
      </div>
    </template>
  </UDashboardPanel>
</template>
