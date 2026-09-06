<script setup lang="ts">
import { useFeedsQuery } from '~~/generated/graphql'

definePageMeta({ layout: false })

// The app is feed-scoped: every real page lives under /:feedCode. This entry
// point resolves a feed (last-used cookie, else the first one) and redirects.
const lastFeed = useCookie<string | null>('tt-feed', { default: () => null })
const { result } = useFeedsQuery()

const target = computed(() => {
  const feeds = result.value?.feeds ?? []
  if (feeds.length === 0) return null
  const preferred = feeds.find((f) => f.code === lastFeed.value)
  return (preferred ?? feeds[0])!.code
})

watchEffect(() => {
  if (target.value) navigateTo(`/${target.value}`, { replace: true })
})
</script>

<template>
  <div class="flex h-svh items-center justify-center">
    <div class="flex items-center gap-2 text-sm text-muted">
      <UIcon name="i-lucide-loader-circle" class="size-4 animate-spin" />
      Loading feeds…
    </div>
  </div>
</template>
