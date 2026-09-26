import {
  Alert,
  Button,
  Card,
  Form,
  Modal,
  Result,
  Skeleton,
  Space,
  Typography,
} from 'antd'
import { useCallback, useState } from 'react'
import {
  Link,
  useBeforeUnload,
  useBlocker,
  useParams,
} from 'react-router-dom'

import {
  getApiErrorMessage,
  isBackendErrorCode,
} from '../../../shared/api/apiError'
import {
  useGetProcurementRequestQuery,
  useUpdateProcurementRequestMutation,
} from '../api/procurementApi'
import { ProcurementItemsFields } from '../components/ProcurementItemsFields'
import { ProcurementRequestFields } from '../components/ProcurementRequestFields'
import { ProcurementStatusTag } from '../components/ProcurementStatusTag'
import { UpdatedProcurementRequestResult } from '../components/UpdatedProcurementRequestResult'
import { isProcurementRequestDetail } from '../model/procurementContract'
import {
  asBackendError,
  partitionProcurementFormErrors,
  toEditableFormValue,
  toUpdateRequest,
  type ProcurementRequestFormValue,
} from '../model/procurementForm'
import type { ProcurementRequestDetailResponse } from '../types/procurement'

interface BusinessConflictState {
  latestRequest?: ProcurementRequestDetailResponse
  refreshError?: string
}

type ConcurrentState = 'pending' | 'retained' | null

interface DraftEditorProps {
  initialRequest: ProcurementRequestDetailResponse
  reloadRequest: () => Promise<ProcurementRequestDetailResponse>
}

