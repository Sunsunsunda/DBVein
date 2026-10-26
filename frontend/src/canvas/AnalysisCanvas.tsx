import {
  Background,
  Controls,
  MarkerType,
  MiniMap,
  Panel,
  ReactFlow,
  ReactFlowProvider,
  addEdge,
  useEdgesState,
  useNodesState,
  useReactFlow,
  type Connection,
  type Edge,
  type Node,
  type OnConnect,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { Button, message, Modal } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { analysisApi } from '../api'
import { TableNode, type TableNodeData } from '../components/TableNode/TableNode'
import type { CanvasNode, Relation, TableDetail } from '../types'

const nodeTypes = { tableNode: TableNode }

const relationEdgeDefaults = {
  type: 'default' as const,
  zIndex: 1001,
  markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16 },
}

function parseColumnHandle(handleId?: string | null): number | null {
  if (!handleId) return null
  const m = handleId.match(/^col-(\d+)-(in|out)$/)
  return m ? Number(m[1]) : null
}

interface Props {
  projectId: number
  onSaveStatus: (status: string) => void
  canvasNodes: CanvasNode[]
  tableDetails: Record<number, TableDetail>
  relations: Relation[]
  canvasTableIds: Set<number>
  onLoadDetail: (tableId: number) => Promise<void>
  onCanvasChange: (tableIds: number[]) => void
  onTableAdded: (node: CanvasNode) => void
  onNodesMoved: (nodes: CanvasNode[]) => void
  onRelationsChange: (relations: Relation[]) => void
  onSelectTable: (tableId: number) => void
  onSelectColumn: (tableId: number, columnId: number) => void
}

