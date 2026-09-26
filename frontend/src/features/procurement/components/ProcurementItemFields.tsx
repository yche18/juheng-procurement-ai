import { Col, Form, Input, Row, Select } from 'antd'

import { categoryLabels, decimalValidator } from '../model/procurementForm'
import { categoryCodes } from '../types/procurement'

interface ProcurementItemFieldsProps {
  fieldName: number
}

export function ProcurementItemFields({ fieldName }: ProcurementItemFieldsProps) {
  return (
    <>
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Form.Item
            label="名称"
            name={[fieldName, 'name']}
            rules={[
              {
                required: true,
                whitespace: true,
                message: '请输入采购项名称',
              },
              { max: 200, message: '采购项名称不能超过 200 个字符' },
            ]}
          >
            <Input maxLength={200} showCount />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item
            label="品类"
            name={[fieldName, 'categoryCode']}
            rules={[{ required: true, message: '请选择品类' }]}
          >
            <Select
              virtual={false}
              options={categoryCodes.map((code) => ({
                value: code,
                label: `${categoryLabels[code]}（${code}）`,
              }))}
            />
          </Form.Item>
        </Col>
      </Row>

      <Form.Item
        label="规格说明"
        name={[fieldName, 'specification']}
        rules={[
          {
            required: true,
            whitespace: true,
            message: '请输入规格说明',
          },
          { max: 2000, message: '规格说明不能超过 2000 个字符' },
        ]}
      >
        <Input.TextArea rows={3} maxLength={2000} showCount />
      </Form.Item>

      <ProcurementNumericFields fieldName={fieldName} />
    </>
  )
}

function ProcurementNumericFields({ fieldName }: ProcurementItemFieldsProps) {
  return (
    <Row gutter={16}>
      <QuantityField fieldName={fieldName} />
      <UnitField fieldName={fieldName} />
      <PriceField fieldName={fieldName} />
    </Row>
  )
}

function QuantityField({ fieldName }: ProcurementItemFieldsProps) {
  return (
    <Col xs={24} md={8}>
      <Form.Item
        label="数量"
        name={[fieldName, 'quantity']}
        rules={[
          { required: true, message: '请输入数量' },
          { validator: decimalValidator('数量', 7, 4, true) },
        ]}
      >
        <Input inputMode="decimal" placeholder="例如 2.5" />
      </Form.Item>
    </Col>
  )
}

function UnitField({ fieldName }: ProcurementItemFieldsProps) {
  return (
    <Col xs={24} md={8}>
      <Form.Item
        label="计量单位"
        name={[fieldName, 'unit']}
        rules={[
          {
            required: true,
            whitespace: true,
            message: '请输入计量单位',
          },
          { max: 40, message: '计量单位不能超过 40 个字符' },
        ]}
      >
        <Input maxLength={40} placeholder="例如 台" />
      </Form.Item>
    </Col>
  )
}

function PriceField({ fieldName }: ProcurementItemFieldsProps) {
  return (
    <Col xs={24} md={8}>
      <Form.Item
        label="预计单价（CNY）"
        name={[fieldName, 'estimatedUnitPrice']}
        rules={[
          { required: true, message: '请输入预计单价' },
          { validator: decimalValidator('预计单价', 8, 2, false) },
        ]}
      >
        <Input inputMode="decimal" placeholder="例如 8999.00" />
      </Form.Item>
    </Col>
  )
}
