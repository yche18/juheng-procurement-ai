import { Button, Layout, Space, Tag, Typography } from 'antd'
import { Outlet, useNavigate } from 'react-router-dom'

import { sessionCleared } from '../features/identity/model/sessionSlice'
import { baseApi } from '../shared/api/baseApi'
import { clearCredentials } from '../shared/api/credentials'
import { useAppDispatch, useAppSelector } from './hooks'

const { Header, Content } = Layout

export function AppShell() {
  const dispatch = useAppDispatch()
  const currentUser = useAppSelector((state) => state.session.currentUser)
  const navigate = useNavigate()

  const handleLogout = () => {
    clearCredentials()
    dispatch(sessionCleared())
    dispatch(baseApi.util.resetApiState())
    void navigate('/login', { replace: true })
  }

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        <Typography.Title level={3} className="app-title">
          据衡
        </Typography.Title>
        <Space wrap>
          <Typography.Text className="header-user">
            {currentUser?.userId}
          </Typography.Text>
          {currentUser?.roles.map((role) => <Tag key={role}>{role}</Tag>)}
          <Button onClick={handleLogout}>退出</Button>
        </Space>
      </Header>
      <Content className="app-content">
        <Outlet />
      </Content>
    </Layout>
  )
}
