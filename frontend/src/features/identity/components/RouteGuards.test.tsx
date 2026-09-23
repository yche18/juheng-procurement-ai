import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { sessionAuthenticated } from '../model/sessionSlice'
import { createAppStore } from '../../../app/store'
import { renderApplication } from '../../../test/renderApplication'
import { RequireRole } from './RequireRole'

describe('identity route guards', () => {
  it('redirects an unauthenticated visitor to login', () => {
    renderApplication({ initialEntries: ['/'] })

    expect(
      screen.getByRole('button', { name: /登\s*录/ }),
    ).toBeInTheDocument()
  })

  it('allows a matching role and rejects a missing role', async () => {
    const requesterStore = createAppStore()
    requesterStore.dispatch(
      sessionAuthenticated({
        userId: 'demo-requester',
        roles: ['REQUESTER'],
      }),
    )

    const routes = [
      {
        path: '/protected',
        element: (
          <RequireRole allowedRoles={['APPROVER']}>
            <div>protected content</div>
          </RequireRole>
        ),
      },
      {
        path: '/403',
        element: <div>forbidden content</div>,
      },
    ]

    renderApplication({
      initialEntries: ['/protected'],
      routes,
      store: requesterStore,
    })

    expect(await screen.findByText('forbidden content')).toBeInTheDocument()
    expect(screen.queryByText('protected content')).not.toBeInTheDocument()
  })

  it('does not treat an ADMIN-only session as a business-data super user', () => {
    const adminStore = createAppStore()
    adminStore.dispatch(
      sessionAuthenticated({
        userId: 'demo-admin',
        roles: ['ADMIN'],
      }),
    )

    renderApplication({ store: adminStore })

    expect(
      screen.getByRole('heading', { name: '当前版本无管理功能' }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(
        'ADMIN 角色不会自动获得采购申请或审批任务的数据访问范围。',
      ),
    ).toBeInTheDocument()
  })
})
