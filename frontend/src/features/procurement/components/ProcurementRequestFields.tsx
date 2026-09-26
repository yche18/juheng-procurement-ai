import { Col, Form, Input, Row } from 'antd'

export function ProcurementRequestFields() {
  return (
    <>
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <Form.Item
            label="标题"
            name="title"
            rules={[
              { required: true, whitespace: true, message: '请输入标题' },
              { max: 200, message: '标题不能超过 200 个字符' },
            ]}
          >
            <Input maxLength={200} showCount />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item
            label="申请部门"
            name="department"
            rules={[
              {
                required: true,
                whitespace: true,
                message: '请输入申请部门',
              },
              { max: 100, message: '申请部门不能超过 100 个字符' },
            ]}
          >
            <Input maxLength={100} showCount />
          </Form.Item>
        </Col>
      </Row>

      <Form.Item
        label="采购目的"
        name="purpose"
        rules={[
          { required: true, whitespace: true, message: '请输入采购目的' },
          { max: 2000, message: '采购目的不能超过 2000 个字符' },
        ]}
      >
        <Input.TextArea rows={4} maxLength={2000} showCount />
      </Form.Item>

      <Form.Item
        label="期望交付日期"
        name="expectedDeliveryDate"
        rules={[{ required: true, message: '请选择期望交付日期' }]}
      >
        <Input type="date" />
      </Form.Item>
    </>
  )
}
