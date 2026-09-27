import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Skeleton,
  Space,
  Tag,
  Timeline,
  Typography,
} from 'antd'

import {
  getApiErrorMessage,
  isBackendErrorCode,
} from '../../../shared/api/apiError'
import { formatDateTime } from '../../../shared/model/displayFormatters'
import { useGetProcurementAuditTrailQuery } from '../api/auditApi'
import { isProcurementAuditTrail } from '../model/auditContract'
import {
  getAuditActionDisplay,
  getAuditResultDisplay,
  getAuditTargetTypeDisplay,
} from '../model/auditPresentation'
import type { AuditEventResponse } from '../types/audit'

interface AuditTimelineProps {
  requestId: string
}

function AuditEventDetails({ event }: { event: AuditEventResponse }) {
  const action = getAuditActionDisplay(event.action)
  const targetType = getAuditTargetTypeDisplay(event.targetType)
  const result = getAuditResultDisplay(event.result)

  return (
    <div className={'audit-event'}>
      <div className={'audit-event-header'}>
        <Typography.Title level={5}>{action.label}</Typography.Title>
        <Typography.Text type={'secondary'}>
          {formatDateTime(event.timestamp)}
        </Typography.Text>
      </div>
      {!action.known ? (
        <Typography.Paragraph type={'secondary'}>
          该动作代码不在当前前端映射中，页面保留服务端原始值。
        </Typography.Paragraph>
      ) : null}
      <Descriptions
        size={'small'}
        column={{ xs: 1, sm: 2 }}
        items={[
          { key: 'actorId', label: '操作者', children: event.actorId },
          {
            key: 'result',
            label: '结果',
            children: (
              <Tag color={result.known ? 'success' : 'default'}>
                {result.label}
              </Tag>
            ),
          },
          {
            key: 'targetType',
            label: '目标类型',
            children: targetType.label,
            span: 'filled',
          },
          {
            key: 'targetId',
            label: '目标 ID',
            children: event.targetId,
            span: 'filled',
          },
          {
            key: 'requestIdentifier',
            label: '请求/幂等标识',
            children: event.requestIdentifier,
            span: 'filled',
          },
        ]}
      />
    </div>
  )
}

export function AuditTimeline({ requestId }: AuditTimelineProps) {
  const auditQuery = useGetProcurementAuditTrailQuery(requestId, {
    skip: requestId.length === 0,
  })

  if (auditQuery.isLoading) {
    return <Skeleton active paragraph={{ rows: 5 }} />
  }

  if (auditQuery.error) {
    const hidden = isBackendErrorCode(
      auditQuery.error,
      'RESOURCE_NOT_FOUND',
    )
    return (
      <Alert
        type={'error'}
        showIcon
        title={hidden ? '审计轨迹不存在或不可访问' : '审计轨迹加载失败'}
        description={
          hidden
            ? '服务端不会区分申请不存在与当前用户无权访问。'
            : getApiErrorMessage(auditQuery.error)
        }
        action={
          <Button onClick={() => void auditQuery.refetch()}>重试</Button>
        }
      />
    )
  }

  if (!isProcurementAuditTrail(auditQuery.data)) {
    return (
      <Alert
        type={'error'}
        showIcon
        title={'服务响应与审计轨迹 Contract 不一致'}
        description={'响应字段缺失或类型不正确，页面未将其作为审计事实展示。'}
      />
    )
  }

  const auditTrail = auditQuery.data
  if (auditTrail.procurementRequestId !== requestId) {
    return (
      <Alert
        type={'error'}
        showIcon
        title={'审计轨迹关联的申请不一致'}
        description={'为避免展示其他申请的审计事实，本次响应已被隐藏。'}
      />
    )
  }

  return (
    <>
      <Alert
        className={'page-alert'}
        type={'info'}
        showIcon
        title={'审计轨迹是服务端只读事实'}
        description={'页面严格保留服务端返回顺序，不提供创建、修改或删除审计事件的入口。'}
      />
      {auditTrail.events.length === 0 ? (
        <Empty description={'暂无审计事件。'} />
      ) : (
        <Timeline
          items={auditTrail.events.map((event) => ({
            key: event.id,
            color: event.result === 'SUCCESS' ? 'green' : 'gray',
            content: <AuditEventDetails event={event} />,
          }))}
        />
      )}
      <Space>
        <Button
          loading={auditQuery.isFetching}
          onClick={() => void auditQuery.refetch()}
        >
          刷新审计轨迹
        </Button>
        <Typography.Text type={'secondary'}>
          共 {auditTrail.events.length} 条服务端事件
        </Typography.Text>
      </Space>
    </>
  )
}
