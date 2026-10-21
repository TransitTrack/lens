<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'
import AppLogo from '~/components/AppLogo.vue'
import UserMenu from '~/components/UserMenu.vue'
import FeedSwitcher from '~/components/FeedSwitcher.vue'
import FeedDrawer from '~/components/FeedDrawer.vue'
import NotificationsSlideover from '~/components/NotificationsSlideover.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedAlerts } from '~/composables/useFeedAlerts'
import { useDashboard } from '~/composables/useDashboard'
import { isInProgressRevisionStatus } from '~/utils/feedStatus'
import { useFeedReadinessQuery } from '~~/generated/graphql'

useFeedAlerts()

const { feeds, selectedFeed, selectedFeedCode, feedIsUnknown, feedPath } = useFeeds()
const { isNotificationsSlideoverOpen } = useDashboard()

const { result, loading, error } = useFeedReadinessQuery(
  () => ({ code: selectedFeedCode.value ?? '' }),
  () => ({
    enabled: !!selectedFeedCode.value,
    pollInterval: 3_000,
  }),
)
const readinessFeed = computed(() => {
  const feed = result.value?.feed ?? null
  return feed?.code === selectedFeedCode.value ? feed : null
})

const feedIsPreparing = computed(
  () =>
    error.value != null ||
    !readinessFeed?.value?.activeRevision &&
      readinessFeed?.value?.revisions.some((revision) =>
        isInProgressRevisionStatus(revision.status),
      ),
)
const feedIsLoading = computed(
  () => !!selectedFeedCode.value && loading.value && !readinessFeed.value,
)
const feedIsBlocked = computed(() => {
  return feedIsLoading.value || feedIsPreparing.value
})
const preparingFeedName = computed(
  () => readinessFeed.value?.name ?? selectedFeed.value?.name ?? 'feed',
)

// If the URL points at a feed that doesn't exist, fall back to the first one.
watch(feedIsUnknown, (unknown) => {
  if (unknown && feeds.value[0]) {
    navigateTo(`/${feeds.value[0].code}`, { replace: true })
  }
})

const searchOpen = ref(false)
const route = useRoute()
const currentTime = ref(new Date())
const timeMounted = ref(false)
let clockTimer: ReturnType<typeof setInterval> | undefined

const formattedClock = computed(() => {
  const date = currentTime.value
  const twoDigits = (value: number) => String(value).padStart(2, '0')
  return `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}:${twoDigits(date.getSeconds())}`
})

const formattedCalendarDate = computed(() => {
  const date = currentTime.value
  const weekday = new Intl.DateTimeFormat('en-GB', { weekday: 'short' }).format(date)
  const twoDigits = (value: number) => String(value).padStart(2, '0')
  return `${weekday}, ${date.getFullYear()}.${twoDigits(date.getMonth() + 1)}.${twoDigits(date.getDate())}`
})

onMounted(() => {
  timeMounted.value = true
  currentTime.value = new Date()
  clockTimer = setInterval(() => {
    currentTime.value = new Date()
  }, 1000)
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
})

/** current route is `to` or a page nested under it (e.g. a detail page) */
function underPath(to: string): boolean {
  return route.path === to || route.path.startsWith(`${to}/`)
}

interface NavLeaf {
  label: string
  icon: string
  description: string
  path: string
}

const REALTIME: NavLeaf[] = [
  {
    label: 'Vehicles',
    icon: 'i-lucide-bus',
    description: 'Live vehicle positions and trip matching',
    path: '/vehicles',
  },
  {
    label: 'Headway',
    icon: 'i-lucide-clock',
    description: 'Service gaps and wait times at a stop',
    path: '/headway',
  },
]

const REPORTS: NavLeaf[] = [
  {
    label: 'AVL Report',
    icon: 'i-lucide-clipboard-list',
    description: 'View historical AVL reports for a vehicle',
    path: '/reports/avl',
  },
  {
    label: 'Schedule Adherence for a Route',
    icon: 'i-lucide-route',
    description: 'Per-stop adherence for one route over a date range',
    path: '/reports/adherence-route',
  },
]

const STATIC: NavLeaf[] = [
  {
    label: 'Routes',
    icon: 'i-lucide-route',
    description: 'Browse routes, filter by type and agency',
    path: '/explore/routes',
  },
  {
    label: 'Stops',
    icon: 'i-lucide-map-pin',
    description: 'Every stop on the map with its patterns',
    path: '/explore/stops',
  },
  {
    label: 'Trips',
    icon: 'i-lucide-git-branch',
    description: 'Scheduled trips with start/end times and filters',
    path: '/explore/trips',
  },
  {
    label: 'Blocks',
    icon: 'i-lucide-layers',
    description: 'Vehicle blocks and peak concurrency',
    path: '/explore/blocks',
  },
  {
    label: 'Calendar',
    icon: 'i-lucide-calendar-days',
    description: 'Service calendars and date exceptions',
    path: '/explore/calendar',
  },
  {
    label: 'Feed',
    icon: 'i-lucide-file-text',
    description: 'Feed metadata, revisions and quality report',
    path: '/explore/feed',
  },
  {
    label: 'Drafts',
    icon: 'i-lucide-file-pen-line',
    description: 'Edit and activate schedule drafts',
    path: '/drafts',
  },
]

