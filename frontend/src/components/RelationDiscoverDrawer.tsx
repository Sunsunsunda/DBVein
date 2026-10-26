import { Button, Drawer, List, Space, Tag, Typography, message } from 'antd'
import { CheckOutlined, CloseOutlined } from '@ant-design/icons'
import { useState } from 'react'
import { analysisApi } from '../api'
import type { Relation } from '../types'
import { askForceAfterMatchFail } from '../utils/valueMatch'

interface Props {
  open: boolean
  projectId: number
  pending: Relation[]
  onClose: () => void
  onChanged: (relations: Relation[]) => void
  allRelations: Relation[]
}

export function RelationDiscoverDrawer({
  open,
  projectId,
  pending,
  onClose,
  onChanged,
}: Props) {
  const [busy, setBusy] = useState(false)

  const reload = async () => {
    const rels = await analysisApi.relations(projectId)
    onChanged(rels)
  }

  const confirmOne = async (r: Relation) => {
    setBusy(true)
    try {
      try {
        await analysisApi.confirmRelation(projectId, r.id, false)
        await reload()
        message.success('已确认（数据校验通过）')
        return
      } catch {
        // fall through to validate + force prompt
      }
      const match = await analysisApi.validateRelation(projectId, {
        sourceColumnId: r.sourceColumnId,
        targetColumnId: r.targetColumnId,
      })
      if (match.passed) {
        await analysisApi.confirmRelation(projectId, r.id, false)
        await reload()
        message.success('已确认（数据校验通过）')
        return
      }
      const force = await askForceAfterMatchFail(match, '确认')
      if (!force) return
      await analysisApi.confirmRelation(projectId, r.id, true)
      await reload()
      message.warning('已强制确认')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '确认失败')
    } finally {
      setBusy(false)
    }
  }

  const ignoreOne = async (r: Relation) => {
    setBusy(true)
    try {
      await analysisApi.ignoreRelation(projectId, r.id)
      await reload()
      message.success('已忽略')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '忽略失败')
    } finally {
      setBusy(false)
    }
  }

  const confirmAll = async () => {
    const ids = pending.map((r) => r.id)
    if (ids.length === 0) return
    setBusy(true)
    try {
      const res = await analysisApi.batchConfirmRelations(projectId, ids)
      await reload()
      message.success(`已确认 ${res.updated} 条（未通过数据校验的会跳过）`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '全部确认失败')
    } finally {
      setBusy(false)
    }
  }

  const ignoreAll = async () => {
    const ids = pending.map((r) => r.id)
    if (ids.length === 0) return
    setBusy(true)
    try {
      const res = await analysisApi.batchIgnoreRelations(projectId, ids)
      await reload()
      message.success(`已忽略 ${res.updated} 条`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '全部忽略失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer
      title={`待确认关系 (${pending.length})`}
      open={open}
      onClose={onClose}
      width={480}
      extra={
        <Space>
          <Button size="small" onClick={confirmAll} disabled={pending.length === 0 || busy} loading={busy}>
            全部确认
          </Button>
          <Button size="small" danger onClick={ignoreAll} disabled={pending.length === 0 || busy} loading={busy}>
            全部忽略
          </Button>
        </Space>
      }
    >
      <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
        确认前会抽样比对两侧字段值（如 REGIONNO 是否对得上）。未通过时可强制确认。
      </Typography.Paragraph>
      <List
        dataSource={pending}
        locale={{ emptyText: '暂无待确认关系，可先把表拖到画布再点「关系发现」' }}
        renderItem={(r) => (
          <List.Item
            actions={[
              <Button
                key="ok"
                type="link"
                icon={<CheckOutlined />}
                disabled={busy}
                onClick={() => confirmOne(r)}
              >
                确认
              </Button>,
              <Button
                key="no"
                type="link"
                danger
                icon={<CloseOutlined />}
                disabled={busy}
                onClick={() => ignoreOne(r)}
              >
                忽略
              </Button>,
            ]}
          >
            <List.Item.Meta
              title={
                <span>
                  {r.sourceTableName}.{r.sourceColumnName}
                  <span style={{ margin: '0 8px', color: '#999' }}>→</span>
                  {r.targetTableName}.{r.targetColumnName}
                </span>
              }
              description={
                <Space wrap size={[4, 4]}>
                  <Tag color="orange">置信度 {r.confidence ?? '-'}</Tag>
                  <Tag>{r.relationType}</Tag>
                  <span style={{ color: '#888', fontSize: 12 }}>{r.remark}</span>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </Drawer>
  )
}
