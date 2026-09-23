import { Button, Result } from 'antd'
import { Link } from 'react-router-dom'

export function ForbiddenPage() {
  return (
    <Result
      status="403"
      title="无权访问"
      subTitle="当前身份没有进入该页面所需的角色。"
      extra={
        <Button type="primary">
          <Link to="/">返回首页</Link>
        </Button>
      }
    />
  )
}

export function NotFoundPage() {
  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="请检查地址，或返回应用首页。"
      extra={
        <Button type="primary">
          <Link to="/">返回首页</Link>
        </Button>
      }
    />
  )
}
