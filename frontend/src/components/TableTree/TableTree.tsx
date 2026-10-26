import { Button, Input, Modal, Popconfirm, Select, Space, Tree, message } from 'antd'
import { DeleteOutlined, EditOutlined, FilterOutlined, TableOutlined } from '@ant-design/icons'
import { useCallback, useMemo, useState } from 'react'
import { metadataApi } from '../../api'
import type { TableMeta } from '../../types'

type RowFilter = 'all' | 'nonzero' | 'zero' | 'unknown' | 'on_canvas'

interface Props {
  tables: TableMeta[]
  schemas: string[]
  selectedSchema: string | null
  selectedTableId?: number | null
  canvasTableIds: Set<number>
  onSchemaChange: (schema: string | null) => void
  onDropTable?: (tableId: number) => void
  onSelectTable?: (tableId: number) => void
  onTablesChanged?: () => void
}

function formatRows(n?: number | null) {
  if (n == null) return ''
  return ` · ${n}`
}

export function TableTree({
  tables,
  schemas,
  selectedSchema,
  canvasTableIds,
  selectedTableId,
  onSchemaChange,
  onSelectTable,
  onDropTable,
  onTablesChanged,
}: Props) {
  const [keyword, setKeyword] = useState('')
  const [rowFilter, setRowFilter] = useState<RowFilter>('all')
  const [filterOpen, setFilterOpen] = useState(false)
  const [editTable, setEditTable] = useState<TableMeta | null>(null)
  const [commentText, setCommentText] = useState('')
  const [busy, setBusy] = useState(false)

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase()
    return tables.filter((t) => {
      if (selectedSchema && t.schemaName !== selectedSchema) return false
      if (rowFilter === 'nonzero' && !(t.rowCount != null && t.rowCount > 0)) return false
      if (rowFilter === 'zero' && !(t.rowCount != null && t.rowCount === 0)) return false
      if (rowFilter === 'unknown' && t.rowCount != null) return false
      if (rowFilter === 'on_canvas' && !canvasTableIds.has(t.id)) return false
      if (!kw) return true
      return t.tableName.toLowerCase().includes(kw) || t.schemaName.toLowerCase().includes(kw)
    })
  }, [tables, selectedSchema, keyword, rowFilter, canvasTableIds])

  const deleteOne = useCallback(async (t: TableMeta) => {
    setBusy(true)
    try {
      await metadataApi.deleteTable(t.id)
      message.success(`已删除 ${t.tableName}`)
      onTablesChanged?.()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败')
    } finally {
      setBusy(false)
    }
  }, [onTablesChanged])

  const deleteFiltered = async () => {
    if (filtered.length === 0) return
    setBusy(true)
    try {
      const res = await metadataApi.batchDeleteTables(filtered.map((t) => t.id))
      message.success(`已删除 ${res.deleted} 张表`)
      onTablesChanged?.()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '批量删除失败')
    } finally {
      setBusy(false)
    }
  }

  const saveComment = async () => {
    if (!editTable) return
    setBusy(true)
    try {
      await metadataApi.updateTable(editTable.id, { commentText })
      message.success('已保存备注')
      setEditTable(null)
      onTablesChanged?.()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setBusy(false)
    }
  }

  const treeData = useMemo(() => {
    const bySchema = new Map<string, TableMeta[]>()
    for (const t of filtered) {
      const list = bySchema.get(t.schemaName) ?? []
      list.push(t)
      bySchema.set(t.schemaName, list)
    }
    return [...bySchema.entries()].map(([schema, list]) => ({
      key: schema,
      title: `${schema} (${list.length})`,
      children: list.map((t) => ({
        key: String(t.id),
        title: (
          <div className="tree-table-row">
            <span
              draggable
              onDragStart={(e) => {
                e.dataTransfer.setData('application/dbvein-table', String(t.id))
                e.dataTransfer.effectAllowed = 'copy'
              }}
              onClick={() => onSelectTable?.(t.id)}
              onDoubleClick={() => onDropTable?.(t.id)}
              className={canvasTableIds.has(t.id) ? 'tree-table on-canvas' : 'tree-table'}
              title={`${t.schemaName}.${t.tableName}${t.rowCount != null ? ` · 行数: ${t.rowCount}` : ' · 行数未知'}`}
            >
              <TableOutlined style={{ marginRight: 6, flexShrink: 0 }} />
              <span className="tree-table-name">{t.tableName.split('_').map((part, index, parts) => <span key={index}>{part}{index < parts.length - 1 ? '_' : ''}<wbr /></span>)}</span>
              <span className="tree-table-rows">{formatRows(t.rowCount)}</span>
            </span>
            <span className="tree-table-actions">
              <EditOutlined
                title="编辑备注"
                onClick={(e) => {
                  e.stopPropagation()
                  setEditTable(t)
                  setCommentText(t.commentText ?? '')
                }}
              />
              <Popconfirm
                title={`删除 ${t.tableName}？`}
                description="仅删本项目快照，不影响源库"
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={() => deleteOne(t)}
              >
                <DeleteOutlined
                  title="删除"
                  style={{ color: '#ff4d4f' }}
                  onClick={(e) => e.stopPropagation()}
                />
              </Popconfirm>
            </span>
          </div>
        ),
        isLeaf: true,
      })),
    }))
  }, [filtered, canvasTableIds, onDropTable, onSelectTable, deleteOne])

  const selectedKeys = selectedTableId != null ? [String(selectedTableId)] : []

  const filterActive = rowFilter !== 'all'
  const canBulkDelete = filterActive && filtered.length > 0

  return (
    <div className="table-tree">
      <div className="table-tree-toolbar">
        <Input.Search
          placeholder="搜索表名"
          allowClear
          onChange={(e) => setKeyword(e.target.value)}
          style={{ flex: 1 }}
        />
        <Button
          type={filterOpen || filterActive ? 'primary' : 'default'}
          icon={<FilterOutlined />}
          onClick={() => setFilterOpen((v) => !v)}
          title={filterOpen ? '收起筛选' : '展开筛选'}
        />
      </div>
      {filterOpen && (
        <Select
          className="table-filter"
          value={rowFilter}
          onChange={setRowFilter}
          style={{ width: '100%', marginTop: 8 }}
          options={[
            { value: 'all', label: '全部表' },
            { value: 'nonzero', label: '数据量不为 0' },
            { value: 'zero', label: '数据量为 0' },
            { value: 'unknown', label: '数据量未知' },
            { value: 'on_canvas', label: '已在画布' },
          ]}
        />
      )}
      {canBulkDelete && (
        <Space style={{ marginTop: 8 }}>
          <Popconfirm
            title={`删除当前列表的 ${filtered.length} 张表？`}
            description="仅删本项目快照，不影响源库"
            okText="全部删除"
            cancelText="取消"
            okButtonProps={{ danger: true, loading: busy }}
            onConfirm={deleteFiltered}
          >
            <Button size="small" danger icon={<DeleteOutlined />} loading={busy}>
              删除当前列表 ({filtered.length})
            </Button>
          </Popconfirm>
        </Space>
      )}
      <div className="schema-tabs">
        <span className={selectedSchema === null ? 'active' : ''} onClick={() => onSchemaChange(null)}>
          全部
        </span>
        {schemas.map((s) => (
          <span
            key={s}
            className={selectedSchema === s ? 'active' : ''}
            onClick={() => onSchemaChange(s)}
          >
            {s}
          </span>
        ))}
      </div>
      <div className="tree-filter-count">
        共 {filtered.length} 张表
        {filterActive ? '（已筛选）' : ''}
      </div>
      <Tree
        treeData={treeData}
        defaultExpandAll
        height={440}
        selectedKeys={selectedKeys}
        onSelect={(keys) => {
          const key = keys[0]
          if (key == null) return
          const id = Number(key)
          if (!Number.isFinite(id)) return
          onSelectTable?.(id)
        }}
      />
      <div className="tree-hint">单击查看表信息；双击添加到画布；悬停可编辑/删除</div>

      <Modal
        title={editTable ? `编辑备注 - ${editTable.tableName}` : '编辑备注'}
        open={!!editTable}
        onOk={saveComment}
        onCancel={() => setEditTable(null)}
        confirmLoading={busy}
      >
        <Input.TextArea
          rows={4}
          value={commentText}
          onChange={(e) => setCommentText(e.target.value)}
          placeholder="表备注 / 用途说明"
        />
      </Modal>
    </div>
  )
}
