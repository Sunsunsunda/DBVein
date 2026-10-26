export type DatabaseType = 'ORACLE' | 'MYSQL'

export interface Project {
  id: number
  name: string
  description?: string
  sourceDataSourceId?: number
  createdAt?: string
}

export interface DataSource {
  id: number
  name: string
  type: DatabaseType
  host: string
  port: number
  databaseName?: string
  serviceName?: string
  username: string
  role: string
  passwordMasked: string
}

export interface DataSourceRequest {
  name: string
  type: DatabaseType
  host: string
  port: number
  databaseName?: string
  serviceName?: string
  username: string
  password: string
  role?: string
}

export interface TableMeta {
  id: number
  snapshotId: number
  schemaName: string
  tableName: string
  commentText?: string
  rowCount?: number
}

export interface TablePreview {
  columns: string[]
  rows: Record<string, string | null>[]
  page: number
  size: number
  hasMore: boolean
}

export interface ColumnProfile {
  columnName: string
  sampleSize: number
  sampledRows: number
  nullCount: number
  nonNullCount: number
  distinctCount: number
  nullRate?: number
}

export interface TableProfile {
  schemaName: string
  tableName: string
  estimatedRowCount?: number
  liveSampleCount: number
  sampleSize: number
  columnCount: number
}

export interface ColumnMeta {
  id: number
  tableId: number
  columnName: string
  dataType?: string
  length?: number
  precisionValue?: number
  scaleValue?: number
  nullable: boolean
  defaultValue?: string
  ordinalPosition: number
  commentText?: string
  primaryKey: boolean
}

export interface TableDetail {
  table: TableMeta
  columns: ColumnMeta[]
}

export interface CanvasNode {
  id: number
  workspaceId: number
  tableId: number
  positionX: number
  positionY: number
  width?: number
  height?: number
  collapsed: boolean
}

export interface CanvasNodeRequest {
  tableId: number
  positionX: number
  positionY: number
  width?: number
  height?: number
  collapsed: boolean
}

export interface Relation {
  id: number
  sourceTableId: number
  sourceColumnId: number
  targetTableId: number
  targetColumnId: number
  sourceTableName?: string
  sourceColumnName?: string
  targetTableName?: string
  targetColumnName?: string
  relationType: string
  sourceKind: string
  status: string
  confidence?: number
  remark?: string
}

export interface ValueMatchResult {
  sourceTableId: number
  sourceColumnId: number
  targetTableId: number
  targetColumnId: number
  sourceTableName?: string
  sourceColumnName?: string
  targetTableName?: string
  targetColumnName?: string
  sampleSize: number
  sourceDistinct: number
  targetDistinct: number
  sourceInTarget: number
  targetInSource: number
  bestInclusion: number
  suggestedDirection?: string
  passed: boolean
  threshold: number
  summary: string
}

export interface HierarchyRule {
  parentKind: string
  childKind: string
  joinColumn: string
  redundantWhenPresent?: string[]
}

export interface TableKindRule {
  contains: string
  kind: string
}

export interface DiscoveryRules {
  redundantKeys?: string[]
  hierarchy?: HierarchyRule[]
  businessKeys?: string[]
  tableKinds?: TableKindRule[]
  minConfidence?: number
  /** Distinct values per column for discovery / link validation (50–10000). */
  sampleSize?: number
  /** Inclusion threshold 0.5–1. */
  minInclusion?: number
}

export interface DiscoveryTemplate {
  id: string
  name: string
  description: string
}

export interface DiscoverRelationsResult {
  scannedTables: number
  candidates: number
  created: number
  skippedExisting: number
  relations: Relation[]
}

export interface Snapshot {
  id: number
  projectId: number
  dataSourceId: number
  label: string
}
