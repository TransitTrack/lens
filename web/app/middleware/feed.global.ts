/**
 * Remembers the feed code from the URL so a bare visit to `/` can restore it.
 * Validation of unknown feed codes happens reactively in the default layout
 * (it needs the feed list, which isn't available synchronously here).
 */
export default defineNuxtRouteMiddleware((to) => {
  const feedCode = to.params.feedCode
  if (typeof feedCode === 'string' && feedCode) {
    const cookie = useCookie<string | null>('tt-feed', { default: () => null })
    cookie.value = feedCode
  }
})
