<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'
import UserMenu from '~/components/UserMenu.vue'
import FeedSwitcher from '~/components/FeedSwitcher.vue'
import NotificationsSlideover from '~/components/NotificationsSlideover.vue'
import NotificationsButton from '~/components/NotificationsButton.vue'
import SidebarUtilities from '~/components/SidebarUtilities.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedAlerts } from '~/composables/useFeedAlerts'
import { isInProgressRevisionStatus } from '~/utils/feedStatus'
import { docSearchItems, type DocSearchSection } from '~/utils/docs'
import { useFeedReadinessQuery } from '~~/generated/graphql'

useFeedAlerts()

const { feeds, selectedFeed, selectedFeedCode, feedIsUnknown, feedPath } = useFeeds()

const { result, loading, error } = useFeedReadinessQuery(
  () => ({ code: selectedFeedCode.value ?? '' }),
  () => ({
    enabled: !!selectedFeedCode.value,
    pollInterval: 10_000,
  }),
)
const readinessFeed = computed(() => {
  const feed = result.value?.feed ?? null
  return feed?.code === selectedFeedCode.value ? feed : null
})

const feedIsPreparing = computed(
  () =>
    error.value != null ||
    (!readinessFeed?.value?.activeRevision &&
      readinessFeed?.value?.revisions.some((revision) =>
        isInProgressRevisionStatus(revision.status),
      )),
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

const unhealthyFeedCount = computed(
  () => feeds.value.filter((f) => f.hasRealtime && !f.realtimeHealthy).length,
)

const searchOpen = ref(false)
const route = useRoute()

/** current route is `to` or a page nested under it (e.g. a detail page) */
function underPath(to: string): boolean {
  return route.path === to || route.path.startsWith(`${to}/`)
}

interface NavLeaf {
  label: string
  icon: string
  path: string
}

const REALTIME: NavLeaf[] = [
  {
    label: 'Vehicles',
    icon: 'i-lucide-bus',
    path: '/vehicles',
  },
  {
    label: 'Headway',
    icon: 'i-lucide-clock',
    path: '/headway',
  },
]

const REPORTS: NavLeaf[] = [
  {
    label: 'AVL history',
    icon: 'i-lucide-clipboard-list',
    path: '/reports/avl',
  },
  {
    label: 'Adherence by route',
    icon: 'i-lucide-route',
    path: '/reports/adherence-route',
  },
  {
    label: 'Route adherence summary',
    icon: 'i-lucide-list-tree',
    path: '/reports/adherence-routes',
  },
  {
    label: 'Adherence by trip',
    icon: 'i-lucide-git-branch',
    path: '/reports/adherence-trip',
  },
  {
    label: 'Trip adherence summary',
    icon: 'i-lucide-table',
    path: '/reports/adherence-trips',
  },
  {
    label: 'Adherence by stop',
    icon: 'i-lucide-map-pin',
    path: '/reports/adherence-stop',
  },
]

const SCHEDULE: NavLeaf[] = [
  {
    label: 'Feed',
    icon: 'i-lucide-file-text',
    path: '/explore/feed',
  },
  {
    label: 'Routes',
    icon: 'i-lucide-route',
    path: '/explore/routes',
  },
  {
    label: 'Stops',
    icon: 'i-lucide-map-pin',
    path: '/explore/stops',
  },
  {
    label: 'Trips',
    icon: 'i-lucide-git-branch',
    path: '/explore/trips',
  },
  {
    label: 'Blocks',
    icon: 'i-lucide-layers',
    path: '/explore/blocks',
  },
  {
    label: 'Calendar',
    icon: 'i-lucide-calendar-days',
    path: '/explore/calendar',
  },
]

const WORK: NavLeaf[] = [
  { label: 'Drafts', icon: 'i-lucide-file-pen-line', path: '/drafts' },
  { label: 'Compare revisions', icon: 'i-lucide-git-compare', path: '/compare' },
  { label: 'Optimization', icon: 'i-lucide-sparkles', path: '/optimize' },
]

const DOCS: NavLeaf[] = [{ label: 'Docs', icon: 'i-lucide-book-open', path: '/docs' }]

function toItems(leaves: NavLeaf[]): NavigationMenuItem[] {
  // A leaf nested under a sibling's path (/docs/live-config under /docs) takes
  // the highlight from that sibling.
  const isActive = (l: NavLeaf) =>
    underPath(feedPath(l.path)) &&
    !leaves.some((o) => o.path.startsWith(`${l.path}/`) && underPath(feedPath(o.path)))
  return leaves.map((l) => ({
    label: l.label,
    icon: l.icon,
    to: feedPath(l.path),
    active: isActive(l),
    slot: 'leaf',
  }))
}

const primaryNav = computed<NavigationMenuItem[]>(() => [
  {
    label: 'Overview',
    icon: 'i-lucide-home',
    to: feedPath(),
    active: route.path === feedPath(),
    slot: 'item',
  },
  {
    label: 'Live service',
    icon: 'i-lucide-radio',
    defaultOpen: REALTIME.some((l) => underPath(feedPath(l.path))),
    active: REALTIME.some((l) => underPath(feedPath(l.path))),
    children: toItems(REALTIME),
    slot: 'group',
  },
  {
    label: 'GTFS',
    icon: 'i-lucide-database',
    defaultOpen: SCHEDULE.some((l) => underPath(feedPath(l.path))),
    active: SCHEDULE.some((l) => underPath(feedPath(l.path))),
    children: toItems(SCHEDULE),
    slot: 'group',
  },
  {
    label: 'Changes',
    icon: 'i-lucide-file-pen-line',
    defaultOpen: WORK.some((l) => underPath(feedPath(l.path))),
    active: WORK.some((l) => underPath(feedPath(l.path))),
    children: toItems(WORK),
    slot: 'group',
  },
  {
    label: 'Reports',
    icon: 'i-lucide-chart-no-axes-combined',
    defaultOpen: REPORTS.some((l) => underPath(feedPath(l.path))),
    active: REPORTS.some((l) => underPath(feedPath(l.path))),
    children: toItems(REPORTS),
    slot: 'group',
  },
])

const navigationItems = computed(() => (feedIsBlocked.value ? [] : primaryNav.value))

// Docs sit apart from the feed's pages, pinned to the bottom of the sidebar.
const docsNav = computed<NavigationMenuItem[]>(() =>
  feedIsBlocked.value ? [] : toItems(DOCS).map((item) => ({ ...item, slot: undefined })),
)

// Docs sections join ⌘K search. The index lives in the content collection's
// WASM SQLite, so it's only loaded the first time the palette opens.
const docSections = ref<DocSearchSection[]>([])
watch(searchOpen, async (open) => {
  if (!open || docSections.value.length) return
  try {
    docSections.value = await queryCollectionSearchSections('docs')
  } catch {
    // search still works for pages and feeds
  }
})
// `content` is searched for docs items only (others don't have the field)
const searchFuse = { fuseOptions: { keys: ['content'] } }

const groups = computed(() => [
  {
    id: 'links',
    label: 'Go to',
    items: feedIsBlocked.value
      ? []
      : [...primaryNav.value.flatMap((i) => i.children ?? [i]), ...docsNav.value],
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
  {
    id: 'docs',
    label: 'Docs',
    items: feedIsBlocked.value ? [] : docSearchItems(docSections.value, feedPath('/docs')),
    // only once something is typed — otherwise every docs section lists on open
    postFilter: (term: string, items: unknown[]) => (term ? items : []),
  },
])
</script>

<template>
  <UDashboardGroup>
    <UDashboardSidebar
      collapsible
      resizable
      :ui="{
        header: 'h-auto! min-h-(--ui-header-height) shrink-0 flex items-center px-1.5 py-1.5',
      }"
    >
      <template #header="{ collapsed }">
        <div class="flex w-full flex-col gap-1.5">
          <!-- <SidebarUtilities :collapsed="collapsed" /> -->
          <div class="flex items-center gap-1">
            <FeedSwitcher class="min-w-0 flex-1" :collapsed="collapsed" />
            <UDashboardSidebarCollapse side="left" size="xs" square />
          </div>
        </div>
      </template>

      <template #default="{ collapsed }">
        <UDashboardSearchButton
          :collapsed="collapsed"
          variant="subtle"
          @click="searchOpen = true"
        />

        <USeparator class="my-1" />

        <UNavigationMenu
          :items="navigationItems"
          orientation="vertical"
          :collapsed="collapsed"
          :tooltip="collapsed"
          :popover="collapsed"
          :highlight="true"
          :ui="{ link: 'py-2 font-medium' }"
        >
          <template #leaf="{ item: leafItem, active }">
            <UIcon
              v-if="(leafItem as NavigationMenuItem).icon"
              :name="(leafItem as NavigationMenuItem).icon as string"
              class="size-4 shrink-0"
              :class="active ? 'text-primary' : 'text-muted'"
            />
            <span
              class="min-w-0 flex-1 truncate text-sm"
              :class="active ? 'font-semibold text-primary' : 'text-default'"
            >
              {{ (leafItem as NavigationMenuItem).label }}
            </span>
          </template>

          <template #group-content="groupSlot">
            <ul class="min-w-56 max-h-100 space-y-1 overflow-y-auto px-1 py-1.5">
              <li
                v-for="child in (groupSlot.item as NavigationMenuItem).children"
                :key="child.label"
              >
                <NuxtLink
                  :to="child.to"
                  class="flex items-center gap-2 rounded-md px-2.5 py-2 hover:bg-primary/15"
                  :class="{ 'bg-elevated/60': child.active }"
                  @click="(groupSlot as { close?: () => void }).close?.()"
                >
                  <UIcon
                    v-if="child.icon"
                    :name="child.icon"
                    class="size-4 shrink-0"
                    :class="child.active ? 'text-primary' : 'text-muted'"
                  />
                  <span
                    class="min-w-0 flex-1 truncate text-sm"
                    :class="child.active ? 'font-semibold text-primary' : 'text-highlighted'"
                  >
                    {{ child.label }}
                  </span>
                </NuxtLink>
              </li>
            </ul>
          </template>
        </UNavigationMenu>

        <UNavigationMenu
          :items="docsNav"
          orientation="vertical"
          :collapsed="collapsed"
          :tooltip="collapsed"
          :highlight="true"
          class="mt-auto"
          :ui="{ link: 'py-2 font-medium' }"
        />
      </template>

      <template #footer="{ collapsed }">
        <div class="flex w-full justify-center border-t border-default px-1.5 py-1.5">
          <UserMenu :collapsed="collapsed" />
        </div>
      </template>
    </UDashboardSidebar>

    <UDashboardPanel :ui="{ body: 'p-0 gap-0 sm:p-0 sm:gap-0' }">
      <template #header>
        <UDashboardNavbar toggle class="lg:hidden">
          <template #right>
            <NotificationsButton :count="unhealthyFeedCount || undefined" />
          </template>
        </UDashboardNavbar>
      </template>

      <template #body>
        <UContainer v-if="feedIsBlocked" class="flex min-h-dvh items-center justify-center py-6">
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
      </template>
    </UDashboardPanel>

    <UDashboardSearch v-model:open="searchOpen" :groups="groups" :fuse="searchFuse" />
    <NotificationsSlideover />
  </UDashboardGroup>
</template>
