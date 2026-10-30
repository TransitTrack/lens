<script setup lang="ts">
import type { MapToolMode, MapTools } from '~/composables/useMapTools'

export interface MapToolbarLayer {
  id: string
  label: string
  enabled: boolean
}

type ToolboxSection = 'layers' | 'filters' | 'shapes' | 'measure' | 'colors'

const props = withDefaults(
  defineProps<{
    tools: MapTools
    layers?: MapToolbarLayer[]
    filtersAvailable?: boolean
    routeColorsAvailable?: boolean
    routeColorMode?: 'official' | 'distinct'
  }>(),
  { layers: () => [], filtersAvailable: false, routeColorsAvailable: false, routeColorMode: 'official' },
)
const emit = defineEmits<{
  (e: 'update:layer', value: { id: string; enabled: boolean }): void
  (e: 'update:routeColorMode', value: 'official' | 'distinct'): void
}>()

const hovered = ref(false)
const activeSection = ref<ToolboxSection | null>(null)
const expanded = computed(() => hovered.value || activeSection.value !== null)

const sections: { id: ToolboxSection; label: string; icon: string }[] = [
  { id: 'layers', label: 'Layers', icon: 'i-lucide-layers-3' },
  { id: 'filters', label: 'Filters', icon: 'i-lucide-filter' },
  { id: 'shapes', label: 'Shapes', icon: 'i-lucide-mouse-pointer-2' },
  { id: 'measure', label: 'Measure', icon: 'i-lucide-ruler' },
  { id: 'colors', label: 'Route colors', icon: 'i-lucide-palette' },
]

const shapeTools: { id: Extract<MapToolMode, 'line' | 'polygon' | 'note'>; label: string; icon: string }[] = [
  { id: 'line', label: 'Line', icon: 'i-lucide-spline' },
  { id: 'polygon', label: 'Area shape', icon: 'i-lucide-pentagon' },
  { id: 'note', label: 'Note', icon: 'i-lucide-message-square-plus' },
]
const measureTools: { id: Extract<MapToolMode, 'distance' | 'area' | 'radius'>; label: string; icon: string }[] = [
  { id: 'distance', label: 'Distance', icon: 'i-lucide-ruler' },
  { id: 'area', label: 'Area', icon: 'i-lucide-maximize-2' },
  { id: 'radius', label: 'Radius', icon: 'i-lucide-circle-dot' },
]

function toggleSection(section: ToolboxSection) {
  activeSection.value = activeSection.value === section ? null : section
}

function chooseTool(tool: MapToolMode) {
  props.tools.selectTool(tool)
}

