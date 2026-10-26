const SESSION_COOKIE = 'tt-demo-authenticated'

export function useDemoAuth() {
  const config = useRuntimeConfig()
  const authenticated = useCookie<boolean>(SESSION_COOKIE, {
    default: () => false,
    sameSite: 'lax',
  })

  function signIn(username: string, password: string) {
    const valid =
      username.trim() === config.public.demoAuthUsername && password === config.public.demoAuthPassword

    if (valid) authenticated.value = true
    return valid
  }

  function signOut() {
    authenticated.value = false
  }

  return { authenticated, signIn, signOut }
}