function DraftEditor({ initialRequest, reloadRequest }: DraftEditorProps) {
  const [form] = Form.useForm<ProcurementRequestFormValue>()
  const [updateRequest, { isLoading: isSaving }] =
    useUpdateProcurementRequestMutation()
  const [snapshot, setSnapshot] =
    useState<ProcurementRequestDetailResponse>(initialRequest)
  const [savedRequest, setSavedRequest] =
    useState<ProcurementRequestDetailResponse | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)
  const [concurrentState, setConcurrentState] =
    useState<ConcurrentState>(null)
  const [reloadConfirmationOpen, setReloadConfirmationOpen] = useState(false)
  const [reloadError, setReloadError] = useState<string | null>(null)
  const [isReloading, setIsReloading] = useState(false)
  const [businessConflict, setBusinessConflict] =
    useState<BusinessConflictState | null>(null)
  const [isResolvingBusinessConflict, setIsResolvingBusinessConflict] =
    useState(false)
  const [isDirty, setIsDirty] = useState(false)

  const shouldBlockNavigation =
    isDirty && savedRequest === null && businessConflict === null
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      shouldBlockNavigation &&
      currentLocation.pathname !== nextLocation.pathname,
  )

  useBeforeUnload(
    useCallback(
      (event) => {
        if (shouldBlockNavigation) {
          event.preventDefault()
          event.returnValue = ''
        }
      },
      [shouldBlockNavigation],
    ),
  )

  const stopForBusinessConflict = async () => {
    setIsResolvingBusinessConflict(true)
    setConcurrentState(null)
    setPageError(null)

    try {
      const latest = await reloadRequest()
      setBusinessConflict({
        latestRequest: isProcurementRequestDetail(latest) ? latest : undefined,
        refreshError: isProcurementRequestDetail(latest)
          ? undefined
          : '服务端返回的最新申请不符合详情 Contract。',
      })
    } catch (error) {
      setBusinessConflict({ refreshError: getApiErrorMessage(error) })
    } finally {
      setIsDirty(false)
      setIsResolvingBusinessConflict(false)
    }
  }

  const handleSubmit = async (values: ProcurementRequestFormValue) => {
    if (!snapshot || concurrentState !== null) {
      return
    }

    setPageError(null)
    setReloadError(null)

    try {
      const result = await updateRequest({
        requestId: snapshot.id,
        body: toUpdateRequest(values, snapshot.version),
      }).unwrap()
      if (!isProcurementRequestDetail(result) || result.id !== snapshot.id) {
        setPageError('服务端响应与申请详情 Contract 不一致，页面未确认本次保存。')
        return
      }

      setIsDirty(false)
      setSavedRequest(result)
    } catch (error) {
      const backendError = asBackendError(error)
      if (backendError?.response.code === 'VALIDATION_FAILED') {
        const itemCount = form.getFieldsValue().items?.length ?? 0
        const { locatedErrors, unlocatedMessages } =
          partitionProcurementFormErrors(
            backendError.response.fieldErrors,
            itemCount,
          )
        form.setFields(locatedErrors)
        setPageError(
          unlocatedMessages.length > 0
            ? `部分校验错误无法定位到字段：${unlocatedMessages.join('；')}`
            : '服务端校验未通过，请检查标注字段。',
        )
        return
      }

      if (backendError?.response.code === 'CONCURRENT_MODIFICATION') {
        setConcurrentState('pending')
        return
      }

      if (backendError?.response.code === 'BUSINESS_CONFLICT') {
        await stopForBusinessConflict()
        return
      }

      setPageError(getApiErrorMessage(error))
    }
  }

  const handleReloadLatest = async () => {
    setIsReloading(true)
    setReloadError(null)

    try {
      const latest = await reloadRequest()
      if (!isProcurementRequestDetail(latest)) {
        setReloadError('服务端返回的最新申请不符合详情 Contract。')
        return
      }
      if (latest.status !== 'DRAFT') {
        setReloadConfirmationOpen(false)
        setBusinessConflict({ latestRequest: latest })
        setIsDirty(false)
        setConcurrentState(null)
        return
      }

      const editableValue = toEditableFormValue(latest)
      if (!editableValue) {
        setReloadError('最新申请包含页面无法识别的品类，不能安全地继续编辑。')
        return
      }

      form.resetFields()
      form.setFieldsValue(editableValue)
      setSnapshot(latest)
      setConcurrentState(null)
      setIsDirty(false)
      setReloadConfirmationOpen(false)
    } catch (error) {
      setReloadError(getApiErrorMessage(error))
    } finally {
      setIsReloading(false)
    }
  }

  if (savedRequest) {
    return <UpdatedProcurementRequestResult request={savedRequest} />
  }

  if (businessConflict) {
    return (
      <Card>
        <Result
          status="warning"
          title="申请已不再允许编辑"
          subTitle="服务端报告申请状态已变化。页面已停止编辑，不会用本地内容覆盖最新状态。"
          extra={
            <Space wrap>
              <Button type="primary">
                <Link to={`/requester/requests/${snapshot.id}`}>
                  查看最新详情
                </Link>
              </Button>
              <Button>
                <Link to="/requester/requests">返回我的申请</Link>
              </Button>
            </Space>
          }
        />
        {businessConflict.latestRequest ? (
          <Alert
            type="info"
            showIcon
            title="已刷新服务端状态"
            description={
              <Space>
                <span>当前状态：</span>
                <ProcurementStatusTag
                  status={businessConflict.latestRequest.status}
                />
                <span>版本：{businessConflict.latestRequest.version}</span>
              </Space>
            }
          />
        ) : null}
        {businessConflict.refreshError ? (
          <Alert
            type="error"
            showIcon
            title="最新状态刷新失败"
            description={businessConflict.refreshError}
          />
        ) : null}
      </Card>
    )
  }

  if (isResolvingBusinessConflict) {
    return (
      <Card>
        <Skeleton active title paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  const initialValues = toEditableFormValue(snapshot)
  if (!initialValues) {
    return null
  }

  return (
    <Card>
      <div className="page-heading-row">
        <div>
          <Typography.Title level={2}>编辑采购申请草稿</Typography.Title>
          <Typography.Text type="secondary">
            {snapshot.businessNumber} · 当前版本 {snapshot.version}
          </Typography.Text>
        </div>
        <Button>
          <Link to={`/requester/requests/${snapshot.id}`}>返回申请详情</Link>
        </Button>
      </div>

      <Typography.Paragraph type="secondary">
        保存时会提交当前完整草稿和版本号；总额及行金额由服务端重新计算。
      </Typography.Paragraph>

      {pageError ? (
        <Alert
          className="form-alert"
          type="error"
          showIcon
          title="保存失败"
          description={pageError}
        />
      ) : null}

      {concurrentState ? (
        <Alert
          className="form-alert"
          type="warning"
          showIcon
          title="草稿已被其他操作更新"
          description={
            concurrentState === 'retained'
              ? '当前页面内容已保留，且不会自动覆盖服务端。要继续保存，请重新加载最新版本后再修改。'
              : '请选择保留当前内容供复制，或确认放弃当前内容并重新加载最新版本。'
          }
          action={
            <Space wrap>
              {concurrentState === 'pending' ? (
                <Button onClick={() => setConcurrentState('retained')}>
                  保留当前内容
                </Button>
              ) : null}
              <Button
                type="primary"
                onClick={() => {
                  setReloadError(null)
                  setReloadConfirmationOpen(true)
                }}
              >
                重新加载最新版本
              </Button>
            </Space>
          }
        />
      ) : null}

      <Form<ProcurementRequestFormValue>
        form={form}
        layout="vertical"
        initialValues={initialValues}
        disabled={isSaving || isResolvingBusinessConflict}
        onValuesChange={() => {
          setIsDirty(true)
          setPageError(null)
        }}
        onFinish={(values) => void handleSubmit(values)}
      >
        <ProcurementRequestFields />
        <ProcurementItemsFields
          isLoading={isSaving || isResolvingBusinessConflict}
        />

        <div className="form-actions">
          <Button
            type="primary"
            htmlType="submit"
            loading={isSaving || isResolvingBusinessConflict}
            disabled={
              isSaving ||
              isResolvingBusinessConflict ||
              concurrentState !== null
            }
          >
            保存草稿
          </Button>
        </div>
      </Form>

      <Modal
        title="重新加载最新版本？"
        open={reloadConfirmationOpen}
        okText="放弃当前内容并重新加载"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        confirmLoading={isReloading}
        closable={!isReloading}
        mask={{ closable: !isReloading }}
        onCancel={() => {
          if (!isReloading) {
            setReloadConfirmationOpen(false)
            setReloadError(null)
          }
        }}
        onOk={() => void handleReloadLatest()}
      >
        <Typography.Paragraph>
          重新加载会丢弃当前页面中尚未保存的内容，并使用服务端最新完整快照替换表单。
        </Typography.Paragraph>
        {reloadError ? (
          <Alert type="error" showIcon description={reloadError} />
        ) : null}
      </Modal>

      <Modal
        title="有未保存的修改"
        open={blocker.state === 'blocked'}
        okText="放弃修改并离开"
        cancelText="继续编辑"
        okButtonProps={{ danger: true }}
        onCancel={() => {
          if (blocker.state === 'blocked') {
            blocker.reset()
          }
        }}
        onOk={() => {
          if (blocker.state === 'blocked') {
            blocker.proceed()
          }
        }}
      >
        当前表单有未保存的修改。离开后，这些内容将不会保留。
      </Modal>
    </Card>
  )
}

export function EditProcurementRequestPage() {
  const { requestId = '' } = useParams()
  const requestQuery = useGetProcurementRequestQuery(requestId, {
    skip: requestId.length === 0,
  })

  if (requestQuery.isLoading) {
    return (
      <Card>
        <Skeleton active title paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  if (requestQuery.error && requestQuery.data === undefined) {
    const notFound = isBackendErrorCode(
      requestQuery.error,
      'RESOURCE_NOT_FOUND',
    )
    return (
      <Card>
        <Result
          status={notFound ? '404' : 'error'}
          title={notFound ? '申请不存在或不可访问' : '申请加载失败'}
          subTitle={
            notFound
              ? '服务端不会区分申请不存在与当前用户无权访问。'
              : getApiErrorMessage(requestQuery.error)
          }
          extra={
            notFound ? (
              <Button type="primary">
                <Link to="/requester/requests">返回我的申请</Link>
              </Button>
            ) : (
              <Button type="primary" onClick={() => void requestQuery.refetch()}>
                重试
              </Button>
            )
          }
        />
      </Card>
    )
  }

  const loadedRequest = isProcurementRequestDetail(requestQuery.data)
    ? requestQuery.data
    : undefined
  if (!loadedRequest) {
    return (
      <Card>
        <Result
          status="error"
          title="服务响应与申请详情 Contract 不一致"
          subTitle="响应字段缺失或类型不正确，页面未将其作为可编辑申请使用。"
          extra={
            <Button type="primary" onClick={() => void requestQuery.refetch()}>
              重试
            </Button>
          }
        />
      </Card>
    )
  }

  if (loadedRequest.status !== 'DRAFT') {
    return (
      <Card>
        <Result
          status="info"
          title="当前申请不可编辑"
          subTitle="只有 DRAFT 状态的采购申请可以编辑。"
          extra={
            <Space wrap>
              <ProcurementStatusTag status={loadedRequest.status} />
              <Button type="primary">
                <Link to={`/requester/requests/${requestId}`}>查看申请详情</Link>
              </Button>
            </Space>
          }
        />
      </Card>
    )
  }

  if (!toEditableFormValue(loadedRequest)) {
    return (
      <Card>
        <Result
          status="error"
          title="申请包含页面无法安全编辑的数据"
          subTitle="采购项中存在未知品类。页面不会猜测或改写这些字段。"
          extra={
            <Button type="primary">
              <Link to={`/requester/requests/${requestId}`}>查看申请详情</Link>
            </Button>
          }
        />
      </Card>
    )
  }

  return (
    <DraftEditor
      initialRequest={loadedRequest}
      reloadRequest={() => requestQuery.refetch().unwrap()}
    />
  )
}
