export default defineNuxtRouteMiddleware((to) => {
  const { authenticated } = useDemoAuth()
  const isLogin = to.path === '/login'

  if (!authenticated.value && !isLogin) {
    return navigateTo({ path: '/login', query: { redirect: to.fullPath } })
  }

  if (authenticated.value && isLogin) {
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/'
    return navigateTo(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/')
  }
})
