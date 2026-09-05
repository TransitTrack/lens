<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'
import FeedPicker from '../components/FeedPicker.vue'

const navItems: NavigationMenuItem[] = [
  { label: 'Vehicles', to: '/', icon: 'i-lucide-bus' },
  { label: 'Headway', to: '/headway', icon: 'i-lucide-clock' },
]
</script>

<template>
  <div class="flex h-screen">
    <USidebar collapsible="icon" title="Feed">
      <FeedPicker />
    </USidebar>

    <div class="flex min-w-0 flex-1 flex-col">
      <UHeader>
        <template #left>
          <NuxtLink to="/" class="flex items-center gap-2 font-semibold">
            <UIcon name="i-lucide-map-pinned" class="size-5 text-primary" />
            TransitTrack
          </NuxtLink>
        </template>

        <UNavigationMenu :items="navItems" />

        <template #right>
          <UColorModeButton />
        </template>
      </UHeader>

      <div class="min-h-0 flex-1">
        <slot />
      </div>
    </div>
  </div>
</template>
