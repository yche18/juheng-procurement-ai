import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { useAppSelector } from '../../../app/hooks'

interface RequireRoleProps {
  allowedRoles: readonly string[]
  children: ReactNode
}

export function RequireRole({ allowedRoles, children }: RequireRoleProps) {
  const currentUser = useAppSelector((state) => state.session.currentUser)
  const location = useLocation()

  if (!currentUser) {
    const returnPath = `${location.pathname}${location.search}${location.hash}`
    return <Navigate to="/login" replace state={{ from: returnPath }} />
  }

  const hasAllowedRole = currentUser.roles.some((role) =>
    allowedRoles.includes(role),
  )

  if (!hasAllowedRole) {
    return <Navigate to="/403" replace />
  }

  return children
}
