export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

export interface BusinessType {
  id: number
  name: string
  prefix: string
  is_enabled: boolean
  sort_order: number
}

export interface Counter {
  id: number
  number: string
  name: string
  status: string
  current_ticket_id: number | null
  operator_name: string
  businessTypeIds?: number[]
}

export interface CounterDTO {
  id?: number
  number: string
  name: string
  operatorName: string
  businessTypeIds: number[]
}

export interface Region {
  id: number
  name: string
  code: string
  address: string
  isEnabled: boolean
}

export interface SysMenu {
  id: number
  parentId: number | null
  name: string
  path: string
  icon: string
  sortOrder: number
  type?: string
  children?: SysMenu[]
}

export interface SysButton {
  id: number
  menuId: number
  name: string
  code: string
  sortOrder: number
}

export interface SysRole {
  id: number
  code: string
  name: string
  description: string
  sortOrder: number
  type?: string
  deleted?: number
  createdAt?: string | any[]
  updatedAt?: string | any[]
}
