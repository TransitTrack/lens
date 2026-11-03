<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { useEditorIdentity } from '~/composables/useEditorIdentity'

defineProps<{
  /** Icon-only trigger for a collapsed sidebar; full name row otherwise. */
  collapsed?: boolean
}>()

const colorMode = useColorMode()
const { t } = useI18n()
const { locale, options: localeOptions, setLocale } = useLocaleSwitcher()
const { name: editorName } = useEditorIdentity()
const { signOut } = useDemoAuth()
const identityOpen = ref(false)
const identityInput = ref('')

const avatar = { icon: 'i-lucide-user-round', size: 'sm' }
const displayName = computed(() => editorName.value?.trim() || t('userMenu.operator'))

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
      label: t('userMenu.editorIdentity'),
      icon: 'i-lucide-user-round-pen',
      onSelect() {
        identityOpen.value = true
      },
    },
    {
      label: t('userMenu.theme'),
      icon: 'i-lucide-palette',
      children: [
        {
          label: t('userMenu.themeSystem'),
          icon: 'i-lucide-monitor',
          type: 'checkbox',
          checked: colorMode.preference === 'system',
          onSelect(e: Event) {
            e.preventDefault()
            colorMode.preference = 'system'
          },
        },
        {
          label: t('userMenu.themeLight'),
          icon: 'i-lucide-sun',
          type: 'checkbox',
          checked: colorMode.preference === 'light',
          onSelect(e: Event) {
            e.preventDefault()
            colorMode.preference = 'light'
          },
        },
        {
          label: t('userMenu.themeDark'),
          icon: 'i-lucide-moon',
          type: 'checkbox',
          checked: colorMode.preference === 'dark',
          onSelect(e: Event) {
            e.preventDefault()
            colorMode.preference = 'dark'
          },
        },
      ],
    },
    {
      label: t('userMenu.language'),
      icon: 'i-lucide-languages',
      children: localeOptions.map((o) => ({
        label: o.name,
        type: 'checkbox' as const,
        checked: locale.value === o.code,
        onSelect(e: Event) {
          e.preventDefault()
          void setLocale(o.code)
        },
      })),
    },
    {
      label: t('userMenu.signOut'),
      icon: 'i-lucide-log-out',
      onSelect() {
        signOut()
        navigateTo('/login')
      },
    },
  ],
])
</script>

<template>
  <UDropdownMenu
    :items="items"
    :content="{ side: 'top', align: 'start', collisionPadding: 6 }"
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
      class="h-12 justify-start gap-1 px-1 data-[state=open]:bg-elevated"
    >
      <UAvatar :icon="avatar.icon" size="sm" />
      <span class="min-w-0 flex-1 text-left">
        <span class="block truncate text-sm font-medium text-highlighted">{{ displayName }}</span>
        <span class="block truncate text-[10px] text-dimmed">{{ $t('userMenu.profileAndPreferences') }}</span>
      </span>
      <UIcon name="i-lucide-chevrons-up-down" class="size-4 shrink-0 text-dimmed" />
    </UButton>
  </UDropdownMenu>

  <UModal v-model:open="identityOpen" :title="$t('userMenu.editorIdentity')">
    <template #body>
      <p class="mb-3 text-sm text-muted">{{ $t('userMenu.editorIdentityHelp') }}</p>
      <UInput
        v-model="identityInput"
        :placeholder="$t('userMenu.namePlaceholder')"
        autofocus
        class="w-full"
        @keydown.enter="saveIdentity"
      />
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="identityOpen = false" />
        <UButton :disabled="!identityInput.trim()" :label="$t('userMenu.saveIdentity')" @click="saveIdentity" />
      </div>
    </template>
  </UModal>
</template>
