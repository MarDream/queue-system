// 业务类型（全局定义）
export interface BusinessType {
  id: number
  name: string
  prefix: string
  description: string
  dailyAppointmentLimit: number
  isEnabled: boolean
  sortOrder: number
  version: number
  deleted: number
  createdAt: string[]
  updatedAt: string[]
}

// 区域
export interface Region {
  id: number
  name: string
  code: string
  level: 'city' | 'town' | 'street'
  parentId: number | null
  sortOrder: number
  createdAt?: string | any[]
  children?: Region[]
}

// 窗口
export interface Counter {
  id: number
  regionId: number
  number: number
  name: string
  status: 'idle' | 'busy' | 'paused'
  operatorName: string
  currentTicketId: number | null
  businessTypes?: BusinessType[]
}

// 窗口 DTO
export interface CounterDTO {
  id?: number
  regionId: number
  number: number
  name: string
  status?: string
  operatorName: string
  businessTypeIds: number[]
  businessTypes?: BusinessType[]
}

// 号票
export interface Ticket {
  id: number
  ticketNo: string
  businessTypeId: number
  phone: string
  name: string
  status: 'waiting' | 'called' | 'serving' | 'completed' | 'cancelled' | 'skipped'
  counterId: number | null
  calledAt: string[] | null
  servedAt: string[] | null
  completedAt: string[] | null
  createdAt: string[]
}

// API 响应
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// 菜单
export interface SysMenu {
  id: number
  name: string
  path: string
  icon: string
  sortOrder: number
  parentId: number | null
  type: string
}

// 按钮
export interface SysButton {
  id: number
  menuId: number
  name: string
  code: string
  sortOrder: number
}

// 角色
export interface SysRole {
  id: number
  code: string
  name: string
  description: string
  sortOrder: number
  type: 'SYSTEM' | 'CUSTOM'
}
