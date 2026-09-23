import {
  createListenerMiddleware,
  isRejectedWithValue,
} from '@reduxjs/toolkit'

import { sessionCleared } from '../features/identity/model/sessionSlice'
import { isAuthenticationRequiredError } from '../shared/api/apiError'
import { baseApi } from '../shared/api/baseApi'
import { clearCredentials } from '../shared/api/credentials'

export function createSessionListenerMiddleware() {
  const listenerMiddleware = createListenerMiddleware()

  listenerMiddleware.startListening({
    predicate: (action) =>
      isRejectedWithValue(action) &&
      isAuthenticationRequiredError(action.payload),
    effect: async (_action, listenerApi) => {
      clearCredentials()
      listenerApi.dispatch(sessionCleared())
      await listenerApi.delay(0)
      listenerApi.dispatch(baseApi.util.resetApiState())
    },
  })

  return listenerMiddleware
}
