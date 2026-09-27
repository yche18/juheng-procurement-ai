import { Alert, Descriptions, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import {
  formatDateTime,
  formatMoney,
} from '../../../shared/model/displayFormatters'
import {
  getCategoryDisplay,
  isProcurementRequestStatus,
} from '../model/procurementPresentation'
import type {
  ProcurementItemResponse,
  ProcurementRequestDetailResponse,
} from '../types/procurement'
import { ProcurementStatusTag } from './ProcurementStatusTag'

function itemColumns(currency: string): ColumnsType<ProcurementItemResponse> {
  return [
    { title: '序号', dataIndex: 'lineNumber', width: 72 },
    { title: '名称', dataIndex: 'name' },
    {
      title: '品类',
      dataIndex: 'categoryCode',
      render: (value: string) => getCategoryDisplay(value),
    },
    { title: '规格说明', dataIndex: 'specification' },
    {
      title: '数量',
      dataIndex: 'quantity',
      render: (value: number, item) => `${String(value)} ${item.unit}`,
    },
    {
      title: '预计单价',
      dataIndex: 'estimatedUnitPrice',
      align: 'right',
      render: (value: number) => formatMoney(value, currency),
    },
    {
      title: '行金额',
      dataIndex: 'estimatedLineTotal',
      align: 'right',
      render: (value: number) => formatMoney(value, currency),
    },
  ]
}

interface ProcurementRequestReadOnlyDetailsProps {
  request: ProcurementRequestDetailResponse
}

export function ProcurementRequestReadOnlyDetails({
  request,
}: ProcurementRequestReadOnlyDetailsProps) {
  return (
    <>
      {!isProcurementRequestStatus(request.status) ? (
        <Alert
          className={'page-alert'}
          type={'warning'}
          showIcon
          title={'服务端返回了未知申请状态'}
          description={'页面保留原始代码，不会把它映射为任何已知业务状态。'}
        />
      ) : null}

      <Descriptions
        bordered
        column={{ xs: 1, sm: 2 }}
        items={[
          {
            key: 'status',
            label: '状态',
            children: <ProcurementStatusTag status={request.status} />,
          },
          { key: 'creatorId', label: '申请人', children: request.creatorId },
          { key: 'department', label: '申请部门', children: request.department },
          {
            key: 'expectedDeliveryDate',
            label: '期望交付日期',
            children: request.expectedDeliveryDate,
          },
          { key: 'currency', label: '币种', children: request.currency },
          {
            key: 'estimatedTotal',
            label: '服务端预计总额',
            children: formatMoney(request.estimatedTotal, request.currency),
          },
          { key: 'version', label: '版本', children: request.version },
          {
            key: 'createdAt',
            label: '创建时间',
            children: formatDateTime(request.createdAt),
          },
          {
            key: 'updatedAt',
            label: '更新时间',
            children: formatDateTime(request.updatedAt),
          },
          {
            key: 'purpose',
            label: '采购目的',
            children: request.purpose,
            span: 2,
          },
        ]}
      />

      <Typography.Title level={3} className={'detail-section-title'}>
        采购项
      </Typography.Title>
      <Table
        rowKey={'id'}
        columns={itemColumns(request.currency)}
        dataSource={request.items}
        pagination={false}
        scroll={{ x: 980 }}
      />
    </>
  )
}
