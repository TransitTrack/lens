import { ApolloClient, InMemoryCache, HttpLink } from '@apollo/client/core'
import { provideApolloClient } from '@vue/apollo-composable'

export default defineNuxtPlugin(() => {
  const httpLink = new HttpLink({ uri: '/graphql' })
  const apolloClient = new ApolloClient({
    link: httpLink,
    cache: new InMemoryCache(),
  })
  provideApolloClient(apolloClient)
})
