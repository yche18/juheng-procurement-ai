import { baseApi } from '../../../shared/api/baseApi'
import type {
  CreateProcurementRequestRequest,
  CreateProcurementRequestResponse,
  FinalApprovalDecisionResponse,
  PageResponse,
  ProcurementRequestDetailResponse,
  ProcurementRequestListQuery,
  ProcurementRequestSummaryResponse,
  UpdateProcurementRequestMutationArgs,
} from '../types/procurement'

export const procurementApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getProcurementRequests: builder.query<
      PageResponse<ProcurementRequestSummaryResponse>,
      ProcurementRequestListQuery
    >({
      query: (params) => ({
        url: 'procurement-requests',
        method: 'GET',
        params,
      }),
      providesTags: (result) => [
        { type: 'ProcurementRequest', id: 'LIST' },
        ...(Array.isArray(result?.content)
          ? result.content.flatMap((item) =>
              typeof item === 'object' &&
              item !== null &&
              'id' in item &&
              typeof item.id === 'string'
                ? [{ type: 'ProcurementRequest' as const, id: item.id }]
                : [],
            )
          : []),
      ],
    }),
    getProcurementRequest: builder.query<
      ProcurementRequestDetailResponse,
      string
    >({
      query: (requestId) => ({
        url: `procurement-requests/${requestId}`,
        method: 'GET',
      }),
      providesTags: (_result, _error, requestId) => [
        { type: 'ProcurementRequest', id: requestId },
      ],
    }),
    getFinalApprovalDecision: builder.query<
      FinalApprovalDecisionResponse,
      string
    >({
      query: (requestId) => ({
        url: `procurement-requests/${requestId}/approval-decision`,
        method: 'GET',
      }),
      providesTags: (_result, _error, requestId) => [
        { type: 'ApprovalDecision', id: requestId },
      ],
    }),
    createProcurementRequest: builder.mutation<
      CreateProcurementRequestResponse,
      CreateProcurementRequestRequest
    >({
      query: (body) => ({
        url: 'procurement-requests',
        method: 'POST',
        data: body,
      }),
      invalidatesTags: [{ type: 'ProcurementRequest', id: 'LIST' }],
    }),
    updateProcurementRequest: builder.mutation<
      ProcurementRequestDetailResponse,
      UpdateProcurementRequestMutationArgs
    >({
      query: ({ requestId, body }) => ({
        url: `procurement-requests/${requestId}`,
        method: 'PUT',
        data: body,
      }),
      invalidatesTags: (result) =>
        result
          ? [
              { type: 'ProcurementRequest', id: result.id },
              { type: 'ProcurementRequest', id: 'LIST' },
            ]
          : [],
    }),
  }),
})

export const {
  useCreateProcurementRequestMutation,
  useGetFinalApprovalDecisionQuery,
  useGetProcurementRequestQuery,
  useGetProcurementRequestsQuery,
  useUpdateProcurementRequestMutation,
} = procurementApi