function AnalysisCanvasInner({
  projectId,
  onSaveStatus,
  canvasNodes,
  tableDetails,
  relations,
  canvasTableIds,
  onLoadDetail,
  onCanvasChange,
  onTableAdded,
  onNodesMoved,
  onRelationsChange,
  onSelectTable,
  onSelectColumn,
}: Props) {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const nodesRef = useRef<Node[]>([])
  const layoutHistory = useRef<Map<string, { x: number; y: number }>[]>([])
  const [undoCount, setUndoCount] = useState(0)
  const [undoing, setUndoing] = useState(false)
  const saveTimer = useRef<number | null>(null)
  const fittedRef = useRef(false)
  const { screenToFlowPosition, fitView } = useReactFlow()

  useEffect(() => {
    fittedRef.current = false;
    layoutHistory.current = []
    setUndoCount(0)
    return () => {
      if (saveTimer.current) window.clearTimeout(saveTimer.current)
    }
  }, [projectId])

  useEffect(() => {
    nodesRef.current = nodes
  }, [nodes])

  const onRemoveTable = useCallback(
    async (tableId: number) => {
      const ok = window.confirm('从画布移除此表？')
      if (!ok) return
      if (saveTimer.current) window.clearTimeout(saveTimer.current)
      try {
        await analysisApi.removeTable(projectId, tableId)
        const remainIds = [...canvasTableIds].filter((id) => id !== tableId)
        onCanvasChange(remainIds)
        message.success('已从画布移除')
      } catch (e) {
        message.error(e instanceof Error ? e.message : '移除失败')
      }
    },
    [projectId, canvasTableIds, onCanvasChange],
  )

  // Sync nodes from store: merge by tableId, keep local positions when possible
  useEffect(() => {
    setNodes((current) => {
      const posMap = new Map(current.map((n) => [n.id, n.position]))
      const next: Node[] = []
      const linkedByTable = new Map<number, Set<number>>()
      for (const r of relations) {
        if (!canvasTableIds.has(r.sourceTableId) || !canvasTableIds.has(r.targetTableId)) continue
        if (!linkedByTable.has(r.sourceTableId)) linkedByTable.set(r.sourceTableId, new Set())
        if (!linkedByTable.has(r.targetTableId)) linkedByTable.set(r.targetTableId, new Set())
        linkedByTable.get(r.sourceTableId)!.add(r.sourceColumnId)
        linkedByTable.get(r.targetTableId)!.add(r.targetColumnId)
      }

      for (const cn of canvasNodes) {
        const detail = tableDetails[cn.tableId]
        if (!detail) continue
        const id = String(cn.tableId)
        const data: TableNodeData = {
          tableId: cn.tableId,
          schemaName: detail.table.schemaName,
          tableName: detail.table.tableName,
          commentText: detail.table.commentText,
          rowCount: detail.table.rowCount,
          columns: detail.columns,
          linkedColumnIds: [...(linkedByTable.get(cn.tableId) ?? [])],
          onSelectTable,
          onSelectColumn: (col) => onSelectColumn(cn.tableId, col.id),
          onRemove: onRemoveTable,
        }
        next.push({
          id,
          type: 'tableNode',
          position: posMap.get(id) ?? { x: cn.positionX, y: cn.positionY },
          data,
        })
      }
      return next
    })
  }, [canvasNodes, tableDetails, relations, canvasTableIds, onSelectTable, onSelectColumn, onRemoveTable, setNodes])

  useEffect(() => {
    if (nodes.length > 0 && !fittedRef.current) {
      fittedRef.current = true
      requestAnimationFrame(() => fitView({ padding: 0.2 }))
    }
  }, [nodes.length, fitView])

  useEffect(() => {
    setEdges((prev) => {
      const temps = prev.filter((e) => e.data?.temporary)
      const fromRels = relations
        .filter((r) => canvasTableIds.has(r.sourceTableId) && canvasTableIds.has(r.targetTableId))
        .map((r) => {
          const pending = r.status === 'PENDING'
          return {
            id: `rel-${r.id}`,
            ...relationEdgeDefaults,
            source: String(r.sourceTableId),
            target: String(r.targetTableId),
            sourceHandle: `col-${r.sourceColumnId}-out`,
            targetHandle: `col-${r.targetColumnId}-in`,
            label: pending
              ? `? ${r.sourceColumnName} → ${r.targetColumnName} (${r.confidence ?? ''})`
              : `${r.sourceColumnName} → ${r.targetColumnName}`,
            data: { relationId: r.id, relationType: r.relationType, status: r.status },
            animated: pending || r.relationType === 'INFERRED',
            style: {
              stroke: pending
                ? '#722ed1'
                : r.relationType === 'FOREIGN_KEY'
                  ? '#1677ff'
                  : '#fa8c16',
              strokeWidth: 2,
              strokeDasharray:
                pending ||
                r.relationType === 'USER_DEFINED' ||
                r.relationType === 'BUSINESS' ||
                r.relationType === 'INFERRED'
                  ? '6 4'
                  : undefined,
            },
            markerEnd: {
              type: MarkerType.ArrowClosed,
              width: 16,
              height: 16,
              color: pending
                ? '#722ed1'
                : r.relationType === 'FOREIGN_KEY'
                  ? '#1677ff'
                  : '#fa8c16',
            },
          }
        })
      return [...fromRels, ...temps]
    })
  }, [relations, canvasTableIds, setEdges])

  const scheduleSave = useCallback(() => {
    if (saveTimer.current) window.clearTimeout(saveTimer.current)
    onSaveStatus('布局待保存…')
    saveTimer.current = window.setTimeout(async () => {
      const current = nodesRef.current
      if (current.length === 0) return
      try {
        const payload = current.map((n) => ({
          tableId: Number(n.id),
          positionX: n.position.x,
          positionY: n.position.y,
          collapsed: false,
        }))
        onSaveStatus('布局保存中…')
        const saved = await analysisApi.saveCanvas(projectId, payload)
        onNodesMoved(saved)
        onSaveStatus('布局已保存')
      } catch (e) {
        onSaveStatus('布局保存失败，请重新拖动后重试')
        message.error(e instanceof Error ? e.message : '保存画布失败')
      }
    }, 600)
  }, [projectId, onNodesMoved, onSaveStatus])

  const rememberLayout = useCallback(() => {
    layoutHistory.current.push(new Map(nodesRef.current.map((node) => [node.id, { ...node.position }])))
    if (layoutHistory.current.length > 20) layoutHistory.current.shift()
    setUndoCount(layoutHistory.current.length)
  }, [])

  const undoLayout = async () => {
    const previous = layoutHistory.current.pop()
    if (!previous) return
    if (saveTimer.current) window.clearTimeout(saveTimer.current)
    setUndoing(true)
    onSaveStatus('布局保存中…')
    try {
      const next = nodesRef.current.map((node) => ({ ...node, position: previous.get(node.id) ?? node.position }))
      const saved = await analysisApi.saveCanvas(projectId, next.map((node) => ({ tableId: Number(node.id), positionX: node.position.x, positionY: node.position.y, collapsed: false })))
      setNodes(next)
      onNodesMoved(saved)
      onSaveStatus('布局已保存')
    } catch (e) {
      layoutHistory.current.push(previous)
      onSaveStatus('撤销失败，原布局未变')
      message.error(e instanceof Error ? e.message : '撤销失败')
    } finally {
      setUndoing(false)
      setUndoCount(layoutHistory.current.length)
    }
  }

  const onNodeDragStop = useCallback(() => {
    scheduleSave()
  }, [scheduleSave])

  const onConnect: OnConnect = useCallback(
    async (connection: Connection) => {
      const sourceCol = parseColumnHandle(connection.sourceHandle)
      const targetCol = parseColumnHandle(connection.targetHandle)
      if (!sourceCol || !targetCol) {
        message.warning('请拖到具体字段圆点上再松开（不要只连到表）')
        return
      }

      // Guard against RF picking a handle from the wrong table after scroll/stack.
      let sourceName = String(sourceCol)
      let targetName = String(targetCol)
      let sourceOk = false
      let targetOk = false
      for (const detail of Object.values(tableDetails)) {
        for (const c of detail.columns) {
          if (c.id === sourceCol) {
            sourceName = `${detail.table.tableName}.${c.columnName}`
            sourceOk = String(detail.table.id) === String(connection.source)
          }
          if (c.id === targetCol) {
            targetName = `${detail.table.tableName}.${c.columnName}`
            targetOk = String(detail.table.id) === String(connection.target)
          }
        }
      }
      if (!sourceOk || !targetOk) {
        message.error('连线字段与表不匹配，请重新对准字段圆点再连')
        return
      }

      // Show the line immediately; validate+create in background.
      const tempId = `temp-${sourceCol}-${targetCol}-${Date.now()}`
      setEdges((eds) =>
        addEdge(
          {
            ...connection,
            ...relationEdgeDefaults,
            id: tempId,
            label: `校验 ${sourceName} → ${targetName}…`,
            animated: true,
            style: { stroke: '#1677ff', strokeWidth: 2, strokeDasharray: '4 4' },
            markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16, color: '#1677ff' },
            data: { temporary: true },
          },
          eds,
        ),
      )

      const removeTemp = () => setEdges((eds) => eds.filter((e) => e.id !== tempId))

      try {
        const create = (force: boolean) =>
          analysisApi.createRelation(projectId, {
            sourceColumnId: sourceCol,
            targetColumnId: targetCol,
            relationType: 'USER_DEFINED',
            force,
          })

        let rel
        let forced = false
        try {
          rel = await create(false)
        } catch (e) {
          const msg = e instanceof Error ? e.message : '创建关系失败'
          if (!msg.includes('数据校验')) {
            removeTemp()
            message.error(msg)
            return
          }
          const ok = await new Promise<boolean>((resolve) => {
            Modal.confirm({
              title: '数据校验未通过',
              width: 520,
              content: `${sourceName} → ${targetName}\n\n${msg.replace(/^数据校验未通过：/, '')}`,
              okText: '仍要创建',
              okButtonProps: { danger: true },
              cancelText: '取消',
              onOk: () => resolve(true),
              onCancel: () => resolve(false),
            })
          })
          if (!ok) {
            removeTemp()
            return
          }
          rel = await create(true)
          forced = true
        }

        // Swap temp → real edge in one update to avoid flicker.
        setEdges((eds) => {
          const withoutTemp = eds.filter((e) => e.id !== tempId)
          return addEdge(
            {
              ...connection,
              ...relationEdgeDefaults,
              id: `rel-${rel.id}`,
              label: `${rel.sourceColumnName} → ${rel.targetColumnName}`,
              data: { relationId: rel.id, relationType: rel.relationType, status: rel.status },
              animated: false,
              style: { stroke: '#fa8c16', strokeWidth: 2, strokeDasharray: '6 4' },
              markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16, color: '#fa8c16' },
            },
            withoutTemp,
          )
        })
        onRelationsChange([...relations, rel])
        message.success(
          forced
            ? `已强制创建 ${sourceName} → ${targetName}`
            : `已创建 ${sourceName} → ${targetName}（置信度 ${rel.confidence ?? '-'}）`,
        )
      } catch (e) {
        removeTemp()
        message.error(e instanceof Error ? e.message : '创建关系失败')
      }
    },
    [projectId, relations, onRelationsChange, setEdges, tableDetails],
  )

  const onEdgeClick = useCallback(
    async (_: React.MouseEvent, edge: Edge) => {
      const relationId = edge.data?.relationId as number | undefined
      if (!relationId) return
      const rel = relations.find((r) => r.id === relationId)
      if (!rel) return
      if (rel.status === 'PENDING') {
        const choice = window.prompt(
          `推荐关系 ${rel.sourceColumnName} → ${rel.targetColumnName}（置信度 ${rel.confidence}）\n输入 Y 确认，N 忽略，取消则跳过`,
          'Y',
        )
        if (choice == null) return
        try {
          if (choice.trim().toUpperCase() === 'Y') {
            try {
              const updated = await analysisApi.confirmRelation(projectId, relationId, false)
              onRelationsChange(relations.map((r) => (r.id === relationId ? updated : r)))
              message.success('已确认（数据校验通过）')
            } catch (e) {
              const msg = e instanceof Error ? e.message : '确认失败'
              const force = window.confirm(`${msg}\n\n仍要强制确认？`)
              if (!force) return
              const updated = await analysisApi.confirmRelation(projectId, relationId, true)
              onRelationsChange(relations.map((r) => (r.id === relationId ? updated : r)))
              message.warning('已强制确认')
            }
          } else {
            await analysisApi.ignoreRelation(projectId, relationId)
            onRelationsChange(relations.filter((r) => r.id !== relationId))
            message.success('已忽略')
          }
        } catch (e) {
          message.error(e instanceof Error ? e.message : '操作失败')
        }
        return
      }
      const action = rel.relationType === 'FOREIGN_KEY' ? 'ignore' : 'delete'
      const ok = window.confirm(
        action === 'ignore'
          ? `忽略外键关系 ${rel.sourceColumnName} → ${rel.targetColumnName}？`
          : `删除关系 ${rel.sourceColumnName} → ${rel.targetColumnName}？`,
      )
      if (!ok) return
      if (saveTimer.current) window.clearTimeout(saveTimer.current)
      try {
        if (action === 'ignore') {
          await analysisApi.ignoreRelation(projectId, relationId)
        } else {
          await analysisApi.deleteRelation(projectId, relationId)
        }
        onRelationsChange(relations.filter((r) => r.id !== relationId))
        message.success(action === 'ignore' ? '已忽略' : '已删除')
      } catch (e) {
        message.error(e instanceof Error ? e.message : '操作失败')
      }
    },
    [projectId, relations, onRelationsChange],
  )

  const onDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'copy'
  }, [])

  const addTableAt = useCallback(
    async (tableId: number, x: number, y: number) => {
      if (canvasTableIds.has(tableId)) {
        message.info('表已在画布上')
        return
      }
      try {
        await onLoadDetail(tableId)
        const node = await analysisApi.addTable(projectId, { tableId, positionX: x, positionY: y })
        onTableAdded(node)
        message.success('已添加到画布')
      } catch (e) {
        message.error(e instanceof Error ? e.message : '添加失败')
      }
    },
    [projectId, canvasTableIds, onLoadDetail, onTableAdded],
  )

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault()
      const raw = e.dataTransfer.getData('application/dbvein-table')
      if (!raw) return
      const position = screenToFlowPosition({ x: e.clientX, y: e.clientY })
      addTableAt(Number(raw), position.x - 80, position.y - 40)
    },
    [addTableAt, screenToFlowPosition],
  )

  const onNodesDelete = useCallback(
    async (deleted: Node[]) => {
      if (saveTimer.current) window.clearTimeout(saveTimer.current)
      const removed = new Set<number>()
      for (const n of deleted) {
        const tableId = Number(n.id)
        try {
          await analysisApi.removeTable(projectId, tableId)
          removed.add(tableId)
        } catch (e) {
          message.error(e instanceof Error ? e.message : '删除失败')
        }
      }
      const remainIds = [...canvasTableIds].filter((id) => !removed.has(id))
      onCanvasChange(remainIds)
    },
    [projectId, canvasTableIds, onCanvasChange],
  )

  return (
    <div className="analysis-canvas">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onEdgeClick={onEdgeClick}
        onNodeDragStart={rememberLayout}
        onNodeDragStop={onNodeDragStop}
        nodesDraggable={!undoing}
        onNodesDelete={onNodesDelete}
        onDrop={onDrop}
        onDragOver={onDragOver}
        deleteKeyCode={['Backspace', 'Delete']}
        defaultEdgeOptions={relationEdgeDefaults}
        elevateEdgesOnSelect
        proOptions={{ hideAttribution: true }}
      >
        <Panel position="top-left" style={{ marginTop: 62 }}><Button size="small" disabled={undoCount === 0} loading={undoing} onClick={undoLayout} title="撤销本次打开画板后的拖动，最多保留20步">撤销布局{undoCount > 0 ? ` (${undoCount})` : ''}</Button></Panel>
        <Background />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  )
}

export function AnalysisCanvas(props: Props) {
  return (
    <ReactFlowProvider>
      <AnalysisCanvasInner {...props} />
    </ReactFlowProvider>
  )
}
