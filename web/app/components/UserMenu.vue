<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { useEditorIdentity } from '~/composables/useEditorIdentity'

defineProps<{
  /** Icon-only trigger for a collapsed sidebar; full name row otherwise. */
  collapsed?: boolean
}>()

const colorMode = useColorMode()
const { name: editorName } = useEditorIdentity()
const identityOpen = ref(false)
const identityInput = ref('')

const avatar = { icon: 'i-lucide-user-round' }
const displayName = computed(() => editorName.value?.trim() || 'Operator')

watch(identityOpen, (open) => {
  if (open) identityInput.value = editorName.value ?? ''
})

function saveIdentity() {
  const value = identityInput.value.trim()
  if (!value) return
  editorName.value = value
  identityOpen.value = false
}

const items = computed<DropdownMenuItem[][]>(() => [
  [{ type: 'label', label: displayName.value, avatar }],
  [
    {
      label: 'Editor identity',
      icon: 'i-lucide-user-round-pen',
      onSelect() {
        identityOpen.value = true
      },
    },
    {
      label: 'Theme',
      icon: 'i-lucide-palette',
      children: [
        {
          label: 'System',
          icon: 'i-lucide-monitor',
          type: 'checkbox',
          checked: colorMode.preference === 'system',
          onSelect(e: Event) {
            e.preventDefault()
            colorMode.preference = 'system'
          },
        },
        {
          label: 'Light',
          icon: 'i-lucide-sun',
          type: 'checkbox',
          checked: colorMode.value === 'light',
          onSelect(e: Event) {
            e.preventDefault()
            colorMode.preference = 'light'
          },
        },
        {
          label: 'Dark',
          icon: 'i-lucide-moon',
          type: 'checkbox',
          checked: colorMode.value === 'dark',
          onSelect(e: Event) {
            e.preventDefault()
            colorMode.preference = 'dark'
          },
        },
      ],
    },
  ],
])
</script>

<template>
  <UDropdownMenu
    :items="items"
    :content="{ side: 'top', align: 'start', collisionPadding: 12 }"
    :ui="{ content: 'w-60' }"
  >
    <UButton
      v-if="collapsed"
      :avatar="avatar"
      :aria-label="displayName"
      color="neutral"
      variant="ghost"
      square
      class="data-[state=open]:bg-elevated"
    />
    <UButton
      v-else
      color="neutral"
      variant="ghost"
      block
      class="h-12 justify-start gap-2.5 px-2.5 data-[state=open]:bg-elevated"
    >
      <UAvatar :icon="avatar.icon" size="xs" />
      <span class="min-w-0 flex-1 text-left">
        <span class="block truncate text-sm font-medium text-highlighted">{{ displayName }}</span>
        <span class="block truncate text-[10px] text-dimmed">Profile &amp; preferences</span>
      </span>
      <UIcon name="i-lucide-chevrons-up-down" class="size-4 shrink-0 text-dimmed" />
    </UButton>
  </UDropdownMenu>

  <UModal v-model:open="identityOpen" title="Editor identity">
    <template #body>
      <p class="mb-3 text-sm text-muted">Shown on draft changes and schedule actions.</p>
      <UInput
        v-model="identityInput"
        placeholder="Your name"
        autofocus
        class="w-full"
        @keydown.enter="saveIdentity"
      />
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" label="Cancel" @click="identityOpen = false" />
        <UButton :disabled="!identityInput.trim()" label="Save identity" @click="saveIdentity" />
      </div>
    </template>
  </UModal>
</template>
