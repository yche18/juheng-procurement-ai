import { configureStore } from '@reduxjs/toolkit'

import { sessionReducer } from '../features/identity/model/sessionSlice'
import { baseApi } from '../shared/api/baseApi'
import { createSessionListenerMiddleware } from './sessionListener'

export function createAppStore() {
  const sessionListenerMiddleware = createSessionListenerMiddleware()

  return configureStore({
    reducer: {
      session: sessionReducer,
      [baseApi.reducerPath]: baseApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware()
        .prepend(sessionListenerMiddleware.middleware)
        .concat(baseApi.middleware),
  })
}

export const store = createAppStore()

export type AppStore = ReturnType<typeof createAppStore>
export type RootState = ReturnType<AppStore['getState']>
export type AppDispatch = AppStore['dispatch']
