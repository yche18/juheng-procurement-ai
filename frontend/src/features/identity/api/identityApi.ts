import { baseApi } from '../../../shared/api/baseApi'
import type { CurrentUser } from '../model/sessionSlice'

export const identityApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getCurrentUser: builder.query<CurrentUser, void>({
      query: () => ({
        url: 'current-user',
        method: 'GET',
      }),
      providesTags: ['CurrentUser'],
    }),
  }),
})

export const { useLazyGetCurrentUserQuery } = identityApi
