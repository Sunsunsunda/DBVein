import type { ColumnMeta } from '../types'

const TYPE_ABBR: Record<string, string> = {
  VARCHAR2: 'VC',
  VARCHAR: 'VC',
  NVARCHAR2: 'NVC',
  CHAR: 'C',
  NCHAR: 'NC',
  NUMBER: 'N',
  NUMERIC: 'N',
  DECIMAL: 'DEC',
  INTEGER: 'I',
  INT: 'I',
  BIGINT: 'BI',
  SMALLINT: 'SI',
  TINYINT: 'TI',
  FLOAT: 'F',
  DOUBLE: 'D',
  BINARY_FLOAT: 'BF',
  BINARY_DOUBLE: 'BD',
  DATE: 'DT',
  TIMESTAMP: 'TS',
  'TIMESTAMP WITH TIME ZONE': 'TSTZ',
  'TIMESTAMP WITH LOCAL TIME ZONE': 'TSLTZ',
  DATETIME: 'DT',
  TIME: 'TM',
  CLOB: 'CL',
  NCLOB: 'NCL',
  BLOB: 'BL',
  RAW: 'RAW',
  LONG: 'L',
  'LONG RAW': 'LR',
  TEXT: 'TXT',
  TINYTEXT: 'TT',
  MEDIUMTEXT: 'MT',
  LONGTEXT: 'LT',
  JSON: 'JSON',
  BOOLEAN: 'B',
  BOOL: 'B',
}

/** Compact type label for canvas rows; full text via title tooltip. */
export function abbreviateDataType(col: ColumnMeta): string {
  const raw = (col.dataType ?? '').trim()
  if (!raw) return ''
  const upper = raw.toUpperCase()
  const base = upper.replace(/\s*\(.*\)$/, '').trim()
  const abbr = TYPE_ABBR[base] ?? TYPE_ABBR[upper] ?? (base.length <= 4 ? base : base.slice(0, 3))

  if (col.length != null && col.length > 0 && (base.includes('CHAR') || base.includes('RAW') || base === 'VARCHAR' || base === 'VARCHAR2' || base === 'NVARCHAR2')) {
    return `${abbr}(${col.length})`
  }
  if (col.precisionValue != null) {
    const scale = col.scaleValue ?? 0
    if (scale > 0) return `${abbr}(${col.precisionValue},${scale})`
    return `${abbr}(${col.precisionValue})`
  }
  // TIMESTAMP(6) etc. sometimes only in dataType string
  const paren = raw.match(/\(([^)]+)\)/)
  if (paren && !col.length && col.precisionValue == null) {
    return `${abbr}(${paren[1]})`
  }
  return abbr
}

export function fullDataType(col: ColumnMeta): string {
  if (col.length) return `${col.dataType}(${col.length})`
  if (col.precisionValue != null) {
    return `${col.dataType}(${col.precisionValue},${col.scaleValue ?? 0})`
  }
  return col.dataType ?? ''
}
