<script setup lang="ts">
import PollControl from './PollControl.vue'

defineProps<{ updatedAt?: string | number | null }>()
</script>

<template>
  <PollControl :updated-at="updatedAt" />
</template>
