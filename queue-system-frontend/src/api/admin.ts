import request from './index'
import type { BusinessType, Counter, CounterDTO, Region, SysMenu, SysButton } from '../types'

// 业务类型 API
export const businessTypeApi = {
  list: (params?: { regionId?: number }) =>
    request.get<BusinessType[]>('/admin/business-types', { params }),

  getById: (id: number) =>
    request.get<BusinessType>(`/admin/business-types/${id}`),

  create: (data: Partial<BusinessType>) =>
    request.post<BusinessType>('/admin/business-types', data),

  update: (id: number, data: Partial<BusinessType>) =>
    request.put<BusinessType>(`/admin/business-types/${id}`, data),

  delete: (id: number) =>
    request.delete<void>(`/admin/business-types/${id}`)
}

// 窗口 API
export const counterApi = {
  list: (params?: { regionId?: number; userId?: number }) =>
    request.get<CounterDTO[]>('/admin/counters', { params }),

  getById: (id: number) =>
    request.get<CounterDTO>(`/admin/counters/${id}`),

  create: (data: CounterDTO) =>
    request.post<CounterDTO>('/admin/counters', data),

  update: (id: number, data: Partial<CounterDTO>) =>
    request.put<void>(`/admin/counters/${id}`, data),

  delete: (id: number) =>
    request.delete<void>(`/admin/counters/${id}`),

  getStats: (id: number) =>
    request.get(`/admin/counters/${id}/stats`)
}

// 区域 API
export const regionApi = {
  getCities: () =>
    request.get<Region[]>('/regions/cities'),

  getTowns: (cityId: number) =>
    request.get<Region[]>(`/regions/${cityId}/towns`),

  create: (data: Partial<Region>) =>
    request.post<Region>('/regions', data),

  update: (id: number, data: Partial<Region>) =>
    request.put<Region>(`/regions/${id}`, data),

  delete: (id: number) =>
    request.delete<void>(`/regions/${id}`),

  batchSort: (updates: Array<{ id: number; sortOrder: number }>) =>
    request.post<void>('/regions/batch-sort', updates)
}

// 区域-业务关联 API
export const regionBusinessApi = {
  // 获取区域关联的业务类型
  listByRegion: (regionId: number) =>
    request.get<BusinessType[]>(`/regions/${regionId}/business-types`),

  // 获取可关联的业务类型（全局启用且未关联的）
  listAvailable: (regionId: number) =>
    request.get<BusinessType[]>(`/regions/${regionId}/business-types/available`),

  // 关联业务类型
  link: (regionId: number, data: { businessTypeId: number; isEnabled?: boolean; sortOrder?: number }) =>
    request.post<void>(`/regions/${regionId}/business-types`, data),

  // 批量关联
  batchLink: (regionId: number, businessTypeIds: number[]) =>
    request.post<void>(`/regions/${regionId}/business-types/batch`, businessTypeIds),

  // 取消关联
  unlink: (regionId: number, businessTypeId: number) =>
    request.delete<void>(`/regions/${regionId}/business-types/${businessTypeId}`),

  // 更新区域级别业务启用状态
  updateStatus: (regionId: number, businessTypeId: number, isEnabled: boolean) =>
    request.put<void>(`/regions/${regionId}/business-types/${businessTypeId}/status`, { isEnabled })
}

// 菜单 API
export const menuApi = {
  list: (params?: { userId?: number }) =>
    request.get<SysMenu[]>('/admin/menus', { params }),

  getById: (id: number) =>
    request.get<SysMenu>(`/admin/menus/${id}`),

  updateSort: (menuIds: number[]) =>
    request.put('/admin/menus/sort', { menuIds }),

  rename: (id: number, name: string) =>
    request.put(`/admin/menus/${id}/rename`, { name }),

  updateParent: (id: number, parentId: number | null) =>
    request.put(`/admin/menus/${id}/parent`, { parentId }),

  createGroup: (name: string) =>
    request.post('/admin/menus/group', { name }),

  deleteGroup: (id: number) =>
    request.delete(`/admin/menus/group/${id}`),

  // 通用 CRUD
  create: (data: Partial<SysMenu>) =>
    request.post<SysMenu>('/admin/menus', data),

  update: (id: number, data: Partial<SysMenu>) =>
    request.put<SysMenu>(`/admin/menus/${id}`, data),

  remove: (id: number) =>
    request.delete<void>(`/admin/menus/${id}`)
}

// 用户权限管理 API
export const userPermissionApi = {
  // 获取用户权限
  get: (userId: number) =>
    request.get(`/admin/users/${userId}/permissions`),

  // 设置用户权限
  set: (userId: number, data: { menuIds?: number[]; buttonIds?: number[] }) =>
    request.put(`/admin/users/${userId}/permissions`, data),

  // 获取当前操作者可授权的菜单
  getAvailableMenus: () =>
    request.get<SysMenu[]>('/admin/users/available-menus'),

  // 获取当前操作者可授权的按钮
  getAvailableButtons: () =>
    request.get<SysButton[]>('/admin/users/available-buttons')
}
