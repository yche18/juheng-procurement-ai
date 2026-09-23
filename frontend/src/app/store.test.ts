import { describe, expect, it, vi } from 'vitest'

import { identityApi } from '../features/identity/api/identityApi'
import { sessionAuthenticated } from '../features/identity/model/sessionSlice'
import { apiClient } from '../shared/api/apiClient'
import {
  getAuthorizationHeader,
  setCredentials,
} from '../shared/api/credentials'
import { createAppStore } from './store'

describe('app store', () => {
  it('stores the verified user but never stores Basic credentials', () => {
    const appStore = createAppStore()
    setCredentials('demo-requester', 'not-in-redux')
    appStore.dispatch(
      sessionAuthenticated({
        userId: 'demo-requester',
        roles: ['REQUESTER'],
      }),
    )

    const serializedState = JSON.stringify(appStore.getState())
    expect(serializedState).toContain('demo-requester')
    expect(serializedState).not.toContain('not-in-redux')
    expect(serializedState).not.toContain('Authorization')
  })

  it('clears credentials, session, and user-scoped cache after a 401', async () => {
    const appStore = createAppStore()
    setCredentials('demo-requester', 'expired-password')
    appStore.dispatch(
      sessionAuthenticated({
        userId: 'demo-requester',
        roles: ['REQUESTER'],
      }),
    )

    vi.spyOn(apiClient, 'request').mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 401,
        data: {
          code: 'AUTHENTICATION_REQUIRED',
          message: 'Authentication is required',
          path: '/api/current-user',
          fieldErrors: [],
        },
      },
    })

    await appStore.dispatch(identityApi.endpoints.getCurrentUser.initiate())

    expect(appStore.getState().session).toEqual({
      status: 'unauthenticated',
      currentUser: null,
    })
    expect(getAuthorizationHeader()).toBeUndefined()
    await vi.waitFor(() => {
      expect(Object.keys(appStore.getState().api.queries)).toHaveLength(0)
    })
  })

  it('keeps the verified session after a 403 authorization failure', async () => {
    const appStore = createAppStore()
    setCredentials('demo-requester', 'valid-password')
    appStore.dispatch(
      sessionAuthenticated({
        userId: 'demo-requester',
        roles: ['REQUESTER'],
      }),
    )

    vi.spyOn(apiClient, 'request').mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 403,
        data: {
          code: 'ACCESS_DENIED',
          message: 'Access is denied',
          path: '/api/current-user',
          fieldErrors: [],
        },
      },
    })

    await appStore.dispatch(identityApi.endpoints.getCurrentUser.initiate())

    expect(appStore.getState().session).toEqual({
      status: 'authenticated',
      currentUser: {
        userId: 'demo-requester',
        roles: ['REQUESTER'],
      },
    })
    expect(getAuthorizationHeader()).toBeDefined()
  })
})
