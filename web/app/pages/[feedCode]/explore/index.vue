<script setup lang="ts">
import { useFeeds } from '~/composables/useFeeds'

const { feedPath } = useFeeds()
await navigateTo(feedPath('/explore/routes'), { replace: true })
</script>

<template>
  <div />
</template>
