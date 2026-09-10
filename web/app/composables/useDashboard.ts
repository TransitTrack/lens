import { createSharedComposable } from '@vueuse/core'

const _useDashboard = () => {
  const route = useRoute()
  const router = useRouter()
  const isNotificationsSlideoverOpen = ref(false)
  const isFeedDrawerOpen = ref(false)

  const feedBase = () => {
    const p = route.params.feedCode
    return typeof p === 'string' && p ? `/${p}` : ''
  }

  defineShortcuts({
    'g-o': () => router.push(feedBase() || '/'),
    'g-v': () => feedBase() && router.push(`${feedBase()}/vehicles`),
    'g-h': () => feedBase() && router.push(`${feedBase()}/headway`),
    'g-e': () => feedBase() && router.push(`${feedBase()}/explore/routes`),
    n: () => (isNotificationsSlideoverOpen.value = !isNotificationsSlideoverOpen.value),
    f: () => (isFeedDrawerOpen.value = !isFeedDrawerOpen.value),
  })

  watch(
    () => route.fullPath,
    () => {
      isNotificationsSlideoverOpen.value = false
      isFeedDrawerOpen.value = false
    },
  )

  return {
    isNotificationsSlideoverOpen,
    isFeedDrawerOpen,
  }
}

export const useDashboard = createSharedComposable(_useDashboard)
