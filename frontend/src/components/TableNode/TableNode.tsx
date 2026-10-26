import { memo, useEffect, useMemo, useRef, useState } from 'react'
import { Handle, Position, useUpdateNodeInternals, type NodeProps } from '@xyflow/react'
import type { ColumnMeta } from '../../types'
import { abbreviateDataType, fullDataType } from '../../utils/dataTypeLabel'

export interface TableNodeData extends Record<string, unknown> {
  tableId: number
  schemaName: string
  tableName: string
  commentText?: string
  rowCount?: number
  columns: ColumnMeta[]
  /** Columns involved in canvas relations — kept outside scroll so edges stay attached */
  linkedColumnIds?: number[]
  onSelectTable?: (tableId: number) => void
  onSelectColumn?: (col: ColumnMeta) => void
  onRemove?: (tableId: number) => void
}

function ColumnRow({
  col,
  linked,
  onSelect,
}: {
  col: ColumnMeta
  linked?: boolean
  onSelect?: (col: ColumnMeta) => void
}) {
  // Handles must be position:relative inside the row; RF default absolute+center
  // stacks every handle on the node midpoint and causes wrong-column connections.
  const handleStyle = {
    position: 'relative' as const,
    top: 'auto',
    left: 'auto',
    right: 'auto',
    transform: 'none',
  }

  return (
    <div
      className={`table-node-row ${linked ? 'linked' : ''}`}
      onClick={(e) => {
        e.stopPropagation()
        onSelect?.(col)
      }}
    >
      <Handle
        type="target"
        position={Position.Left}
        id={`col-${col.id}-in`}
        className="col-handle col-handle-in"
        style={handleStyle}
      />
      <span className="col-name">
        {col.primaryKey && <span className="pk">PK</span>}
        {linked && <span className="lk">链</span>}
        {col.columnName}
      </span>
      <span className="col-type" title={fullDataType(col)}>
        {abbreviateDataType(col)}
      </span>
      <Handle
        type="source"
        position={Position.Right}
        id={`col-${col.id}-out`}
        className="col-handle col-handle-out"
        style={handleStyle}
      />
    </div>
  )
}

export const TableNode = memo(function TableNode({ id, data, selected }: NodeProps) {
  const [keyword, setKeyword] = useState('')
  const [keysOnly, setKeysOnly] = useState(false)
  const d = data as unknown as TableNodeData
  const linkedSet = useMemo(() => new Set(d.linkedColumnIds ?? []), [d.linkedColumnIds])
  const updateNodeInternals = useUpdateNodeInternals()
  const bodyRef = useRef<HTMLDivElement>(null)

  const { pinned, rest } = useMemo(() => {
    const pinnedCols: ColumnMeta[] = []
    const restCols: ColumnMeta[] = []
    const seen = new Set<number>()
    for (const col of d.columns) {
      if (col.primaryKey || linkedSet.has(col.id)) {
        pinnedCols.push(col)
        seen.add(col.id)
      }
    }
    for (const col of d.columns) {
      if (!seen.has(col.id) && !keysOnly && (!keyword.trim() || `${col.columnName} ${col.commentText ?? ''}`.toLowerCase().includes(keyword.trim().toLowerCase()))) restCols.push(col)
    }
    return { pinned: pinnedCols, rest: restCols }
  }, [d.columns, linkedSet, keyword, keysOnly])

  useEffect(() => {
    updateNodeInternals(id)
    // Pin move remounts handles; refresh again after layout.
    const t = window.requestAnimationFrame(() => updateNodeInternals(id))
    return () => window.cancelAnimationFrame(t)
  }, [id, pinned, rest, updateNodeInternals])

  useEffect(() => {
    const el = bodyRef.current
    if (!el) return
    const onScroll = () => updateNodeInternals(id)
    el.addEventListener('scroll', onScroll, { passive: true })
    return () => el.removeEventListener('scroll', onScroll)
  }, [id, updateNodeInternals])

  return (
    <div className={`table-node ${selected ? 'selected' : ''}`}>
      <div className="table-node-header" onClick={() => d.onSelectTable?.(d.tableId)}>
        <div className="table-node-header-row">
          <div className="table-node-title">{d.tableName}</div>
          <button
            type="button"
            className="table-node-remove"
            title="从画布移除"
            onClick={(e) => {
              e.stopPropagation()
              d.onRemove?.(d.tableId)
            }}
          >
            ×
          </button>
        </div>
        <div className="table-node-sub">
          {d.schemaName}
          {d.rowCount != null ? ` · ${d.rowCount} 行` : ''}
        </div>
        {d.commentText && <div className="table-node-comment">{d.commentText}</div>}
      </div>

      <div className="table-node-search nodrag nowheel" onKeyDown={(event) => event.stopPropagation()}>
        <input aria-label={`${d.tableName} 搜索字段`} placeholder="搜索字段 / 含义" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
        <label><input type="checkbox" checked={keysOnly} onChange={(event) => setKeysOnly(event.target.checked)} />仅关键字段</label>
      </div>
      {keyword && <div className="table-node-search-hint">主键和已关联字段始终保留</div>}
      {pinned.length > 0 && (
        <div className="table-node-pinned">
          {pinned.map((col) => (
            <ColumnRow
              key={col.id}
              col={col}
              linked={linkedSet.has(col.id)}
              onSelect={d.onSelectColumn}
            />
          ))}
        </div>
      )}

      {!keysOnly && keyword && rest.length === 0 && <div className="table-node-search-hint">没有匹配的其他字段</div>}
      {rest.length > 0 && (
        <div className="table-node-body nowheel nodrag" ref={bodyRef}>
          {rest.map((col) => (
            <ColumnRow key={col.id} col={col} onSelect={d.onSelectColumn} />
          ))}
        </div>
      )}
    </div>
  )
})
