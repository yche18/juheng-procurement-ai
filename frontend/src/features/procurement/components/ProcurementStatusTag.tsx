import { Tag } from 'antd'

import { getProcurementStatusDisplay } from '../model/procurementPresentation'

interface ProcurementStatusTagProps {
  status: string
}

export function ProcurementStatusTag({ status }: ProcurementStatusTagProps) {
  const display = getProcurementStatusDisplay(status)
  return <Tag color={display.color}>{display.label}</Tag>
}
