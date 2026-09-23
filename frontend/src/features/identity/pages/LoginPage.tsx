import { Alert, Button, Card, Form, Input, Space, Typography } from 'antd'
import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'

import { useAppDispatch, useAppSelector } from '../../../app/hooks'
import { getApiErrorMessage } from '../../../shared/api/apiError'
import { baseApi } from '../../../shared/api/baseApi'
import {
  clearCredentials,
  setCredentials,
} from '../../../shared/api/credentials'
import { useLazyGetCurrentUserQuery } from '../api/identityApi'
import { sessionAuthenticated, sessionCleared } from '../model/sessionSlice'

interface LoginFormValues {
  username: string
  password: string
}

function getSafeReturnPath(state: unknown): string {
  if (typeof state !== 'object' || state === null || !('from' in state)) {
    return '/'
  }

  const from = state.from
  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//')) {
    return '/'
  }

  return from
}

export function LoginPage() {
  const dispatch = useAppDispatch()
  const sessionStatus = useAppSelector((state) => state.session.status)
  const location = useLocation()
  const navigate = useNavigate()
  const [getCurrentUser, { isFetching }] = useLazyGetCurrentUserQuery()
  const [errorMessage, setErrorMessage] = useState<string>()

  if (sessionStatus === 'authenticated') {
    return <Navigate to="/" replace />
  }

  const handleSubmit = async ({ username, password }: LoginFormValues) => {
    setErrorMessage(undefined)
    setCredentials(username, password)

    try {
      const currentUser = await getCurrentUser().unwrap()
      dispatch(sessionAuthenticated(currentUser))
      void navigate(getSafeReturnPath(location.state), { replace: true })
    } catch (error) {
      clearCredentials()
      dispatch(sessionCleared())
      dispatch(baseApi.util.resetApiState())
      setErrorMessage(getApiErrorMessage(error))
    }
  }

  return (
    <main className="login-page">
      <Card className="login-card" variant="borderless">
        <Space orientation="vertical" size="large" className="full-width">
          <div>
            <Typography.Title level={1}>据衡</Typography.Title>
            <Typography.Paragraph type="secondary">
              R1 采购授权演示客户端
            </Typography.Paragraph>
          </div>

          {errorMessage ? (
            <Alert
              type="error"
              showIcon
              title="登录失败"
              description={errorMessage}
            />
          ) : null}

          <Form<LoginFormValues>
            layout="vertical"
            requiredMark={false}
            onFinish={(values) => {
              void handleSubmit(values)
            }}
          >
            <Form.Item
              label="用户名"
              name="username"
              rules={[
                { required: true, whitespace: true, message: '请输入用户名' },
                { pattern: /^[^:]+$/, message: '用户名不能包含冒号' },
              ]}
            >
              <Input autoComplete="username" disabled={isFetching} />
            </Form.Item>

            <Form.Item
              label="密码"
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                autoComplete="current-password"
                disabled={isFetching}
              />
            </Form.Item>

            <Button
              type="primary"
              htmlType="submit"
              loading={isFetching}
              block
            >
              登录
            </Button>
          </Form>

          <Typography.Text type="secondary">
            仅用于本地演示。刷新页面后需要重新登录，凭据不会持久化。
          </Typography.Text>
        </Space>
      </Card>
    </main>
  )
}
