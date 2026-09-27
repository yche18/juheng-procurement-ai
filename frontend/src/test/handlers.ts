import { http, HttpResponse } from 'msw'

export const API_ORIGIN = 'http://localhost:3000'

export const handlers = [
  http.get(`${API_ORIGIN}/api/current-user`, () =>
    HttpResponse.json({
      userId: 'demo-multi-role',
      roles: ['APPROVER', 'REQUESTER'],
    }),
  ),
  http.get(
    `${API_ORIGIN}/api/procurement-requests/:requestId/audit-events`,
    ({ params }) =>
      HttpResponse.json({
        procurementRequestId: String(params.requestId),
        events: [],
      }),
  ),
]
