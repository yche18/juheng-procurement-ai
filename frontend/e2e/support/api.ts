import { expect, request, type APIRequestContext } from '@playwright/test'

export const demoPassword =
  process.env.JUHENG_E2E_DEMO_PASSWORD ?? 'juheng-local'

export const demoUsers = {
  requester: 'demo-requester',
  approver: 'demo-approver',
  admin: 'demo-admin',
  multiRole: 'demo-requester-approver',
} as const

export interface DraftResponse {
  id: string
  businessNumber: string
  status: 'DRAFT'
  version: number
}

export interface SubmissionResponse {
  requestId: string
  requestStatus: 'SUBMITTED'
  requestVersion: number
  approvalTaskId: string
  approvalTaskStatus: 'PENDING'
}

export interface DraftPayload {
  title: string
  purpose: string
  department: string
  expectedDeliveryDate: string
  items: Array<{
    name: string
    categoryCode: 'LAPTOP'
    specification: string
    quantity: number
    unit: string
    estimatedUnitPrice: number
  }>
}

export function createDraftPayload(title: string): DraftPayload {
  return {
    title,
    purpose: 'FE-017 合成端到端验收数据',
    department: '测试采购部',
    expectedDeliveryDate: '2030-12-31',
    items: [
      {
        name: '验收笔记本',
        categoryCode: 'LAPTOP',
        specification: '32GB 内存，端到端测试专用',
        quantity: 2,
        unit: '台',
        estimatedUnitPrice: 8000,
      },
    ],
  }
}

export async function createApiContext(
  baseURL: string,
  username?: string,
): Promise<APIRequestContext> {
  const extraHTTPHeaders: Record<string, string> = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  }
  if (username) {
    extraHTTPHeaders.Authorization = `Basic ${Buffer.from(`${username}:${demoPassword}`).toString('base64')}`
  }

  return request.newContext({ baseURL, extraHTTPHeaders })
}

export async function createDraft(
  api: APIRequestContext,
  title: string,
): Promise<DraftResponse> {
  const response = await api.post('/api/procurement-requests', {
    data: createDraftPayload(title),
  })
  await expect(response).toBeOK()
  return response.json() as Promise<DraftResponse>
}

export async function submitDraft(
  api: APIRequestContext,
  draft: DraftResponse,
  idempotencyKey: string,
): Promise<SubmissionResponse> {
  const response = await api.post(
    `/api/procurement-requests/${draft.id}/submit`,
    {
      headers: { 'Idempotency-Key': idempotencyKey },
      data: { version: draft.version },
    },
  )
  await expect(response).toBeOK()
  return response.json() as Promise<SubmissionResponse>
}

export function uniqueTestTitle(purpose: string): string {
  return `FE017-${purpose}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
}
