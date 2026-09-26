import {
  Button,
  Card,
  Descriptions,
  Result,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'

import { categoryLabels, formatCny } from '../model/procurementForm'
import type {
  CategoryCode,
  CreateProcurementRequestResponse,
  ProcurementItemResponse,
} from '../types/procurement'

interface CreatedProcurementRequestResultProps {
  request: CreateProcurementRequestResponse
  onCreateAnother: () => void
}

const itemColumns: ColumnsType<ProcurementItemResponse> = [
  { title: '序号', dataIndex: 'lineNumber', width: 72 },
  { title: '名称', dataIndex: 'name' },
  {
    title: '品类',
    dataIndex: 'categoryCode',
    render: (value: CategoryCode) => categoryLabels[value] ?? value,
  },
  {
    title: '数量',
    dataIndex: 'quantity',
    render: (value: number, item) => `${String(value)} ${item.unit}`,
  },
  {
    title: '预计单价',
    dataIndex: 'estimatedUnitPrice',
    render: (value: number) => formatCny(value),
  },
  {
    title: '服务端行金额',
    dataIndex: 'estimatedLineTotal',
    render: (value: number) => formatCny(value),
  },
]

export function CreatedProcurementRequestResult({
  request,
  onCreateAnother,
}: CreatedProcurementRequestResultProps) {
  return (
    <Card>
      <Result
        status="success"
        title="采购申请草稿已创建"
        subTitle={`业务编号：${request.businessNumber}`}
        extra={
          <Button type="primary" onClick={onCreateAnother}>
            继续创建另一份申请
          </Button>
        }
      />
      <Descriptions
        bordered
        column={{ xs: 1, sm: 2 }}
        items={[
          {
            key: 'businessNumber',
            label: '业务编号',
            children: request.businessNumber,
          },
          {
            key: 'status',
            label: '状态',
            children: <Tag color="blue">{request.status}</Tag>,
          },
          {
            key: 'version',
            label: '版本',
            children: request.version,
          },
          {
            key: 'estimatedTotal',
            label: '服务端预计总额',
            children: formatCny(request.estimatedTotal),
          },
        ]}
      />
      <Typography.Title level={3} className="result-section-title">
        服务端采购项结果
      </Typography.Title>
      <Table
        rowKey="id"
        columns={itemColumns}
        dataSource={request.items}
        pagination={false}
        scroll={{ x: 760 }}
      />
    </Card>
  )
}
