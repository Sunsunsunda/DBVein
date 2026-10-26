import { Alert, Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, message } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { datasourceApi, projectApi } from '../api'
import type { DataSource, DataSourceRequest, Project } from '../types'

export function ProjectPage() {
  const navigate = useNavigate()
  const [projects, setProjects] = useState<Project[]>([])
  const [datasources, setDatasources] = useState<DataSource[]>([])
  const [open, setOpen] = useState(false)
  const [managerOpen, setManagerOpen] = useState(false)
  const [editingDs, setEditingDs] = useState<DataSource | null>(null)
  const [testingSavedId, setTestingSavedId] = useState<number | null>(null)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const openDatasource = (source?: DataSource) => {
    setEditingDs(source ?? null)
    setConnectionResult(null)
    dsForm.resetFields()
    if (source) dsForm.setFieldsValue({ ...source, password: '' })
    setDsOpen(true)
  }
  const testSaved = async (id: number) => {
    setTestingSavedId(id)
    try {
      const result = await datasourceApi.testSaved(id)
      if (!result.success) throw new Error(result.message || '连接失败')
      message.success('连接成功')
    } catch (e) { message.error(e instanceof Error ? e.message : '连接失败') }
    finally { setTestingSavedId(null) }
  }
  const deleteDatasource = async (id: number) => {
    setDeletingId(id)
    try {
      await datasourceApi.delete(id)
      message.success('数据源已删除')
      await load()
    } catch (e) { message.error(e instanceof Error ? e.message : '删除失败') }
    finally { setDeletingId(null) }
  }
  const [dsOpen, setDsOpen] = useState(false)
  const [form] = Form.useForm()
  const [dsForm] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [testingConnection, setTestingConnection] = useState(false)
  const [savingDs, setSavingDs] = useState(false)
  const [connectionResult, setConnectionResult] = useState<{ success: boolean; message: string } | null>(null)
  const databaseType = Form.useWatch('type', dsForm) ?? 'ORACLE'

  const load = async () => {
    setLoading(true)
    try {
      const [p, d] = await Promise.all([projectApi.list(), datasourceApi.list()])
      setProjects(p)
      setDatasources(d)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const createProject = async () => {
    const values = await form.validateFields()
    try {
      const p = await projectApi.create(values)
      message.success('项目已创建')
      setOpen(false)
      form.resetFields()
      navigate(`/projects/${p.id}/analysis`)
    } catch (e) {
      message.error(e instanceof Error ? e.message : '创建失败')
    }
  }

  const testConnection = async () => {
    try {
      await dsForm.validateFields(['type', 'host', 'port', 'databaseName', 'serviceName', 'username', 'password'])
    } catch {
      return
    }
    setTestingConnection(true)
    setConnectionResult(null)
    try {
      const values = dsForm.getFieldsValue(true) as DataSourceRequest
      const result = editingDs ? await datasourceApi.testSaved(editingDs.id, values) : await datasourceApi.test(values)
      setConnectionResult({ success: result.success, message: result.success ? '连接成功' : result.message || '连接失败' })
    } catch (e) {
      setConnectionResult({ success: false, message: e instanceof Error ? e.message : '连接失败' })
    } finally {
      setTestingConnection(false)
    }
  }

  const createDs = async () => {
    let values: DataSourceRequest
    try {
      values = (await dsForm.validateFields()) as DataSourceRequest
    } catch {
      return
    }
    setSavingDs(true)
    setConnectionResult(null)
    try {
      const result = editingDs ? await datasourceApi.testSaved(editingDs.id, values) : await datasourceApi.test(values)
      if (!result.success) throw new Error(result.message || '连接失败')
      if (editingDs) await datasourceApi.update(editingDs.id, values)
      else await datasourceApi.create(values)
      message.success('数据源已保存')
      setDsOpen(false)
      dsForm.resetFields()
      void load()
    } catch (e) {
      setConnectionResult({ success: false, message: e instanceof Error ? e.message : '保存失败' })
    } finally {
      setSavingDs(false)
    }
  }

  return (
    <div className="project-page">
      <Card
        title="DBVein 项目"
        extra={
          <Space>
            <Button onClick={() => setManagerOpen(true)}>数据源管理</Button>
            <Button onClick={() => openDatasource()}>新建数据源</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
              新建项目
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={projects}
          columns={[
            { title: 'ID', dataIndex: 'id', width: 80 },
            { title: '名称', dataIndex: 'name' },
            { title: '说明', dataIndex: 'description' },
            {
              title: '操作',
              width: 160,
              render: (_, row) => (
                <Button type="link" onClick={() => navigate(`/projects/${row.id}/analysis`)}>
                  打开分析画板
                </Button>
              ),
            },
          ]}
        />
      </Card>

      <Modal title="数据源管理" open={managerOpen} width={1000} onCancel={() => setManagerOpen(false)} footer={<Button type="primary" onClick={() => openDatasource()}>新建数据源</Button>}>
        <Table<DataSource> rowKey="id" dataSource={datasources} loading={loading} scroll={{ x: 850 }} columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '类型', dataIndex: 'type' },
          { title: '地址', render: (_, row) => `${row.host}:${row.port}` },
          { title: '数据库 / 服务', render: (_, row) => row.serviceName || row.databaseName || '-' },
          { title: '用户名', dataIndex: 'username' },
          { title: '关联项目', render: (_, row) => projects.filter((project) => project.sourceDataSourceId === row.id).map((project) => project.name).join('、') || '无' },
          { title: '操作', width: 230, render: (_, row) => {
            const inUse = projects.some((project) => project.sourceDataSourceId === row.id)
            return <Space>
              <Button size="small" onClick={() => openDatasource(row)}>编辑</Button>
              <Button size="small" loading={testingSavedId === row.id} disabled={testingSavedId !== null && testingSavedId !== row.id} onClick={() => testSaved(row.id)}>测试连接</Button>
              <Popconfirm title="删除此数据源？" description="仅删除连接配置，不影响源数据库。" disabled={inUse} onConfirm={() => deleteDatasource(row.id)}>
                <Button size="small" danger disabled={inUse} loading={deletingId === row.id} title={inUse ? '数据源被项目使用，不能删除' : undefined}>删除</Button>
              </Popconfirm>
            </Space>
          } },
        ]} />
      </Modal>

      <Modal title="新建项目" open={open} onOk={createProject} onCancel={() => setOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="项目名称" rules={[{ required: true }]}>
            <Input placeholder="数据库结构分析" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="sourceDataSourceId" label="源数据源" rules={[{ required: true }]}>
            <Select
              options={datasources.map((d) => ({
                value: d.id,
                label: `${d.name} (${d.type} ${d.host})`,
              }))}
              placeholder="请先创建数据源"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={editingDs ? '编辑数据源' : '新建数据源'} open={dsOpen} onCancel={() => setDsOpen(false)} closable={!testingConnection && !savingDs} maskClosable={!testingConnection && !savingDs} keyboard={!testingConnection && !savingDs} destroyOnClose footer={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Button loading={testingConnection} disabled={savingDs} onClick={testConnection}>测试连接</Button>
          <Space>
            <Button disabled={testingConnection || savingDs} onClick={() => setDsOpen(false)}>取消</Button>
            <Button type="primary" loading={savingDs} disabled={testingConnection} onClick={createDs}>测试并保存</Button>
          </Space>
        </div>
      }>
        {connectionResult && <Alert style={{ marginBottom: 16 }} type={connectionResult.success ? 'success' : 'error'} showIcon title={connectionResult.message} />}
        <Form
          form={dsForm}
          disabled={testingConnection || savingDs}
          onValuesChange={() => setConnectionResult(null)}
          layout="vertical"
          initialValues={{ type: 'ORACLE', host: 'localhost', port: 1521, role: 'SOURCE' }}
        >
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={[{ value: 'ORACLE', label: 'Oracle' }, { value: 'MYSQL', label: 'MySQL' }]} onChange={(type) => dsForm.setFieldsValue({ port: type === 'MYSQL' ? 3306 : 1521, databaseName: undefined, serviceName: undefined })} />
          </Form.Item>
          <Form.Item name="host" label="主机" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="port" label="端口" rules={[{ required: true }]}>
            <InputNumber min={1} max={65535} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="databaseName" label={databaseType === 'MYSQL' ? '数据库名' : 'SID'} rules={databaseType === 'MYSQL' ? [{ required: true }] : []} tooltip="Oracle 可填写 SID 或 Service Name；MySQL 填写数据库名">
            <Input placeholder={databaseType === 'MYSQL' ? '例如 dbvein_demo' : '例如 ORCL'} />
          </Form.Item>
          <Form.Item name="serviceName" hidden={databaseType !== 'ORACLE'} label="Service Name" tooltip="若填了 Service Name 会优先用它，可留空">
            <Input placeholder="可留空" />
          </Form.Item>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: !editingDs }]}>
            <Input.Password placeholder={editingDs ? '留空保留原密码' : '请输入密码'} />
          </Form.Item>
          <Form.Item name="role" hidden>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
