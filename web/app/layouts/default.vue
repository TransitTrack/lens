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

useFeedAlerts()

const { feeds, selectedFeedCode, feedIsUnknown, feedPath } = useFeeds()
const { isNotificationsSlideoverOpen } = useDashboard()

// If the URL points at a feed that doesn't exist, fall back to the first one.
watch(feedIsUnknown, (unknown) => {
  if (unknown && feeds.value[0]) {
    navigateTo(`/${feeds.value[0].code}`, { replace: true })
  }
})

const searchOpen = ref(false)
const route = useRoute()

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
    label: 'Static',
    icon: 'i-lucide-database',
    description: 'GTFS schedule data for the feed',
    active: STATIC.some((l) => underPath(feedPath(l.path))),
    children: toItems(STATIC),
  },
])

const groups = computed(() => [
  { id: 'links', label: 'Go to', items: primaryNav.value.flatMap((i) => i.children ?? [i]) },
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
        :items="primaryNav"
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
        <UNavigationMenu :items="primaryNav" orientation="vertical" class="mx-auto" />
        <USeparator class="my-3" />
        <FeedSwitcher block />
      </template>
    </UHeader>

    <main>
      <slot v-if="selectedFeedCode" />
    </main>

    <FeedSwitcher />
    <UDashboardSearch v-model:open="searchOpen" :groups="groups" />
    <FeedDrawer />
    <NotificationsSlideover />
  </div>
</template>
