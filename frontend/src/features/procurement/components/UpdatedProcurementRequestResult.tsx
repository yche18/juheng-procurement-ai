import { Button, Card, Descriptions, Result, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'

import {
  formatMoney,
  getCategoryDisplay,
} from '../model/procurementPresentation'
import type {
  ProcurementItemResponse,
  ProcurementRequestDetailResponse,
} from '../types/procurement'
import { ProcurementStatusTag } from './ProcurementStatusTag'

interface UpdatedProcurementRequestResultProps {
  request: ProcurementRequestDetailResponse
}

function itemColumns(currency: string): ColumnsType<ProcurementItemResponse> {
  return [
    { title: '序号', dataIndex: 'lineNumber', width: 72 },
    { title: '名称', dataIndex: 'name' },
    {
      title: '品类',
      dataIndex: 'categoryCode',
      render: (value: string) => getCategoryDisplay(value),
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      render: (value: number, item) => `${String(value)} ${item.unit}`,
    },
    {
      title: '服务端行金额',
      dataIndex: 'estimatedLineTotal',
      align: 'right',
      render: (value: number) => formatMoney(value, currency),
    },
  ]
}

export function UpdatedProcurementRequestResult({
  request,
}: UpdatedProcurementRequestResultProps) {
  return (
    <Card>
      <Result
        status="success"
        title="采购申请草稿已保存"
        subTitle={`业务编号：${request.businessNumber}`}
        extra={
          <Space wrap>
            <Button type="primary">
              <Link to={`/requester/requests/${request.id}`}>查看申请详情</Link>
            </Button>
            <Button>
              <Link to="/requester/requests">返回我的申请</Link>
            </Button>
          </Space>
        }
      />
      <Descriptions
        bordered
        column={{ xs: 1, sm: 2 }}
        items={[
          {
            key: 'status',
            label: '服务端状态',
            children: <ProcurementStatusTag status={request.status} />,
          },
          { key: 'version', label: '新版本', children: request.version },
          {
            key: 'estimatedTotal',
            label: '服务端重算总额',
            children: formatMoney(request.estimatedTotal, request.currency),
          },
        ]}
      />
      <Typography.Title level={3} className="result-section-title">
        服务端采购项结果
      </Typography.Title>
      <Table
        rowKey="id"
        columns={itemColumns(request.currency)}
        dataSource={request.items}
        pagination={false}
        scroll={{ x: 760 }}
      />
    </Card>
  )
}
