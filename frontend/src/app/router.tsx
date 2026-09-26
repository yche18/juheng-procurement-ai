import {
  createBrowserRouter,
  type RouteObject,
} from 'react-router-dom'

import { RequireSession } from '../features/identity/components/RequireSession'
import { RequireRole } from '../features/identity/components/RequireRole'
import { LoginPage } from '../features/identity/pages/LoginPage'
import { CreateProcurementRequestPage } from '../features/procurement/pages/CreateProcurementRequestPage'
import { EditProcurementRequestPage } from '../features/procurement/pages/EditProcurementRequestPage'
import { ProcurementRequestDetailPage } from '../features/procurement/pages/ProcurementRequestDetailPage'
import { ProcurementRequestListPage } from '../features/procurement/pages/ProcurementRequestListPage'
import { AppShell } from './AppShell'
import { HomePage } from './HomePage'
import { ForbiddenPage, NotFoundPage } from './StatusPages'

export const appRoutes: RouteObject[] = [
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <RequireSession>
        <AppShell />
      </RequireSession>
    ),
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: '403',
        element: <ForbiddenPage />,
      },
      {
        path: 'requester/requests',
        element: (
          <RequireRole allowedRoles={['REQUESTER']}>
            <ProcurementRequestListPage />
          </RequireRole>
        ),
      },
      {
        path: 'requester/requests/new',
        element: (
          <RequireRole allowedRoles={['REQUESTER']}>
            <CreateProcurementRequestPage />
          </RequireRole>
        ),
      },
      {
        path: 'requester/requests/:requestId/edit',
        element: (
          <RequireRole allowedRoles={['REQUESTER']}>
            <EditProcurementRequestPage />
          </RequireRole>
        ),
      },
      {
        path: 'requester/requests/:requestId',
        element: (
          <RequireRole allowedRoles={['REQUESTER']}>
            <ProcurementRequestDetailPage />
          </RequireRole>
        ),
      },
    ],
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
]

export const router = createBrowserRouter(appRoutes)
