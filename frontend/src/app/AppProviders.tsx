import { App as AntDesignApp, ConfigProvider } from 'antd'
import { Provider } from 'react-redux'
import { RouterProvider } from 'react-router-dom'

import { router } from './router'
import { store } from './store'

export function AppProviders() {
  return (
    <Provider store={store}>
      <ConfigProvider
        theme={{
          token: {
            colorPrimary: '#175c4c',
            borderRadius: 8,
          },
        }}
      >
        <AntDesignApp>
          <RouterProvider router={router} />
        </AntDesignApp>
      </ConfigProvider>
    </Provider>
  )
}
