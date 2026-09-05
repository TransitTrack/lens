// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2026-01-01',
  modules: ['@nuxt/ui', '@nuxtjs/apollo'],
  css: [],
  devtools: { enabled: true },
  apollo: {
    clients: {
      default: {
        httpEndpoint: '/graphql',
      },
    },
  },
  nitro: {
    devProxy: {
      '/graphql': {
        target: process.env.NUXT_BACKEND_URL ?? 'http://localhost:8080/graphql',
        changeOrigin: true,
      },
    },
  },
})
