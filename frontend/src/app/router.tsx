import {
  createBrowserRouter,
  type RouteObject,
} from 'react-router-dom'

import { RequireSession } from '../features/identity/components/RequireSession'
import { LoginPage } from '../features/identity/pages/LoginPage'
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
    ],
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
]

export const router = createBrowserRouter(appRoutes)
