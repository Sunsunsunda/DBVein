import { api } from './client'
import type {
  CanvasNode,
  CanvasNodeRequest,
  DataSource,
  DataSourceRequest,
  Project,
  Relation,
  Snapshot,
  TableDetail,
  TableMeta,
  TablePreview,
  TableProfile,
  ColumnMeta,
  ColumnProfile,
  DiscoverRelationsResult,
  DiscoveryRules,
  DiscoveryTemplate,
  ValueMatchResult,
} from '../types'

export const projectApi = {
  list: () => api.get<Project[]>('/api/projects'),
  get: (id: number) => api.get<Project>(`/api/projects/${id}`),
  create: (body: { name: string; description?: string; sourceDataSourceId?: number }) =>
    api.post<Project>('/api/projects', body),
  update: (id: number, body: { name: string; description?: string; sourceDataSourceId?: number }) =>
    api.put<Project>(`/api/projects/${id}`, body),
  delete: (id: number) => api.delete(`/api/projects/${id}`),
}

export const datasourceApi = {
  update: (id: number, body: DataSourceRequest) => api.put<DataSource>(`/api/datasources/${id}`, body),
  delete: (id: number) => api.delete(`/api/datasources/${id}`),
  testSaved: (id: number, body?: DataSourceRequest) => api.post<{ success: boolean; message: string }>(`/api/datasources/${id}/test`, body),
  list: () => api.get<DataSource[]>('/api/datasources'),
  create: (body: DataSourceRequest) => api.post<DataSource>('/api/datasources', body),
  test: (body: DataSourceRequest) => api.post<{ success: boolean; message: string }>('/api/datasources/test', body),
}

export const metadataApi = {
  sourceSchemas: (projectId: number) => api.get<string[]>(`/api/projects/${projectId}/source-schemas`),
  pullSnapshot: (projectId: number, body?: { label?: string; schemas?: string[] }) =>
    api.post<Snapshot>(`/api/projects/${projectId}/snapshots/pull`, body ?? {}),
  schemas: (projectId: number) => api.get<string[]>(`/api/projects/${projectId}/schemas`),
  tables: (projectId: number, schema?: string) => {
    const q = schema ? `?schema=${encodeURIComponent(schema)}` : ''
    return api.get<TableMeta[]>(`/api/projects/${projectId}/tables${q}`)
  },
  tableDetail: (tableId: number) => api.get<TableDetail>(`/api/tables/${tableId}`),
  preview: (tableId: number, page = 1, size = 50) =>
    api.get<TablePreview>(`/api/tables/${tableId}/preview?page=${page}&size=${size}`),
  tableProfile: (tableId: number, sampleSize = 10000) =>
    api.get<TableProfile>(`/api/tables/${tableId}/profile?sampleSize=${sampleSize}`),
  columnProfile: (tableId: number, columnId: number, sampleSize = 10000) =>
    api.get<ColumnProfile>(`/api/tables/${tableId}/columns/${columnId}/profile?sampleSize=${sampleSize}`),
  updateTable: (tableId: number, body: { commentText?: string }) =>
    api.put<TableMeta>(`/api/tables/${tableId}`, body),
  updateColumn: (tableId: number, columnId: number, body: { commentText?: string }) =>
    api.put<ColumnMeta>(`/api/tables/${tableId}/columns/${columnId}`, body),
  deleteTable: (tableId: number) => api.delete(`/api/tables/${tableId}`),
  batchDeleteTables: (tableIds: number[]) =>
    api.post<{ deleted: number }>('/api/tables/batch-delete', { tableIds }),
}

export const analysisApi = {
  getCanvas: (projectId: number) => api.get<CanvasNode[]>(`/api/projects/${projectId}/analysis/canvas`),
  saveCanvas: (projectId: number, nodes: CanvasNodeRequest[]) =>
    api.put<CanvasNode[]>(`/api/projects/${projectId}/analysis/canvas`, nodes),
  addTable: (projectId: number, body: { tableId: number; positionX: number; positionY: number }) =>
    api.post<CanvasNode>(`/api/projects/${projectId}/analysis/canvas/tables`, body),
  removeTable: (projectId: number, tableId: number) =>
    api.delete(`/api/projects/${projectId}/analysis/canvas/tables/${tableId}`),
  relations: (projectId: number) => api.get<Relation[]>(`/api/projects/${projectId}/analysis/relations`),
  createRelation: (
    projectId: number,
    body: {
      sourceColumnId: number
      targetColumnId: number
      relationType?: string
      force?: boolean
      sampleSize?: number
      minInclusion?: number
    },
  ) => api.post<Relation>(`/api/projects/${projectId}/analysis/relations`, body),
  validateRelation: (
    projectId: number,
    body: {
      sourceColumnId: number
      targetColumnId: number
      sampleSize?: number
      minInclusion?: number
    },
  ) => api.post<ValueMatchResult>(`/api/projects/${projectId}/analysis/relations/validate`, body),
  discoverRelations: (
    projectId: number,
    body?: {
      scope?: string
      minConfidence?: number
      maxResults?: number
      sampleSize?: number
      minInclusion?: number
    },
  ) =>
    api.post<DiscoverRelationsResult>(`/api/projects/${projectId}/analysis/relations/discover`, body ?? {
      scope: 'canvas',
    }),
  confirmRelation: (projectId: number, id: number, force = false) =>
    api.put<Relation>(
      `/api/projects/${projectId}/analysis/relations/${id}/confirm?force=${force ? 'true' : 'false'}`,
      {},
    ),
  ignoreRelation: (projectId: number, id: number) =>
    api.put<Relation>(`/api/projects/${projectId}/analysis/relations/${id}/ignore`, {}),
  batchIgnoreRelations: (projectId: number, ids: number[]) =>
    api.post<{ updated: number }>(`/api/projects/${projectId}/analysis/relations/batch-ignore`, { ids }),
  batchConfirmRelations: (projectId: number, ids: number[]) =>
    api.post<{ updated: number }>(`/api/projects/${projectId}/analysis/relations/batch-confirm`, { ids }),
  deleteRelation: (projectId: number, id: number) =>
    api.delete(`/api/projects/${projectId}/analysis/relations/${id}`),
  getDiscoveryRules: (projectId: number) =>
    api.get<DiscoveryRules>(`/api/projects/${projectId}/analysis/discovery-rules`),
  saveDiscoveryRules: (projectId: number, rules: DiscoveryRules) =>
    api.put<DiscoveryRules>(`/api/projects/${projectId}/analysis/discovery-rules`, rules),
  discoveryTemplates: (projectId: number) =>
    api.get<{ templates: DiscoveryTemplate[] }>(`/api/projects/${projectId}/analysis/discovery-templates`),
  applyDiscoveryTemplate: (projectId: number, templateId: string) =>
    api.post<DiscoveryRules>(`/api/projects/${projectId}/analysis/discovery-rules/apply-template`, {
      templateId,
    }),
}