function closeTools() {
  props.tools.deactivate()
  activeSection.value = null
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  closeTools()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div
    class="absolute left-3 top-3 z-10 text-sm"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
    @focusin="hovered = true"
    @focusout="!activeSection && (hovered = false)"
  >
    <div
      class="flex items-center gap-1 rounded-xl border border-default bg-default/95 p-1.5 shadow-lg shadow-black/10 backdrop-blur"
    >
      <UButton
        v-for="section in sections"
        :key="section.id"
        color="neutral"
        :variant="activeSection === section.id ? 'solid' : 'ghost'"
        :aria-label="section.label"
        :class="[
          'h-9 shrink-0 justify-start transition-[width,padding] duration-150',
          expanded ? 'w-auto gap-2 px-3' : 'w-9 px-2',
        ]"
        @click="toggleSection(section.id)"
      >
        <UIcon :name="section.icon" class="size-4 shrink-0" />
        <span v-if="expanded" class="whitespace-nowrap">{{ section.label }}</span>
      </UButton>
    </div>

    <section
      v-if="activeSection"
      class="mt-2 w-80 rounded-xl border border-default bg-default/95 p-4 shadow-xl shadow-black/15 backdrop-blur"
    >
      <div class="mb-3 flex items-center justify-between gap-3">
        <div>
          <h2 class="font-semibold text-highlighted">
            {{ sections.find((section) => section.id === activeSection)?.label }}
          </h2>
          <p class="mt-0.5 text-xs text-muted">
            <template v-if="activeSection === 'layers'">Choose what is visible on this map.</template>
            <template v-else-if="activeSection === 'filters'">Narrow the map without leaving it.</template>
            <template v-else-if="activeSection === 'shapes'">Draw temporary map annotations.</template>
            <template v-else-if="activeSection === 'measure'">Measure distances, areas, and radii.</template>
            <template v-else>Choose how route lines are coloured.</template>
          </p>
        </div>
        <UButton color="neutral" variant="ghost" size="xs" icon="i-lucide-x" aria-label="Close" @click="activeSection = null" />
      </div>

      <div v-if="activeSection === 'layers'" class="space-y-2">
        <UCheckbox
          v-for="layer in layers"
          :key="layer.id"
          :model-value="layer.enabled"
          :label="layer.label"
          @update:model-value="emit('update:layer', { id: layer.id, enabled: $event })"
        />
        <p v-if="!layers.length" class="text-xs text-muted">This map has no optional layers.</p>
      </div>

      <div v-else-if="activeSection === 'filters'">
        <slot name="filters">
          <p class="text-sm text-muted">
            {{ filtersAvailable ? 'Use the available map filters.' : 'This map has no additional filters.' }}
          </p>
        </slot>
      </div>

      <div v-else-if="activeSection === 'shapes'" class="space-y-3">
        <div class="grid grid-cols-3 gap-2">
          <UButton
            v-for="tool in shapeTools"
            :key="tool.id"
            color="primary"
            :variant="tools.activeTool.value === tool.id ? 'solid' : 'soft'"
            class="flex-col gap-1 py-2"
            @click="chooseTool(tool.id)"
          >
            <UIcon :name="tool.icon" class="size-4" />
            <span class="text-xs">{{ tool.label }}</span>
          </UButton>
        </div>
        <UInput v-model="tools.noteLabel.value" size="sm" placeholder="Next note label" />
        <div class="flex justify-between gap-2">
          <UButton color="neutral" variant="soft" size="sm" label="Finish" :disabled="!tools.activePoints.value.length" @click="tools.complete" />
          <UButton color="neutral" variant="ghost" size="sm" label="Clear shapes" @click="tools.clear('drawing')" />
        </div>
      </div>

      <div v-else-if="activeSection === 'measure'" class="space-y-3">
        <div class="grid grid-cols-3 gap-2">
          <UButton
            v-for="tool in measureTools"
            :key="tool.id"
            color="primary"
            :variant="tools.activeTool.value === tool.id ? 'solid' : 'soft'"
            class="flex-col gap-1 py-2"
            @click="chooseTool(tool.id)"
          >
            <UIcon :name="tool.icon" class="size-4" />
            <span class="text-xs">{{ tool.label }}</span>
          </UButton>
        </div>
        <p class="text-xs text-muted">Click the map to add points. Radius completes after its second point.</p>
        <div class="flex justify-between gap-2">
          <UButton color="neutral" variant="soft" size="sm" label="Finish" :disabled="!tools.activePoints.value.length" @click="tools.complete" />
          <UButton color="neutral" variant="ghost" size="sm" label="Clear measures" @click="tools.clear('measurement')" />
        </div>
      </div>

      <div v-else class="space-y-2">
        <template v-if="routeColorsAvailable">
          <UButton
            color="primary"
            :variant="routeColorMode === 'official' ? 'solid' : 'soft'"
            block
            class="justify-start"
            icon="i-lucide-palette"
            @click="emit('update:routeColorMode', 'official')"
          >
            Official route colours
          </UButton>
          <UButton
            color="primary"
            :variant="routeColorMode === 'distinct' ? 'solid' : 'soft'"
            block
            class="justify-start"
            icon="i-lucide-swatch-book"
            @click="emit('update:routeColorMode', 'distinct')"
          >
            Distinct colours
          </UButton>
          <p class="text-xs text-muted">Distinct colours help adjacent lines remain legible.</p>
        </template>
        <p v-else class="text-sm text-muted">This map does not render route lines to recolour.</p>
      </div>

      <div v-if="tools.activeTool.value" class="mt-4 flex items-center justify-between border-t border-default pt-3">
        <span class="text-xs text-muted">{{ tools.activeTool.value }} tool active</span>
        <UButton color="neutral" variant="ghost" size="xs" label="Exit tool" @click="closeTools" />
      </div>
    </section>
  </div>
</template>
