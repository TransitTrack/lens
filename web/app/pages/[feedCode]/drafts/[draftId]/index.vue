<script setup lang="ts">
import { useQuery } from '@pinia/colada'
import { routesQuery } from '~/composables/api/queries/gtfs/routesQuery'
import { calendarsQuery } from '~/composables/api/queries/gtfs/calendarsQuery'
import { calendarDatesQuery } from '~/composables/api/queries/gtfs/calendarDatesQuery'

import { useFeeds } from '~/composables/useFeeds'
import AppPage from '~/components/shell/AppPage.vue'
import PageState from '~/components/shell/PageState.vue'

const route = useRoute()
const { selectedFeedCode, feedPath } = useFeeds()

const draftId = computed(() => String(route.params.draftId))

// The pickers read the feed's active revision, as the editor page does.
const enabled = computed(() => !!selectedFeedCode.value)
const routesRead = useQuery(() => ({
  ...routesQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: enabled.value,
}))
const calendarsRead = useQuery(() => ({
  ...calendarsQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: enabled.value,
}))
const calendarDatesRead = useQuery(() => ({
  ...calendarDatesQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: enabled.value,
}))

const routes = computed(() => routesRead.data.value ?? [])
const services = computed(() => {
  const ids = new Set<string>()
  for (const c of calendarsRead.data.value ?? []) ids.add(c.serviceId)
  for (const d of calendarDatesRead.data.value ?? []) ids.add(d.serviceId)
  return [...ids].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
})

// Settled = every read has an answer (data or error) and none is in flight. Checking only
// `isLoading` would redirect before the first fetch starts (it starts on mount).
const reads = [routesRead, calendarsRead, calendarDatesRead]
const ready = computed(() => reads.every((r) => r.status.value !== 'pending' && !r.isLoading.value))
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
  <AppPage :title="$t('drafts.editorTitle')">
    <PageState
      v-if="noRoutes"
      state="empty"
      :title="$t('drafts.noRoutesTitle')"
      :description="$t('drafts.noRoutesBody')"
      icon="i-lucide-route-off"
    >
      <template #action>
        <UButton variant="soft" :label="$t('drafts.backToDrafts')" :to="feedPath('/drafts')" />
      </template>
    </PageState>
    <PageState v-else state="loading" :title="$t('drafts.loadingEditor')" />
  </AppPage>
</template>
