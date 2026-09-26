import { Alert, Button, Card, Form, Typography } from 'antd'
import { useState } from 'react'

import { getApiErrorMessage } from '../../../shared/api/apiError'
import { useCreateProcurementRequestMutation } from '../api/procurementApi'
import { CreatedProcurementRequestResult } from '../components/CreatedProcurementRequestResult'
import { ProcurementItemsFields } from '../components/ProcurementItemsFields'
import { ProcurementRequestFields } from '../components/ProcurementRequestFields'
import {
  asBackendError,
  initialFormValue,
  toFormFieldName,
  toRequest,
  type ProcurementFormFieldName,
  type ProcurementRequestFormValue,
} from '../model/procurementForm'
import type { CreateProcurementRequestResponse } from '../types/procurement'

export function CreateProcurementRequestPage() {
  const [form] = Form.useForm<ProcurementRequestFormValue>()
  const [createRequest, { isLoading }] = useCreateProcurementRequestMutation()
  const [createdRequest, setCreatedRequest] =
    useState<CreateProcurementRequestResponse | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)

  const handleSubmit = async (values: ProcurementRequestFormValue) => {
    setPageError(null)

    try {
      const result = await createRequest(toRequest(values)).unwrap()
      setCreatedRequest(result)
    } catch (error) {
      const backendError = asBackendError(error)
      if (
        backendError?.response.code === 'VALIDATION_FAILED' &&
        backendError.response.fieldErrors.length > 0
      ) {
        const itemCount = form.getFieldsValue().items?.length ?? 0
        const locatedErrors: Array<{
          name: ProcurementFormFieldName
          errors: string[]
        }> = []
        const unlocatedMessages: string[] = []

        backendError.response.fieldErrors.forEach((violation) => {
          const name = toFormFieldName(violation.field, itemCount)
          if (name) {
            locatedErrors.push({ name, errors: [violation.message] })
          } else {
            unlocatedMessages.push(violation.message)
          }
        })

        form.setFields(locatedErrors)
        setPageError(
          unlocatedMessages.length > 0
            ? `部分校验错误无法定位到字段：${unlocatedMessages.join('；')}`
            : '服务端校验未通过，请检查标注字段。',
        )
        return
      }

      setPageError(getApiErrorMessage(error))
    }
  }

  const handleCreateAnother = () => {
    setCreatedRequest(null)
    setPageError(null)
    form.resetFields()
  }

  if (createdRequest) {
    return (
      <CreatedProcurementRequestResult
        request={createdRequest}
        onCreateAnother={handleCreateAnother}
      />
    )
  }

  return (
    <Card>
      <Typography.Title level={2}>创建采购申请</Typography.Title>
      <Typography.Paragraph type="secondary">
        保存后申请处于 DRAFT。预计总额和行金额由服务端计算，页面不会提交或计算权威汇总金额。
      </Typography.Paragraph>

      {pageError ? (
        <Alert
          className="form-alert"
          type="error"
          showIcon
          title="创建失败"
          description={pageError}
        />
      ) : null}

      <Form<ProcurementRequestFormValue>
        form={form}
        layout="vertical"
        initialValues={initialFormValue()}
        disabled={isLoading}
        onFinish={(values) => void handleSubmit(values)}
      >
        <ProcurementRequestFields />
        <ProcurementItemsFields isLoading={isLoading} />

        <div className="form-actions">
          <Button
            type="primary"
            htmlType="submit"
            loading={isLoading}
            disabled={isLoading}
          >
            创建草稿
          </Button>
        </div>
      </Form>
    </Card>
  )
}
