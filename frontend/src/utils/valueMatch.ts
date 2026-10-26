import { Modal } from 'antd'
import type { ValueMatchResult } from '../types'

function pct(n: number) {
  return `${Math.round(n * 100)}%`
}

export function formatValueMatch(match: ValueMatchResult) {
  return [
    `${match.sourceTableName}.${match.sourceColumnName} ↔ ${match.targetTableName}.${match.targetColumnName}`,
    `源列抽样值在目标全表存在：${pct(match.sourceInTarget)}（源抽样 ${match.sourceDistinct} 个不同值）`,
    `目标列抽样值在源全表存在：${pct(match.targetInSource)}（目标抽样 ${match.targetDistinct} 个不同值）`,
    `判定匹配率：${pct(match.bestInclusion)}；阈值：${pct(match.threshold)}；${match.passed ? '通过' : '未通过'}`,
    match.summary,
    match.suggestedDirection ? `建议方向：${match.suggestedDirection}` : '',
  ]
    .filter(Boolean)
    .join('\n')
}

/** Ask user whether to force-save when value match fails. Resolves true to force. */
export function askForceAfterMatchFail(match: ValueMatchResult, actionLabel: string): Promise<boolean> {
  const text =
    formatValueMatch(match) +
    `\n\n抽样值在对方全表中的存在比例未达到阈值。仍${actionLabel}可能表示错误关联。`
  return new Promise((resolve) => {
    Modal.confirm({
      title: '数据校验未通过',
      width: 520,
      content: text,
      okText: `仍要${actionLabel}`,
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
}
