import { create } from 'zustand'
import type { ColumnMeta, Project, Relation, TableDetail, TableMeta } from '../types'

interface AnalysisState {
  project: Project | null
  tables: TableMeta[]
  schemas: string[]
  selectedSchema: string | null
  tableDetails: Record<number, TableDetail>
  canvasTableIds: Set<number>
  canvasNodes: import('../types').CanvasNode[]
  relations: Relation[]
  selectedTableId: number | null
  selectedColumn: ColumnMeta | null
  loading: boolean
  setProject: (p: Project | null) => void
  setTables: (tables: TableMeta[]) => void
  setSchemas: (schemas: string[]) => void
  setSelectedSchema: (schema: string | null) => void
  setTableDetail: (detail: TableDetail) => void
  setCanvasTableIds: (ids: number[]) => void
  setCanvasNodes: (nodes: import('../types').CanvasNode[]) => void
  addCanvasTableId: (id: number) => void
  removeCanvasTableId: (id: number) => void
  setRelations: (relations: Relation[]) => void
  addRelation: (relation: Relation) => void
  removeRelation: (id: number) => void
  setSelectedTableId: (id: number | null) => void
  setSelectedColumn: (col: ColumnMeta | null) => void
  setLoading: (loading: boolean) => void
}

export const useAnalysisStore = create<AnalysisState>((set) => ({
  project: null,
  tables: [],
  schemas: [],
  selectedSchema: null,
  tableDetails: {},
  canvasTableIds: new Set(),
  canvasNodes: [],
  relations: [],
  selectedTableId: null,
  selectedColumn: null,
  loading: false,
  setProject: (project) => set((state) => state.project?.id === project?.id ? { project } : {
    project, tables: [], schemas: [], selectedSchema: null, tableDetails: {},
    canvasTableIds: new Set<number>(), canvasNodes: [], relations: [], selectedTableId: null, selectedColumn: null,
  }),
  setTables: (tables) => set({ tables }),
  setSchemas: (schemas) => set({ schemas }),
  setSelectedSchema: (selectedSchema) => set({ selectedSchema }),
  setTableDetail: (detail) =>
    set((s) => ({ tableDetails: { ...s.tableDetails, [detail.table.id]: detail } })),
  setCanvasTableIds: (ids) => set({ canvasTableIds: new Set(ids) }),
  setCanvasNodes: (canvasNodes) => set({ canvasNodes }),
  addCanvasTableId: (id) => set((s) => ({ canvasTableIds: new Set([...s.canvasTableIds, id]) })),
  removeCanvasTableId: (id) =>
    set((s) => {
      const next = new Set(s.canvasTableIds)
      next.delete(id)
      return { canvasTableIds: next }
    }),
  setRelations: (relations) => set({ relations }),
  addRelation: (relation) => set((s) => ({ relations: [...s.relations, relation] })),
  removeRelation: (id) => set((s) => ({ relations: s.relations.filter((r) => r.id !== id) })),
  setSelectedTableId: (selectedTableId) => set({ selectedTableId, selectedColumn: null }),
  setSelectedColumn: (selectedColumn) => set({ selectedColumn }),
  setLoading: (loading) => set({ loading }),
}))
