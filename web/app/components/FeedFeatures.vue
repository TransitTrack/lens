<script setup lang="ts">
import type {DerivedFeature} from '~/utils/feedFeatures'

const props = defineProps<{ features: DerivedFeature[] }>()

const present = computed(() => props.features.filter((f) => f.present))
const absent = computed(() => props.features.filter((f) => !f.present))
</script>

<template>
  <UCard :ui="{ body: 'flex flex-col gap-3' }">
    <div class="text-sm font-medium text-muted">Features</div>

    <div v-if="present.length" class="flex flex-wrap gap-1.5">
      <UBadge
        v-for="f in present"
        :key="f.key"
        color="success"
        variant="subtle"
        icon="i-lucide-check"
      >
        {{ f.label }}
      </UBadge>
    </div>
    <p v-else class="text-sm text-dimmed">No optional features detected.</p>

    <div v-if="absent.length" class="flex flex-wrap gap-1.5">
      <UBadge
        v-for="f in absent"
        :key="f.key"
        color="neutral"
        variant="subtle"
        class="opacity-60"
      >
        {{ f.label }}
      </UBadge>
    </div>
  </UCard>
</template>
