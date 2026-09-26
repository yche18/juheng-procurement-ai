import { baseApi } from '../../../shared/api/baseApi'
import type {
  CreateProcurementRequestRequest,
  CreateProcurementRequestResponse,
} from '../types/procurement'

export const procurementApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
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
  }),
})

export const { useCreateProcurementRequestMutation } = procurementApi
