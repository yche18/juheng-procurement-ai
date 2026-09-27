import { baseApi } from '../../../shared/api/baseApi'
import type {
  ApprovalTaskDetailResponse,
  ApprovalTaskListQuery,
  ApprovalTaskPageResponse,
} from '../types/approval'

export const approvalApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getApprovalTasks: builder.query<
      ApprovalTaskPageResponse,
      ApprovalTaskListQuery
    >({
      query: (params) => ({
        url: 'approval-tasks',
        method: 'GET',
        params,
      }),
      providesTags: (result) => [
        { type: 'ApprovalTask', id: 'LIST' },
        ...(Array.isArray(result?.content)
          ? result.content.flatMap((task) =>
              typeof task === 'object' &&
              task !== null &&
              'id' in task &&
              typeof task.id === 'string'
                ? [{ type: 'ApprovalTask' as const, id: task.id }]
                : [],
            )
          : []),
      ],
    }),
    getApprovalTask: builder.query<ApprovalTaskDetailResponse, string>({
      query: (taskId) => ({
        url: `approval-tasks/${taskId}`,
        method: 'GET',
      }),
      providesTags: (_result, _error, taskId) => [
        { type: 'ApprovalTask', id: taskId },
      ],
    }),
  }),
})

export const {
  useGetApprovalTaskQuery,
  useGetApprovalTasksQuery,
} = approvalApi
