import { App as AntDesignApp, ConfigProvider } from 'antd'
import { render } from '@testing-library/react'
import { Provider } from 'react-redux'
import {
  createMemoryRouter,
  RouterProvider,
  type RouteObject,
} from 'react-router-dom'

import { appRoutes } from '../app/router'
import { createAppStore, type AppStore } from '../app/store'

interface RenderApplicationOptions {
  initialEntries?: string[]
  routes?: RouteObject[]
  store?: AppStore
}

export function renderApplication({
  initialEntries = ['/'],
  routes = appRoutes,
  store = createAppStore(),
}: RenderApplicationOptions = {}) {
  const router = createMemoryRouter(routes, { initialEntries })
  const result = render(
    <Provider store={store}>
      <ConfigProvider>
        <AntDesignApp>
          <RouterProvider router={router} />
        </AntDesignApp>
      </ConfigProvider>
    </Provider>,
  )

  return {
    ...result,
    router,
    store,
  }
}
