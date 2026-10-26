import { Button, Layout, Modal, Select, Space, Spin, message } from 'antd'
import { ApartmentOutlined, ArrowLeftOutlined, CloudDownloadOutlined, SettingOutlined } from '@ant-design/icons'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { analysisApi, metadataApi, projectApi } from '../api'
import { AnalysisCanvas } from '../canvas/AnalysisCanvas'
import { DiscoveryRulesModal } from '../components/DiscoveryRulesModal'
import { PropertyPanel } from '../components/PropertyPanel/PropertyPanel'
import { RelationDiscoverDrawer } from '../components/RelationDiscoverDrawer'
import { TableTree } from '../components/TableTree/TableTree'
import { useAnalysisStore } from '../stores/analysisStore'

const { Header, Sider, Content } = Layout

export function AnalysisPage() {
  const { projectId: pid } = useParams()
  const projectId = Number(pid)

  const project = useAnalysisStore((s) => s.project)
  const tables = useAnalysisStore((s) => s.tables)
  const schemas = useAnalysisStore((s) => s.schemas)
  const selectedSchema = useAnalysisStore((s) => s.selectedSchema)
  const tableDetails = useAnalysisStore((s) => s.tableDetails)
  const canvasTableIds = useAnalysisStore((s) => s.canvasTableIds)
  const canvasNodes = useAnalysisStore((s) => s.canvasNodes)
  const relations = useAnalysisStore((s) => s.relations)
  const selectedTableId = useAnalysisStore((s) => s.selectedTableId)
  const selectedColumn = useAnalysisStore((s) => s.selectedColumn)
  const loading = useAnalysisStore((s) => s.loading)

  const setProject = useAnalysisStore((s) => s.setProject)
  const setTables = useAnalysisStore((s) => s.setTables)
  const setSchemas = useAnalysisStore((s) => s.setSchemas)
  const setSelectedSchema = useAnalysisStore((s) => s.setSelectedSchema)
  const setTableDetail = useAnalysisStore((s) => s.setTableDetail)
  const setCanvasTableIds = useAnalysisStore((s) => s.setCanvasTableIds)
  const setCanvasNodes = useAnalysisStore((s) => s.setCanvasNodes)
  const addCanvasTableId = useAnalysisStore((s) => s.addCanvasTableId)
  const setRelations = useAnalysisStore((s) => s.setRelations)
  const setSelectedTableId = useAnalysisStore((s) => s.setSelectedTableId)
  const setSelectedColumn = useAnalysisStore((s) => s.setSelectedColumn)
  const setLoading = useAnalysisStore((s) => s.setLoading)

  const [leftOpen, setLeftOpen] = useState(true)
  const [rightOpen, setRightOpen] = useState(true)
  const [saveStatus, setSaveStatus] = useState('布局未修改')
  const [discoverOpen, setDiscoverOpen] = useState(false)
  const [discovering, setDiscovering] = useState(false)
  const [rulesOpen, setRulesOpen] = useState(false)
  const [schemaOpen, setSchemaOpen] = useState(false)
  const [availableSchemas, setAvailableSchemas] = useState<string[]>([])
  const [pullSchemas, setPullSchemas] = useState<string[]>([])

  const chooseSchemas = async () => {
    setLoading(true)
    try {
      const available = await metadataApi.sourceSchemas(projectId)
      setAvailableSchemas(available)
      setPullSchemas(available.length === 1 ? available : [])
      setSchemaOpen(true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '读取 Schema 失败')
    } finally {
      setLoading(false)
    }
  }

  const pendingRelations = useMemo(
    () => relations.filter((r) => r.status === 'PENDING'),
    [relations],
  )

  const loadDetail = useCallback(
    async (tableId: number) => {
      const cached = useAnalysisStore.getState().tableDetails[tableId]
      if (cached) return cached
      const detail = await metadataApi.tableDetail(tableId)
      setTableDetail(detail)
      return detail
    },
    [setTableDetail],
  )

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const p = await projectApi.get(projectId)
      setProject(p)
      const [schemaList, tableList, canvas, rels] = await Promise.all([
        metadataApi.schemas(projectId).catch(() => [] as string[]),
        metadataApi.tables(projectId).catch(() => []),
        analysisApi.getCanvas(projectId).catch(() => []),
        analysisApi.relations(projectId).catch(() => []),
      ])
      setSchemas(schemaList)
      setTables(tableList)
      setRelations(rels)
      setCanvasNodes(canvas)
      setCanvasTableIds(canvas.map((c) => c.tableId))
      for (const cn of canvas) {
        await loadDetail(cn.tableId)
      }
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [
    projectId,
    setProject,
    setSchemas,
    setTables,
    setRelations,
    setCanvasNodes,
    setCanvasTableIds,
    loadDetail,
    setLoading,
  ])

  useEffect(() => {
    refresh()
  }, [refresh])

  const pullSnapshot = async () => {
    setLoading(true)
    try {
      if (pullSchemas.length === 0) { message.warning('请选择至少一个 Schema'); return }
      await metadataApi.pullSnapshot(projectId, { schemas: pullSchemas })
      setSchemaOpen(false)
      message.success('元数据拉取完成')
      await refresh()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '拉取失败')
    } finally {
      setLoading(false)
    }
  }

  const addTableToCanvas = async (tableId: number) => {
    if (canvasTableIds.has(tableId)) {
      message.info('表已在画布上')
      return
    }
    const x = 120 + (canvasTableIds.size % 4) * 320
    const y = 80 + Math.floor(canvasTableIds.size / 4) * 280
    await loadDetail(tableId)
    const node = await analysisApi.addTable(projectId, { tableId, positionX: x, positionY: y })
    setCanvasNodes([...useAnalysisStore.getState().canvasNodes.filter((n) => n.tableId !== tableId), node])
    addCanvasTableId(tableId)
    message.success('已添加到画布')
  }

  const runDiscover = async () => {
    if (canvasTableIds.size < 2) {
      message.warning('请先在画布上放置至少 2 张表')
      return
    }
    setDiscovering(true)
    try {
      const result = await analysisApi.discoverRelations(projectId, {
        scope: 'canvas',
        maxResults: 200,
      })
      message.success(
        `抽样值匹配：扫描 ${result.scannedTables} 张表，新建 ${result.created} 条推荐（跳过已有 ${result.skippedExisting}）`,
      )
      const rels = await analysisApi.relations(projectId)
      setRelations(rels)
      setDiscoverOpen(true)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '关系发现失败')
    } finally {
      setDiscovering(false)
    }
  }

  const selectedDetail = selectedTableId ? tableDetails[selectedTableId] ?? null : null

  return (
    <Layout className="analysis-layout">
      <Modal title="选择要分析的 Schema" open={schemaOpen} onOk={pullSnapshot} onCancel={() => setSchemaOpen(false)} confirmLoading={loading} okButtonProps={{ disabled: pullSchemas.length === 0 }}>
        <Select mode="multiple" style={{ width: '100%' }} placeholder="选择 Schema" value={pullSchemas} onChange={setPullSchemas} options={availableSchemas.map((value) => ({ value, label: value }))} />
        <p>重新拉取会创建新快照。目前版本尚未实现旧画布和人工关系自动迁移，已有分析请先备份。</p>
      </Modal>
      <Header className="analysis-header">
        <Space>
          <Link to="/">
            <Button icon={<ArrowLeftOutlined />}>项目列表</Button>
          </Link>
          <strong>{project?.name ?? '数据分析'}</strong>
        </Space>
        <Space wrap>
          <Button icon={<SettingOutlined />} onClick={() => setRulesOpen(true)}>
            抽样设置
          </Button>
          <Button
            icon={<ApartmentOutlined />}
            onClick={runDiscover}
            loading={discovering}
            title="对画布表抽样源库数据，按值包含率推荐关系（只读）"
          >
            关系发现
            {pendingRelations.length > 0 ? ` (${pendingRelations.length})` : ''}
          </Button>
          <Button onClick={() => setDiscoverOpen(true)}>
            待确认{pendingRelations.length > 0 ? ` (${pendingRelations.length})` : ''}
          </Button>
          <Button icon={<CloudDownloadOutlined />} onClick={chooseSchemas} loading={loading}>
            拉取元数据
          </Button>
        </Space>
      </Header>
      <Layout>
        <Sider width={320} collapsed={!leftOpen} collapsedWidth={0} trigger={null} theme="light" className="analysis-sider">
          <Spin spinning={loading}>
            <TableTree
              tables={tables}
              schemas={schemas}
              selectedSchema={selectedSchema}
              canvasTableIds={canvasTableIds}
              selectedTableId={selectedTableId}
              onSchemaChange={setSelectedSchema}
              onSelectTable={async (tableId) => {
                await loadDetail(tableId)
                setSelectedTableId(tableId)
                setSelectedColumn(null)
              }}
              onDropTable={addTableToCanvas}
              onTablesChanged={async () => {
                await refresh()
                if (selectedTableId && !useAnalysisStore.getState().tables.some((t) => t.id === selectedTableId)) {
                  setSelectedTableId(null)
                  setSelectedColumn(null)
                }
              }}
            />
          </Spin>
        </Sider>
        <button type="button" className="panel-edge-toggle" aria-label={leftOpen ? '收起表列表' : '展开表列表'} title={leftOpen ? '收起表列表' : '展开表列表'} aria-expanded={leftOpen} onClick={() => setLeftOpen((value) => !value)}>{leftOpen ? '‹' : '›'}</button>
        <Content className="analysis-content">
          <div className="canvas-status" role="status">{saveStatus}<span>蓝色：数据库外键 · 橙色：人工关系 · 紫色：待确认</span></div>
          <Spin spinning={loading} wrapperClassName="canvas-spin">
            <AnalysisCanvas
              projectId={projectId}
              onSaveStatus={setSaveStatus}
              canvasNodes={canvasNodes}
              tableDetails={tableDetails}
              relations={relations}
              canvasTableIds={canvasTableIds}
              onLoadDetail={async (tableId) => { await loadDetail(tableId) }}
              onCanvasChange={(ids) => {
                setCanvasTableIds(ids)
                setCanvasNodes(useAnalysisStore.getState().canvasNodes.filter((n) => ids.includes(n.tableId)))
              }}
              onTableAdded={(node) => {
                const prev = useAnalysisStore.getState().canvasNodes
                setCanvasNodes([...prev.filter((n) => n.tableId !== node.tableId), node])
                addCanvasTableId(node.tableId)
              }}
              onNodesMoved={(nodes) => {
                if (!nodes || nodes.length === 0) return
                setCanvasNodes(nodes)
                setCanvasTableIds(nodes.map((n) => n.tableId))
              }}
              onRelationsChange={setRelations}
              onSelectTable={setSelectedTableId}
              onSelectColumn={(_, colId) => {
                const detail = Object.values(useAnalysisStore.getState().tableDetails).find((d) =>
                  d.columns.some((c) => c.id === colId),
                )
                const col = detail?.columns.find((c) => c.id === colId)
                if (col && detail) {
                  setSelectedTableId(detail.table.id)
                  setSelectedColumn(col)
                }
              }}
            />
          </Spin>
        </Content>
        <button type="button" className="panel-edge-toggle" aria-label={rightOpen ? '收起详情' : '展开详情'} title={rightOpen ? '收起详情' : '展开详情'} aria-expanded={rightOpen} onClick={() => setRightOpen((value) => !value)}>{rightOpen ? '›' : '‹'}</button>
        <Sider width={300} collapsed={!rightOpen} collapsedWidth={0} trigger={null} theme="light" className="analysis-sider">
          <PropertyPanel
            tableDetail={selectedDetail}
            selectedColumn={selectedColumn}
            relations={relations}
            tableId={selectedTableId}
            onTableUpdated={(table) => {
              const prev = useAnalysisStore.getState().tableDetails[table.id]
              if (prev) {
                setTableDetail({ ...prev, table: { ...prev.table, ...table } })
              }
              setTables(
                useAnalysisStore.getState().tables.map((t) => (t.id === table.id ? { ...t, ...table } : t)),
              )
            }}
            onColumnUpdated={(column) => {
              const prev = useAnalysisStore.getState().tableDetails[column.tableId]
              if (prev) {
                setTableDetail({
                  ...prev,
                  columns: prev.columns.map((c) => (c.id === column.id ? column : c)),
                })
              }
              setSelectedColumn(column)
            }}
          />
        </Sider>
      </Layout>
      <RelationDiscoverDrawer
        open={discoverOpen}
        projectId={projectId}
        pending={pendingRelations}
        allRelations={relations}
        onClose={() => setDiscoverOpen(false)}
        onChanged={setRelations}
      />
      <DiscoveryRulesModal
        open={rulesOpen}
        projectId={projectId}
        onClose={() => setRulesOpen(false)}
      />
    </Layout>
  )
}
