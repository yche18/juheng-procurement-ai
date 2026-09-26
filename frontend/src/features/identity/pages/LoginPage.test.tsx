import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { renderApplication } from '../../../test/renderApplication'
import { API_ORIGIN } from '../../../test/handlers'
import { server } from '../../../test/server'
import { getAuthorizationHeader } from '../../../shared/api/credentials'

describe('LoginPage', () => {
  it('verifies credentials, stores only the user, and enters the app shell', async () => {
    const user = userEvent.setup()
    const { store } = renderApplication({ initialEntries: ['/login'] })

    await user.type(screen.getByLabelText('用户名'), 'demo-multi-role')
    await user.type(screen.getByLabelText('密码'), 'juheng-local')
    await user.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(
      await screen.findByRole('heading', { name: 'R1 采购授权演示' }),
    ).toBeInTheDocument()
    expect(screen.getByText('demo-multi-role')).toBeInTheDocument()
    expect(screen.getByText('REQUESTER')).toBeInTheDocument()
    expect(screen.getByText('APPROVER')).toBeInTheDocument()

    const serializedState = JSON.stringify(store.getState())
    expect(serializedState).not.toContain('juheng-local')
    expect(window.localStorage).toHaveLength(0)
    expect(window.sessionStorage).toHaveLength(0)

    expect(getAuthorizationHeader()).toBeDefined()
    await user.click(screen.getByRole('button', { name: /退\s*出/ }))

    expect(
      await screen.findByRole('button', { name: /登\s*录/ }),
    ).toBeInTheDocument()
    expect(store.getState().session.status).toBe('unauthenticated')
    expect(getAuthorizationHeader()).toBeUndefined()
  })

  it('shows the backend authentication failure and remains logged out', async () => {
    server.use(
      http.get(`${API_ORIGIN}/api/current-user`, () =>
        HttpResponse.json(
          {
            code: 'AUTHENTICATION_REQUIRED',
            message: 'Authentication is required',
            path: '/api/current-user',
            fieldErrors: [],
          },
          { status: 401 },
        ),
      ),
    )

    const user = userEvent.setup()
    const { store } = renderApplication({ initialEntries: ['/login'] })

    await user.type(screen.getByLabelText('用户名'), 'demo-requester')
    await user.type(screen.getByLabelText('密码'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /登\s*录/ }))

    expect(await screen.findByText('登录失败')).toBeInTheDocument()
    expect(screen.getByText('Authentication is required')).toBeInTheDocument()
    expect(store.getState().session.status).toBe('unauthenticated')
  })
})
