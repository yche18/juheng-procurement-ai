import { http, HttpResponse } from 'msw'

export const API_ORIGIN = 'http://localhost:3000'

export const handlers = [
  http.get(`${API_ORIGIN}/api/current-user`, () =>
    HttpResponse.json({
      userId: 'demo-multi-role',
      roles: ['APPROVER', 'REQUESTER'],
    }),
  ),
]
