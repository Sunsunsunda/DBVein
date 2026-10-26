import { Button, Descriptions, Drawer, Empty, Input, Space, Table, message } from 'antd'
import { useEffect, useState } from 'react'
import { metadataApi } from '../../api'
import type { ColumnMeta, ColumnProfile, Relation, TableDetail, TablePreview, TableProfile } from '../../types'

interface Props {
  tableDetail: TableDetail | null
  selectedColumn: ColumnMeta | null
  relations: Relation[]
  tableId: number | null
  onTableUpdated?: (table: TableDetail['table']) => void
  onColumnUpdated?: (column: ColumnMeta) => void
}

export function PropertyPanel({
  tableDetail,
  selectedColumn,
  relations,
  tableId,
  onTableUpdated,
  onColumnUpdated,
}: Props) {
  const [previewOpen, setPreviewOpen] = useState(false)
  const [preview, setPreview] = useState<TablePreview | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [tableProfile, setTableProfile] = useState<TableProfile | null>(null)
  const [columnProfile, setColumnProfile] = useState<ColumnProfile | null>(null)
  const [profileLoading, setProfileLoading] = useState(false)
  const [tableComment, setTableComment] = useState('')
  const [columnComment, setColumnComment] = useState('')
  const [savingTable, setSavingTable] = useState(false)
  const [savingColumn, setSavingColumn] = useState(false)

  useEffect(() => {
    setTableComment(tableDetail?.table.commentText ?? '')
    setTableProfile(null)
  }, [tableDetail?.table.id, tableDetail?.table.commentText])

  useEffect(() => {
    setColumnComment(selectedColumn?.commentText ?? '')
    setColumnProfile(null)
  }, [selectedColumn?.id, selectedColumn?.commentText])

  if (!tableDetail && !selectedColumn) {
    return (
      <div className="property-panel">
        <Empty description="选择表或字段查看详情" />
      </div>
    )
  }

  const tableRelations = relations.filter(
    (r) =>
      r.sourceTableId === tableId ||
      r.targetTableId === tableId ||
      r.sourceColumnId === selectedColumn?.id ||
      r.targetColumnId === selectedColumn?.id,
  )

  const loadPreview = async (p = 1) => {
    if (!tableId) return
    setPreviewLoading(true)
    try {
      const data = await metadataApi.preview(tableId, p, 50)
      setPreview(data)
      setPage(p)
      setPreviewOpen(true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '预览失败')
    } finally {
      setPreviewLoading(false)
    }
  }

  const loadTableProfile = async () => {
    if (!tableId) return
    setProfileLoading(true)
    try {
      const data = await metadataApi.tableProfile(tableId)
      setTableProfile(data)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '统计失败')
    } finally {
      setProfileLoading(false)
    }
  }

  const loadColumnProfile = async () => {
    if (!tableId || !selectedColumn) return
    setProfileLoading(true)
    try {
      const data = await metadataApi.columnProfile(tableId, selectedColumn.id)
      setColumnProfile(data)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '字段统计失败')
    } finally {
      setProfileLoading(false)
    }
  }

  const saveTableComment = async () => {
    if (!tableId) return
    setSavingTable(true)
    try {
      const updated = await metadataApi.updateTable(tableId, { commentText: tableComment })
      onTableUpdated?.(updated)
      message.success('表含义已保存')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSavingTable(false)
    }
  }

  const saveColumnComment = async () => {
    if (!tableId || !selectedColumn) return
    setSavingColumn(true)
    try {
      const updated = await metadataApi.updateColumn(tableId, selectedColumn.id, {
        commentText: columnComment,
      })
      onColumnUpdated?.(updated)
      message.success('字段含义已保存')
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSavingColumn(false)
    }
  }

  return (
    <div className="property-panel">
      {tableDetail && (
        <>
          <h3>表信息</h3>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="表名">{tableDetail.table.tableName}</Descriptions.Item>
            <Descriptions.Item label="Schema">{tableDetail.table.schemaName}</Descriptions.Item>
            <Descriptions.Item label="字典行数">{tableDetail.table.rowCount ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="字段数">{tableDetail.columns.length}</Descriptions.Item>
          </Descriptions>
          <div style={{ marginTop: 12 }}>
            <div style={{ marginBottom: 6, fontWeight: 600 }}>表含义 / 备注</div>
            <Input.TextArea
              rows={3}
              value={tableComment}
              onChange={(e) => setTableComment(e.target.value)}
              placeholder="例如：客户基础信息，供订单关联"
            />
            <Button
              size="small"
              type="primary"
              style={{ marginTop: 8 }}
              loading={savingTable}
              onClick={saveTableComment}
            >
              保存表含义
            </Button>
          </div>
          <Space style={{ marginTop: 12 }} wrap>
            <Button size="small" loading={previewLoading} onClick={() => loadPreview(1)}>
              数据预览
            </Button>
            <Button size="small" loading={profileLoading} onClick={loadTableProfile}>
              抽样统计
            </Button>
          </Space>
          {tableProfile && (
            <Descriptions column={1} size="small" bordered style={{ marginTop: 12 }}>
              <Descriptions.Item label="抽样上限">{tableProfile.sampleSize}</Descriptions.Item>
              <Descriptions.Item label="抽样行数">{tableProfile.liveSampleCount}</Descriptions.Item>
              <Descriptions.Item label="字典估计行数">{tableProfile.estimatedRowCount ?? '-'}</Descriptions.Item>
            </Descriptions>
          )}
        </>
      )}
      {selectedColumn && (
        <>
          <h3 style={{ marginTop: 16 }}>字段信息</h3>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="字段">{selectedColumn.columnName}</Descriptions.Item>
            <Descriptions.Item label="类型">
              {selectedColumn.dataType}
              {selectedColumn.length ? `(${selectedColumn.length})` : ''}
            </Descriptions.Item>
            <Descriptions.Item label="主键">{selectedColumn.primaryKey ? '是' : '否'}</Descriptions.Item>
            <Descriptions.Item label="可空">{selectedColumn.nullable ? '是' : '否'}</Descriptions.Item>
          </Descriptions>
          <div style={{ marginTop: 12 }}>
            <div style={{ marginBottom: 6, fontWeight: 600 }}>字段含义 / 备注</div>
            <Input.TextArea
              rows={3}
              value={columnComment}
              onChange={(e) => setColumnComment(e.target.value)}
              placeholder="例如：客户编号，关联 customers.id"
            />
            <Button
              size="small"
              type="primary"
              style={{ marginTop: 8 }}
              loading={savingColumn}
              onClick={saveColumnComment}
            >
              保存字段含义
            </Button>
          </div>
          <Button
            size="small"
            style={{ marginTop: 12 }}
            loading={profileLoading}
            onClick={loadColumnProfile}
          >
            字段抽样分析
          </Button>
          {columnProfile && (
            <Descriptions column={1} size="small" bordered style={{ marginTop: 12 }}>
              <Descriptions.Item label="抽样行数">{columnProfile.sampledRows}</Descriptions.Item>
              <Descriptions.Item label="NULL">{columnProfile.nullCount}</Descriptions.Item>
              <Descriptions.Item label="非空">{columnProfile.nonNullCount}</Descriptions.Item>
              <Descriptions.Item label="唯一值">{columnProfile.distinctCount}</Descriptions.Item>
              <Descriptions.Item label="NULL率">
                {columnProfile.nullRate == null ? '-' : `${(columnProfile.nullRate * 100).toFixed(2)}%`}
              </Descriptions.Item>
            </Descriptions>
          )}
        </>
      )}
      {tableRelations.length > 0 && (
        <>
          <h3 style={{ marginTop: 16 }}>相关关系</h3>
          <ul className="relation-list">
            {tableRelations.map((r) => (
              <li key={r.id}>
                <span className={`rel-type ${r.relationType}`}>{r.relationType}</span>
                {r.sourceTableName}.{r.sourceColumnName} → {r.targetTableName}.{r.targetColumnName}
              </li>
            ))}
          </ul>
        </>
      )}

      <Drawer
        title={tableDetail ? `数据预览 - ${tableDetail.table.tableName}` : '数据预览'}
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        placement="bottom"
        height="42vh"
        mask={false}
        styles={{ body: { padding: '8px 16px' } }}
      >
        <Space style={{ marginBottom: 12 }}>
          <Button disabled={page <= 1 || previewLoading} onClick={() => loadPreview(page - 1)}>
            上一页
          </Button>
          <span>第 {page} 页</span>
          <Button disabled={!preview?.hasMore || previewLoading} onClick={() => loadPreview(page + 1)}>
            下一页
          </Button>
        </Space>
        <Table
          size="small"
          loading={previewLoading}
          rowKey={(_, i) => String(i)}
          scroll={{ x: true, y: 'calc(42vh - 150px)' }}
          pagination={false}
          dataSource={preview?.rows ?? []}
          columns={(preview?.columns ?? []).map((c) => ({
            title: c,
            dataIndex: c,
            key: c,
            ellipsis: true,
            width: 140,
            render: (v: string | null) => (v == null ? <span style={{ color: '#bbb' }}>NULL</span> : v),
          }))}
        />
      </Drawer>
    </div>
  )
}
