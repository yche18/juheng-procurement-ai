import { baseApi } from '../../../shared/api/baseApi'
import type { ProcurementAuditTrailResponse } from '../types/audit'

export const auditApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getProcurementAuditTrail: builder.query<
      ProcurementAuditTrailResponse,
      string
    >({
      query: (requestId) => ({
        url: `procurement-requests/${requestId}/audit-events`,
        method: 'GET',
      }),
      providesTags: (_result, _error, requestId) => [
        { type: 'AuditTrail', id: requestId },
      ],
    }),
  }),
})

export const { useGetProcurementAuditTrailQuery } = auditApi
