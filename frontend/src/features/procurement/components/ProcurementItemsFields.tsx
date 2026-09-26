import { Button, Card, Form, Space } from 'antd'

import {
  emptyItem,
  type ProcurementItemFormValue,
} from '../model/procurementForm'
import { ProcurementItemFields } from './ProcurementItemFields'

interface ProcurementItemsFieldsProps {
  isLoading: boolean
}

export function ProcurementItemsFields({
  isLoading,
}: ProcurementItemsFieldsProps) {
  return (
    <Form.List
      name="items"
      rules={[
        {
          validator: (_rule, items: ProcurementItemFormValue[]) => {
            if (!items || items.length === 0) {
              return Promise.reject(new Error('至少需要一个采购项'))
            }
            if (items.length > 100) {
              return Promise.reject(new Error('采购项不能超过 100 个'))
            }
            return Promise.resolve()
          },
        },
      ]}
    >
      {(fields, { add, remove }, { errors }) => (
        <Space orientation="vertical" size="middle" className="full-width">
          {fields.map((field, index) => (
            <Card
              key={field.key}
              size="small"
              title={`采购项 ${index + 1}`}
              extra={
                <Button
                  danger
                  disabled={fields.length === 1 || isLoading}
                  onClick={() => remove(field.name)}
                >
                  删除
                </Button>
              }
            >
              <ProcurementItemFields fieldName={field.name} />
            </Card>
          ))}

          <Form.ErrorList errors={errors} />
          <Button
            type="dashed"
            block
            disabled={fields.length >= 100 || isLoading}
            onClick={() => add(emptyItem())}
          >
            添加采购项
          </Button>
        </Space>
      )}
    </Form.List>
  )
}