function toItems(leaves: NavLeaf[]): NavigationMenuItem[] {
  return leaves.map((l) => ({
    label: l.label,
    icon: l.icon,
    description: l.description,
    to: feedPath(l.path),
    active: underPath(feedPath(l.path)),
  }))
}

const primaryNav = computed<NavigationMenuItem[]>(() => [
  {
    label: 'Home',
    icon: 'i-lucide-layout-dashboard',
    description: 'Fleet overview and live map',
    to: feedPath(),
    exact: true,
    active: route.path === feedPath(),
  },
  {
    label: 'Optimize',
    icon: 'i-lucide-sparkles',
    description: 'AVL-informed schedule optimization',
    to: feedPath('/optimize'),
    active: underPath(feedPath('/optimize')),
  },
  {
    label: 'Realtime',
    icon: 'i-lucide-radio',
    description: 'Live data from the AVL feed',
    active: REALTIME.some((l) => underPath(feedPath(l.path))),
    children: toItems(REALTIME),
  },
  {
    label: 'Reports',
    icon: 'i-lucide-chart-no-axes-combined',
    description: 'Historical operational reports',
    active: REPORTS.some((l) => underPath(feedPath(l.path))),
    children: toItems(REPORTS),
  },
  {
    label: 'Static',
    icon: 'i-lucide-database',
    description: 'GTFS schedule data for the feed',
    active: STATIC.some((l) => underPath(feedPath(l.path))),
    children: toItems(STATIC),
  },
])

const navigationItems = computed(() => (feedIsBlocked.value ? [] : primaryNav.value))

const groups = computed(() => [
  {
    id: 'links',
    label: 'Go to',
    items: feedIsBlocked.value ? [] : primaryNav.value.flatMap((i) => i.children ?? [i]),
  },
  {
    id: 'feeds',
    label: 'Switch feed',
    items: feeds.value.map((f) => ({
      id: `feed-${f.code}`,
      label: f.name,
      suffix: f.code,
      icon: 'i-lucide-rss',
      to: `/${f.code}`,
    })),
  },
])
</script>

<template>
  <div>
    <UHeader mode="drawer" :ui="{ center: 'flex-1 justify-center' }">
      <template #left>
        <NuxtLink :to="feedPath()" aria-label="Home">
          <AppLogo :font-size="16" :icon-only="false" />
        </NuxtLink>
      </template>

      <UNavigationMenu
        :items="navigationItems"
        :ui="{
          link: 'text-sm px-2 py-2 gap-2',
          linkLeadingIcon: 'size-4',
          childList: 'sm:grid-cols-1 gap-2',
          childLink: 'p-2 gap-2 data-[active]:before:bg-primary/10 data-[active]:!text-primary',
          childLinkIcon: 'size-5 mt-1 group-data-[active]:text-primary',
          childLinkLabel: 'group-data-[active]:text-primary',
          childLinkDescription: 'text-sm group-data-[active]:text-primary/70',
        }"
      />

      <template #right>
        <time
          v-if="timeMounted"
          :datetime="currentTime.toISOString()"
          class="hidden items-center gap-2 rounded-md bg-elevated px-3 py-1 shadow-sm lg:flex"
          aria-label="Current local date and time"
        >
          <span class="text-xl font-bold tracking-tight tabular-nums text-highlighted">
            {{ formattedClock }}
          </span>
          <span class="border-l border-default pl-2 text-xs font-medium text-muted">
            {{ formattedCalendarDate }}
          </span>
        </time>
        <UButton
          color="neutral"
          variant="ghost"
          square
          aria-label="Feed status"
          @click="isNotificationsSlideoverOpen = true"
        >
          <UChip color="error" inset>
            <UIcon name="i-lucide-bell" class="size-5 shrink-0" />
          </UChip>
        </UButton>
        <UserMenu />
      </template>

      <template #content>
        <UNavigationMenu :items="navigationItems" orientation="vertical" class="mx-auto" />
        <USeparator class="my-3" />
        <FeedSwitcher block />
      </template>
    </UHeader>

    <main>
      <UContainer
        v-if="feedIsBlocked"
        class="flex min-h-[calc(100vh-var(--ui-header-height))] items-center justify-center py-6"
      >
        <section class="w-full max-w-md space-y-3" aria-live="polite">
          <div class="flex items-center gap-2 text-sm font-medium text-muted">
            <UIcon name="i-lucide-loader-circle" class="size-4 animate-spin" />
            Preparing {{ preparingFeedName }}
          </div>
          <UProgress color="primary" />
          <p class="text-sm text-dimmed">
            <template v-if="feedIsPreparing">
              Preparing the first dataset. Browsing will unlock when it is ready.
            </template>
            <template v-else>Loading feed status…</template>
          </p>
        </section>
      </UContainer>
      <slot v-else-if="selectedFeedCode" />
    </main>

    <FeedSwitcher />
    <UDashboardSearch v-model:open="searchOpen" :groups="groups" />
    <FeedDrawer />
    <NotificationsSlideover />
  </div>
</template>
