import { baseApi } from '../../../shared/api/baseApi'
import type {
  ApprovalDecisionResponse,
  ApprovalTaskDetailResponse,
  ApprovalTaskListQuery,
  ApprovalTaskPageResponse,
  DecideApprovalTaskMutationArgs,
} from '../types/approval'

function decisionInvalidationTags({
  taskId,
  requestId,
}: DecideApprovalTaskMutationArgs) {
  return [
    { type: 'ApprovalTask' as const, id: taskId },
    { type: 'ApprovalTask' as const, id: 'LIST' },
    { type: 'ProcurementRequest' as const, id: requestId },
    { type: 'ProcurementRequest' as const, id: 'LIST' },
    { type: 'ApprovalDecision' as const, id: requestId },
  ]
}

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
    approveApprovalTask: builder.mutation<
      ApprovalDecisionResponse,
      DecideApprovalTaskMutationArgs
    >({
      query: ({
        taskId,
        approvalTaskVersion,
        comment,
        idempotencyKey,
      }) => ({
        url: `approval-tasks/${taskId}/approve`,
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        data: { approvalTaskVersion, comment },
      }),
      invalidatesTags: (result, _error, args) =>
        result ? decisionInvalidationTags(args) : [],
    }),
    rejectApprovalTask: builder.mutation<
      ApprovalDecisionResponse,
      DecideApprovalTaskMutationArgs
    >({
      query: ({
        taskId,
        approvalTaskVersion,
        comment,
        idempotencyKey,
      }) => ({
        url: `approval-tasks/${taskId}/reject`,
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        data: { approvalTaskVersion, comment },
      }),
      invalidatesTags: (result, _error, args) =>
        result ? decisionInvalidationTags(args) : [],
    }),
  }),
})

export const {
  useApproveApprovalTaskMutation,
  useGetApprovalTaskQuery,
  useGetApprovalTasksQuery,
  useRejectApprovalTaskMutation,
} = approvalApi
