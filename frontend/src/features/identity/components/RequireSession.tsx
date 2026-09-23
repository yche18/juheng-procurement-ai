import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'

import { useAppSelector } from '../../../app/hooks'

interface RequireSessionProps {
  children: ReactNode
}

export function RequireSession({ children }: RequireSessionProps) {
  const status = useAppSelector((state) => state.session.status)
  const location = useLocation()

  if (status !== 'authenticated') {
    const returnPath = `${location.pathname}${location.search}${location.hash}`
    return <Navigate to="/login" replace state={{ from: returnPath }} />
  }

  return children
}
