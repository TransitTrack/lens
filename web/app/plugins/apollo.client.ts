import { ApolloClient, InMemoryCache, HttpLink } from '@apollo/client/core'
import { provideApolloClient } from '@vue/apollo-composable'

export default defineNuxtPlugin(() => {
  const httpLink = new HttpLink({ uri: '/graphql' })
  const cache = new InMemoryCache()
  const apolloClient = new ApolloClient({
    link: httpLink,
    cache: cache,
    devtools: { enabled: true }
  })
  provideApolloClient(apolloClient)
})
