import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Typography, message } from 'antd'
import { useEffect, useState } from 'react'
import { analysisApi } from '../api'
import type { DiscoveryRules, DiscoveryTemplate } from '../types'

const { TextArea } = Input
const { Text } = Typography

type Props = {
  open: boolean
  projectId: number
  onClose: () => void
  onApplied?: () => void
}

function listToLines(list?: string[]) {
  return (list ?? []).join('\n')
}

function linesToList(text?: string) {
  return (text ?? '')
    .split(/[\n,，;；]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

export function DiscoveryRulesModal({ open, projectId, onClose, onApplied }: Props) {
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [templates, setTemplates] = useState<DiscoveryTemplate[]>([])
  const [templateId, setTemplateId] = useState<string | undefined>('generic')
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const [rules, tpl] = await Promise.all([
        analysisApi.getDiscoveryRules(projectId),
        analysisApi.discoveryTemplates(projectId),
      ])
      setTemplates(tpl.templates ?? [])
      const hasBiz =
        (rules.hierarchy?.length ?? 0) > 0 ||
        (rules.redundantKeys?.length ?? 0) > 0 ||
        (rules.tableKinds?.length ?? 0) > 0
      setTemplateId(hasBiz ? undefined : 'generic')
      form.setFieldsValue({
        minConfidence: rules.minConfidence ?? 60,
        sampleSize: rules.sampleSize ?? 2000,
        minInclusion: Math.round((rules.minInclusion ?? 0.85) * 100),
        redundantKeys: listToLines(rules.redundantKeys),
        businessKeys: listToLines(rules.businessKeys),
        tableKinds: (rules.tableKinds ?? [])
          .map((k) => `${k.contains}=${k.kind}`)
          .join('\n'),
        hierarchy: (rules.hierarchy ?? [])
          .map((h) => {
            const red = (h.redundantWhenPresent ?? []).join('|')
            return red
              ? `${h.parentKind}>${h.childKind}:${h.joinColumn}:${red}`
              : `${h.parentKind}>${h.childKind}:${h.joinColumn}`
          })
          .join('\n'),
      })
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载发现规则失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) void load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, projectId])

  const parseForm = (): DiscoveryRules => {
    const values = form.getFieldsValue()
    const tableKinds = linesToList(values.tableKinds).map((line) => {
      const [contains, kind] = line.split(/[=:：]/).map((s) => s.trim())
      return { contains, kind: kind || contains }
    }).filter((k) => k.contains)

    const hierarchy = linesToList(values.hierarchy).map((line) => {
      // PARENT>CHILD:JOINCOL or PARENT>CHILD:JOINCOL:RED1|RED2
      const [pair, joinColumn, redPart] = line.split(':').map((s) => s.trim())
      const [parentKind, childKind] = (pair ?? '').split('>').map((s) => s.trim())
      return {
        parentKind,
        childKind,
        joinColumn,
        redundantWhenPresent: redPart
          ? redPart.split(/[|,]/).map((s) => s.trim()).filter(Boolean)
          : [],
      }
    }).filter((h) => h.parentKind && h.childKind && h.joinColumn)

    return {
      minConfidence: values.minConfidence ?? 60,
      sampleSize: values.sampleSize ?? 2000,
      minInclusion: (values.minInclusion ?? 85) / 100,
      redundantKeys: linesToList(values.redundantKeys),
      businessKeys: linesToList(values.businessKeys),
      tableKinds,
      hierarchy,
    }
  }

  const applyTemplate = async () => {
    setSaving(true)
    try {
      if (!templateId) { message.warning('请选择要应用的模板'); return }
      await analysisApi.applyDiscoveryTemplate(projectId, templateId)
      message.success('已应用模板到本项目')
      await load()
      onApplied?.()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '应用模板失败')
    } finally {
      setSaving(false)
    }
  }

  const save = async () => {
    setSaving(true)
    try {
      await analysisApi.saveDiscoveryRules(projectId, parseForm())
      message.success('发现规则已保存（仅对本项目生效）')
      onApplied?.()
      onClose()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title="发现与抽样设置"
      open={open}
      onCancel={onClose}
      width={720}
      confirmLoading={saving}
      onOk={save}
      okText="保存到本项目"
      destroyOnClose
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="抽样量、包含率阈值用于关系发现与连线校验。加权规则可选，不是发现前提。"
      />
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          style={{ width: 280 }}
          value={templateId}
          options={templates.map((t) => ({ value: t.id, label: t.name }))}
          onChange={setTemplateId}
          loading={loading}
        />
        <Button onClick={applyTemplate} loading={saving}>
          应用模板
        </Button>
        <Text type="secondary">
          {templates.find((t) => t.id === templateId)?.description}
        </Text>
      </Space>
      <Form form={form} layout="vertical" disabled={loading}>
        <Space wrap size="large" style={{ width: '100%' }}>
          <Form.Item
            name="sampleSize"
            label="抽样量（每列 distinct）"
            extra="越大越准，越慢。建议 500～2000"
          >
            <InputNumber min={50} max={10000} step={100} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item
            name="minInclusion"
            label="值包含率阈值（%）"
            extra="连线/确认时未达此比例则拦截"
          >
            <InputNumber min={50} max={100} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="minConfidence" label="发现最低置信度">
            <InputNumber min={40} max={100} style={{ width: 120 }} />
          </Form.Item>
        </Space>
        <Form.Item
          name="redundantKeys"
          label="冗余字段（同名也不连）"
          extra="每行一个，如 ZONECODE"
        >
          <TextArea rows={3} placeholder="ZONECODE&#10;COMPNO" />
        </Form.Item>
        <Form.Item
          name="businessKeys"
          label="业务键（同名加权）"
          extra="每行一个"
        >
          <TextArea rows={3} placeholder="REGIONNO&#10;BLDNO" />
        </Form.Item>
        <Form.Item
          name="tableKinds"
          label="表类型识别"
          extra="格式：表名包含=类型，如 CELL=CELL"
        >
          <TextArea rows={4} placeholder="REGION=REGION&#10;BLD=BLD&#10;CELL=CELL" />
        </Form.Item>
        <Form.Item
          name="hierarchy"
          label="层级关联"
          extra="格式：父类型>子类型:关联列 或 父>子:关联列:冗余列|冗余列"
        >
          <TextArea
            rows={5}
            placeholder="REGION>BLD:REGIONNO&#10;BLD>CELL:BLDNO:REGIONNO&#10;CELL>HOUSE:CELLNO:REGIONNO|BLDNO"
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}
