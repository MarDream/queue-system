<template>
  <div class="user-panel">
    <div class="section-tabs">
      <el-radio-group v-model="activeSubTab" size="default">
        <el-radio-button value="users">用户管理</el-radio-button>
        <el-radio-button v-if="userStore.isSuperAdmin" value="roles">角色管理</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="activeSubTab === 'users'">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-group">
        <span class="filter-label">区域</span>
        <el-tree-select
          v-model="filterRegionId"
          :data="regionTree"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="全部区域"
          clearable
          check-strictly
          :render-after-expand="false"
          filterable
          :filter-method="filterRegionByName"
          style="width:200px"
        />
      </div>
      <div class="filter-group">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索用户名/姓名/角色"
          prefix-icon="Search"
          clearable
          style="width: 220px"
          @input="handleSearch"
        />
      </div>
      <div class="filter-group filter-group--compact">
        <el-button-group>
          <el-button @click="expandAllRows">展开全部</el-button>
          <el-button @click="collapseAllRows">收起层级</el-button>
        </el-button-group>
      </div>
      <div class="filter-actions">
        <el-button v-if="isAdmin" :loading="downloadingTemplate" @click="downloadTemplate">
          <el-icon class="btn-i"><Download /></el-icon> 批量导入模板
        </el-button>
        <el-upload
          v-if="isAdmin"
          ref="importUploadRef"
          class="inline-upload"
          :auto-upload="false"
          :show-file-list="false"
          :disabled="importingUsers"
          accept=".xlsx,.xls"
          :on-change="handleImportFile"
        >
          <el-button type="success" :loading="importingUsers">
            <el-icon class="btn-i"><Upload /></el-icon> 批量导入
          </el-button>
        </el-upload>
        <el-button type="primary" @click="openCreate">
          <el-icon class="btn-i"><Plus /></el-icon> 新增用户
        </el-button>
      </div>
    </div>
    <div class="view-summary">
      <span class="view-summary__mode">按区域层级展示</span>
      <span class="view-summary__text">当前显示 {{ visibleRegionCount }} 个区域分组，{{ visibleUserCount }} 个用户</span>
      <div class="view-summary__stats">
        <span class="view-stat">
          <span class="view-stat__label">总用户</span>
          <strong class="view-stat__value">{{ list.length }}</strong>
        </span>
        <span class="view-stat">
          <span class="view-stat__label">启用中</span>
          <strong class="view-stat__value is-success">{{ enabledCount }}</strong>
        </span>
        <span v-if="userStore.isSuperAdmin" class="view-stat">
          <span class="view-stat__label">超级管理员</span>
          <strong class="view-stat__value is-danger">{{ superAdminCount }}</strong>
        </span>
      </div>
    </div>

    <!-- 用户列表 -->
    <div class="table-container">
      <el-table
        :key="tableRenderKey"
        ref="tableRef"
        :data="pagedUserTreeData"
        v-loading="loading"
        stripe
        row-key="rowKey"
        :tree-props="{ children: 'children' }"
        :expand-row-keys="expandedUserRegionRowKeys"
        :row-class-name="rowClassName"
        :max-height="tableMaxHeight"
        empty-text="暂无数据！"
      >
        <el-table-column prop="username" label="用户名" min-width="160">
          <template #default="{ row }">
            <div v-if="row.rowType === REGION_ROW_TYPE" class="region-node">
              <div class="region-node__title">
                <span class="region-node__icon" :class="{ 'is-virtual': row.isVirtualRegion }">
                  {{ row.isVirtualRegion ? '⌘' : '◈' }}
                </span>
                <span class="region-node__name">{{ row.name }}</span>
                <button
                  v-if="hasUserRegionChildren(row)"
                  type="button"
                  class="region-node__toggle"
                  :aria-label="isUserRegionExpanded(row) ? '折叠区域' : '展开区域'"
                  @click.stop="toggleUserRegionRow(row)"
                >
                  <el-icon :class="['region-node__toggle-icon', { 'is-expanded': isUserRegionExpanded(row) }]">
                    <ArrowRight />
                  </el-icon>
                </button>
                <el-tag
                  size="small"
                  effect="plain"
                  :type="row.isVirtualRegion ? (row.virtualTagType || 'info') : levelTagType(row.level)"
                >
                  {{ row.isVirtualRegion ? row.virtualTagLabel : levelLabel(row.level) }}
                </el-tag>
              </div>
            </div>
            <div v-else class="user-cell">
              <div class="user-avatar">{{ row.username?.charAt(0).toUpperCase() }}</div>
              <span class="username">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" min-width="90">
          <template #default="{ row }">
            <span v-if="row.rowType === REGION_ROW_TYPE" class="region-summary">
              本级 {{ row.directUserCount }} / 累计 {{ row.userCount }}
            </span>
            <span v-else class="name-text">{{ row.name || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="name-text">{{ row.rowType === USER_ROW_TYPE ? (row.email || '—') : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.rowType === USER_ROW_TYPE" :type="getRoleType(row.role)" effect="light" round>
              {{ getRoleName(row.role) }}
            </el-tag>
            <el-tag v-else type="info" effect="plain" round>
              区域分组
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="管辖区域" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div v-if="row.rowType === USER_ROW_TYPE" class="region-cell">
              <span class="region-text">{{ row.regionPath || '—' }}</span>
            </div>
            <span v-else class="region-text">{{ row.fullPath || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.rowType === USER_ROW_TYPE" :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
            <span v-else class="cell-dash">—</span>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ row.rowType === USER_ROW_TYPE ? formatTime(row.lastLoginAt) : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.rowType === USER_ROW_TYPE">
            <el-dropdown trigger="click" @command="(cmd) => handleAction(cmd, row)">
              <el-button size="small" link type="primary" class="settings-btn">
                <svg class="gear-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="3"/>
                  <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
                </svg>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canActivate(row)" command="activate">激活</el-dropdown-item>
                  <el-dropdown-item command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item command="resetPwd">重置密码</el-dropdown-item>
                  <el-dropdown-item v-if="canManagePermissions(row)" command="permission" divided>权限配置</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button v-if="canDelete(row)" size="small" link type="danger" @click="handleDelete(row)" class="delete-icon-btn" title="删除">
              <svg class="trash-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                <line x1="10" y1="11" x2="10" y2="17"/>
                <line x1="14" y1="11" x2="14" y2="17"/>
              </svg>
            </el-button>
            </template>
            <span v-else class="cell-dash">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[8, 12, 20, 30]"
          :total="rootGroupTotal"
          layout="total, sizes, prev, pager, next"
          background
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" width="480px" draggable resizable destroy-on-close :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ isEdit ? '编辑用户' : '新增用户' }}</span>
          <el-button circle size="small" class="icon-close-btn" @click="dialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px" class="user-form">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="用于找回密码" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width:100%" @change="onRoleChange">
            <el-option v-for="r in availableRoles" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="管辖区域" prop="regionId">
          <el-tree-select
            v-model="form.regionId"
            :data="regionTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="请选择管辖区域"
            clearable
            check-strictly
            :render-after-expand="false"
            :fallback-option="false"
            filterable
            :filter-method="filterRegionByName"
            style="width:100%"
            :disabled="form.role === 'SUPER_ADMIN'"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%" :disabled="statusSelectDisabled">
            <el-option v-if="isEdit && form.status === 0" label="待激活" :value="0" />
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="密码" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权限配置弹窗 -->
    <el-dialog v-model="permDialogVisible" width="720px" draggable align-center
               :close-on-click-modal="false" destroy-on-close class="perm-dialog" :resizable="true" :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">菜单权限配置</span>
          <el-button circle size="small" class="icon-close-btn" @click="permDialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <div class="perm-info">
        <span>用户：<strong>{{ permTarget?.username }}</strong></span>
        <el-tag size="small" :type="getRoleType(permTarget?.role)">{{ getRoleName(permTarget?.role) }}</el-tag>
      </div>

      <div class="perm-groups">
        <div v-for="group in permGroups" :key="group.menu.id" class="perm-group">
          <!-- 菜单行 -->
          <div class="perm-group-header">
            <el-checkbox :value="group.menu.id" v-model="group.menuChecked" @change="onMenuToggle(group)">
              <span class="perm-menu-label">
                <span class="perm-name">{{ group.menu.name }}</span>
              </span>
            </el-checkbox>
          </div>
          <!-- 按钮列表（缩进） -->
          <div class="perm-buttons" v-if="group.buttons.length > 0">
            <span class="perm-buttons-label">按钮：</span>
            <el-checkbox-group v-model="permForm.buttonIds" class="perm-buttons-list">
              <el-checkbox v-for="btn in group.buttons" :key="btn.id" :value="btn.id" :label="btn.id"
                           :disabled="!group.menuChecked">
                <span class="perm-btn-item">{{ btn.name }}</span>
              </el-checkbox>
            </el-checkbox-group>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="handlePermSave">保存</el-button>
      </template>
    </el-dialog>
    </template>

    <RolePanel v-else />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ArrowRight, Plus, Download, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import request from '../../api/index'
import { userPermissionApi } from '../../api/admin'
import { useUserStore } from '../../stores/user'
import RolePanel from './RolePanel.vue'
import { formatDateTime } from '../../utils/dateTime'

const props = defineProps({
  initialTab: {
    type: String,
    default: 'users'
  }
})

const userStore = useUserStore()
const activeSubTab = ref(props.initialTab === 'roles' ? 'roles' : 'users')

watch(
  () => props.initialTab,
  (value) => {
    activeSubTab.value = value === 'roles' && userStore.isSuperAdmin ? 'roles' : 'users'
  },
  { immediate: true }
)

const list = ref([])
const regions = ref([])
const roles = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const searchKeyword = ref('')
const filterRegionId = ref(null)
const expandAll = ref(false)
const tableRenderKey = ref(0)
const tableRef = ref(null)
const expandedUserRegionRowKeys = ref([])
const currentPage = ref(1)
const pageSize = ref(8)
const tableMaxHeight = ref(560)
const REGION_ROW_TYPE = 'region'
const USER_ROW_TYPE = 'user'
const ROOT_REGION_KEY = 'root'
const PLATFORM_NODE_KEY = 'platform-root'
const DETACHED_NODE_KEY = 'detached-root'

watch([searchKeyword, filterRegionId], () => {
  currentPage.value = 1
  expandAllRows()
})

watch(pageSize, () => {
  currentPage.value = 1
})

// 批量导入相关
const downloadingTemplate = ref(false)
const importingUsers = ref(false)
const importUploadRef = ref(null)

// 判断是否为管理员
const isAdmin = computed(() => {
  return userStore.isSuperAdmin || userStore.isRegionAdmin
})

// 权限配置相关
const permDialogVisible = ref(false)
const permTarget = ref(null)
const permSaving = ref(false)
const availableMenus = ref([])
const availableButtons = ref([])
const permForm = ref({ menuIds: [], buttonIds: [] })

/**
 * 按菜单分组按钮权限，形成 { menu, menuChecked, buttons }[] 结构
 */
const permGroups = computed(() => {
  return availableMenus.value.map(menu => {
    const buttons = availableButtons.value.filter(b => b.menuId === menu.id)
    const menuChecked = permForm.value.menuIds.includes(menu.id)
    return { menu, menuChecked, buttons }
  })
})

/**
 * 菜单复选框切换：勾选时自动勾选其下所有按钮；取消时清空其下所有按钮
 */
function onMenuToggle(group) {
  const menuId = group.menu.id
  const buttonIds = group.buttons.map(b => b.id)

  if (group.menuChecked) {
    // 勾选菜单：加入 menuIds + 其下所有按钮
    if (!permForm.value.menuIds.includes(menuId)) {
      permForm.value.menuIds.push(menuId)
    }
    for (const bid of buttonIds) {
      if (!permForm.value.buttonIds.includes(bid)) {
        permForm.value.buttonIds.push(bid)
      }
    }
  } else {
    // 取消勾选菜单：移除 menuIds + 其下所有按钮
    permForm.value.menuIds = permForm.value.menuIds.filter(id => id !== menuId)
    permForm.value.buttonIds = permForm.value.buttonIds.filter(id => !buttonIds.includes(id))
  }
}

const formRef = ref(null)

const formRules = computed(() => ({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [{
    validator: (rule, value, callback) => {
      const v = String(value || '').trim()
      if (v && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) {
        callback(new Error('邮箱格式不正确'))
        return
      }
      callback()
    },
    trigger: 'blur'
  }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  regionId: [{
    validator: (rule, value, callback) => {
      if (form.value.role === 'SUPER_ADMIN') {
        callback()
      } else if (!value) {
        callback(new Error('请选择管辖区域'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }]
}))

const form = ref({
  username: '',
  name: '',
  email: '',
  role: '',
  regionId: null,
  regionCode: '',
  status: 1,
  password: ''
})

// 监听 regionId 变化，自动设置 regionCode
watch(
  () => form.value.regionId,
  (newRegionId) => {
    if (!newRegionId) {
      form.value.regionCode = ''
      return
    }
    const id = Number(newRegionId)
    const region = regions.value.find(r => Number(r.id) === id)
    form.value.regionCode = region?.code || region?.regionCode || ''
  }
)

// 按角色过滤可选角色（新增时不允许选超级管理员）
const availableRoles = computed(() => {
  const all = (roles.value || []).map(r => ({ label: r.name, value: r.code, type: r.type }))
  if (userStore.isSuperAdmin) {
    if (isEdit.value && form.value.role === 'SUPER_ADMIN') {
      return all
    }
    return all.filter(r => r.value !== 'SUPER_ADMIN')
  }
  if (userStore.isRegionAdmin) {
    return all.filter(r => r.value !== 'SUPER_ADMIN' && r.value !== 'REGION_ADMIN')
  }
  return []
})

// 区域树形结构
const regionTree = computed(() => buildRegionTree(regions.value))
const regionMap = computed(() => {
  const map = new Map()
  ;(regions.value || []).forEach(region => {
    const regionId = normalizeRegionId(region.id)
    if (regionId != null) {
      map.set(regionId, region)
    }
  })
  return map
})
const regionChildrenMap = computed(() => {
  const map = new Map()
  ;(regions.value || [])
    .slice()
    .sort(compareRegionOrder)
    .forEach(region => {
      const parentId = normalizeRegionId(region.parentId)
      const parentKey = parentId == null || !regionMap.value.has(parentId)
        ? ROOT_REGION_KEY
        : parentId
      if (!map.has(parentKey)) {
        map.set(parentKey, [])
      }
      map.get(parentKey).push(region)
    })
  return map
})

function normalizeRegionId(value) {
  if (value === null || value === undefined || value === '') return null
  const regionId = Number(value)
  if (!Number.isFinite(regionId) || regionId <= 0) return null
  return regionId
}

function compareRegionOrder(a, b) {
  const sortDiff = Number(a?.sortOrder || 0) - Number(b?.sortOrder || 0)
  if (sortDiff !== 0) return sortDiff
  return Number(a?.id || 0) - Number(b?.id || 0)
}

function compareUserOrder(a, b) {
  const primary = String(a?.name || a?.username || '').localeCompare(String(b?.name || b?.username || ''), 'zh-CN')
  if (primary !== 0) return primary
  const secondary = String(a?.username || '').localeCompare(String(b?.username || ''), 'zh-CN')
  if (secondary !== 0) return secondary
  return Number(a?.id || 0) - Number(b?.id || 0)
}

function buildRegionTree(flatRegions) {
  if (!flatRegions || flatRegions.length === 0) return []
  const map = {}
  const roots = []
  flatRegions.forEach(r => {
    map[r.id] = { ...r, children: [] }
  })
  flatRegions.forEach(r => {
    const node = map[r.id]
    if (!r.parentId || !map[r.parentId]) {
      roots.push(node)
    } else {
      map[r.parentId].children.push(node)
    }
  })
  const sortNodes = (nodes) => {
    nodes.sort(compareRegionOrder)
    nodes.forEach(node => {
      if (node.children?.length) {
        sortNodes(node.children)
      }
    })
  }
  sortNodes(roots)
  return roots
}

function levelTagType(level) {
  const map = {
    city: 'danger',
    town: 'warning',
    street: 'success'
  }
  return map[level] || 'info'
}

function levelLabel(level) {
  const map = {
    city: '市级',
    town: '镇/区级',
    street: '街道级'
  }
  return map[level] || '未知层级'
}

function filterRegionByName(query, node) {
  if (!query) return true
  const lowerQuery = query.toLowerCase()
  if (node.name && node.name.toLowerCase().includes(lowerQuery)) return true
  if (node.children && node.children.some(child => filterRegionByName(query, child))) return true
  return false
}

function getDescendantRegionIds(parentId, flatRegions) {
  const ids = new Set()
  const normalizedParentId = normalizeRegionId(parentId)
  flatRegions.forEach(r => {
    if (normalizeRegionId(r.parentId) === normalizedParentId) {
      const childId = normalizeRegionId(r.id)
      if (childId == null) return
      ids.add(childId)
      getDescendantRegionIds(r.id, flatRegions).forEach(childId => ids.add(childId))
    }
  })
  return ids
}

// 判断当前操作者能否管理某用户的权限
function canManagePermissions(row) {
  if (userStore.isSuperAdmin) {
    return row.role !== 'SUPER_ADMIN' // 超级管理员不能编辑自己的权限（没必要）
  }
  if (userStore.isRegionAdmin) {
    return row.role === 'WINDOW_OPERATOR'
  }
  return false
}

const filteredList = computed(() => {
  let result = list.value
  // 区域筛选（含后代区域递归匹配）
  if (filterRegionId.value) {
    const allowedIds = getDescendantRegionIds(filterRegionId.value, regions.value)
    const selectedRegionId = normalizeRegionId(filterRegionId.value)
    if (selectedRegionId != null) {
      allowedIds.add(selectedRegionId)
    }
    result = result.filter(u => {
      const userRegionId = normalizeRegionId(u.regionId)
      return userRegionId != null && allowedIds.has(userRegionId)
    })
  }
  // 关键词搜索
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    result = result.filter(u =>
      u.username?.toLowerCase().includes(kw) ||
      u.name?.toLowerCase().includes(kw) ||
      u.email?.toLowerCase().includes(kw) ||
      getRoleName(u.role)?.toLowerCase().includes(kw) ||
      getRegionPathText(u.regionId).toLowerCase().includes(kw)
    )
  }
  return result
})

const userTreeData = computed(() => {
  const usersByRegion = new Map()
  const platformUsers = []
  const detachedUsers = []
  const matchedUsers = filteredList.value.slice().sort(compareUserOrder)

  matchedUsers.forEach(user => {
    const regionId = normalizeRegionId(user.regionId)
    if (regionId == null) {
      platformUsers.push(user)
      return
    }
    if (!regionMap.value.has(regionId)) {
      detachedUsers.push(user)
      return
    }
    if (!usersByRegion.has(regionId)) {
      usersByRegion.set(regionId, [])
    }
    usersByRegion.get(regionId).push(user)
  })

  const buildUserNode = (user) => ({
    ...user,
    rowKey: `user-${user.id}`,
    rowType: USER_ROW_TYPE,
    regionPath: getRegionPathText(user.regionId)
  })

  const buildRegionNode = (region) => {
    const regionId = normalizeRegionId(region.id)
    if (regionId == null) return null

    const childRegionNodes = (regionChildrenMap.value.get(regionId) || [])
      .map(buildRegionNode)
      .filter(Boolean)
    const directUsers = (usersByRegion.get(regionId) || [])
      .slice()
      .sort(compareUserOrder)
      .map(buildUserNode)
    const userCount = directUsers.length + childRegionNodes.reduce((sum, node) => sum + node.userCount, 0)

    if (userCount === 0) return null

    return {
      rowKey: `region-${regionId}`,
      rowType: REGION_ROW_TYPE,
      isVirtualRegion: false,
      virtualTagLabel: '',
      virtualTagType: '',
      regionId,
      name: region.name,
      level: region.level,
      fullPath: getRegionPathText(regionId),
      directUserCount: directUsers.length,
      userCount,
      children: [...childRegionNodes, ...directUsers]
    }
  }

  const buildVirtualNode = (key, name, users, tagLabel, tagType) => ({
    rowKey: key,
    rowType: REGION_ROW_TYPE,
    isVirtualRegion: true,
    virtualTagLabel: tagLabel,
    virtualTagType: tagType,
    name,
    level: '',
    fullPath: name,
    directUserCount: users.length,
    userCount: users.length,
    children: users.slice().sort(compareUserOrder).map(buildUserNode)
  })

  const selectedRegionId = normalizeRegionId(filterRegionId.value)
  const rootRegions = selectedRegionId != null
    ? [regionMap.value.get(selectedRegionId)].filter(Boolean)
    : (regionChildrenMap.value.get(ROOT_REGION_KEY) || [])

  const tree = rootRegions.map(buildRegionNode).filter(Boolean)

  if (selectedRegionId == null && platformUsers.length > 0) {
    tree.unshift(buildVirtualNode(PLATFORM_NODE_KEY, '平台级用户', platformUsers, '平台级', 'danger'))
  }
  if (selectedRegionId == null && detachedUsers.length > 0) {
    tree.push(buildVirtualNode(DETACHED_NODE_KEY, '未匹配区域', detachedUsers, '异常区域', 'warning'))
  }

  return tree
})
const rootGroupTotal = computed(() => userTreeData.value.length)
const pagedUserTreeData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return userTreeData.value.slice(start, end)
})
const visibleUserCount = computed(() => filteredList.value.length)
const visibleRegionCount = computed(() => countRegionNodes(userTreeData.value))

watch(rootGroupTotal, (total) => {
  const totalPages = Math.max(1, Math.ceil(total / pageSize.value))
  if (currentPage.value > totalPages) {
    currentPage.value = totalPages
  }
})

function syncTableMaxHeight() {
  if (typeof window === 'undefined') return
  tableMaxHeight.value = Math.max(window.innerHeight - 360, 360)
}

function countRegionNodes(nodes) {
  return (nodes || []).reduce((sum, node) => {
    if (node.rowType !== REGION_ROW_TYPE) return sum
    return sum + 1 + countRegionNodes(node.children || [])
  }, 0)
}

const enabledCount = computed(() => list.value.filter(u => u.status === 1).length)
const superAdminCount = computed(() => list.value.filter(u => u.role === 'SUPER_ADMIN').length)

async function fetchList() {
  loading.value = true
  try {
    // 确保区域数据已加载，避免数据竞争导致 userTreeData 为空
    if (regions.value.length === 0) {
      await fetchRegions()
    }
    list.value = await request.get('/admin/users')
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function fetchRegions() {
  try {
    regions.value = await request.get('/regions')
  } catch {
    regions.value = []
  }
}

async function fetchRoles() {
  try {
    const data = await request.get('/admin/roles')
    roles.value = (data || []).slice().sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
  } catch {
    roles.value = []
  }
}

function handleSearch() {
  // 搜索由 computed 属性处理
}

function rerenderTreeTable() {
  tableRenderKey.value += 1
}

function expandAllRows() {
  expandAll.value = true
  expandedUserRegionRowKeys.value = collectUserRegionExpandKeys(userTreeData.value)
}

function collapseAllRows() {
  expandAll.value = false
  expandedUserRegionRowKeys.value = []
}

function hasUserRegionChildren(row) {
  return row.rowType === REGION_ROW_TYPE && Array.isArray(row.children) && row.children.length > 0
}

function isUserRegionExpanded(row) {
  return expandedUserRegionRowKeys.value.includes(row.rowKey)
}

function toggleUserRegionRow(row) {
  if (!hasUserRegionChildren(row)) return

  const nextExpanded = !isUserRegionExpanded(row)
  const keySet = new Set(expandedUserRegionRowKeys.value)

  if (nextExpanded) {
    keySet.add(row.rowKey)
  } else {
    keySet.delete(row.rowKey)
  }

  expandedUserRegionRowKeys.value = Array.from(keySet)
  tableRef.value?.toggleRowExpansion?.(row, nextExpanded)
}

function onRoleChange() {
  // 切换角色时清除区域校验提示
  formRef.value?.clearValidate(['regionId'])
  // 超级管理员不需要区域，清空；窗口操作员保留区域选择能力
  if (form.value.role === 'SUPER_ADMIN') {
    form.value.regionId = null
  }
  // 区域管理员角色时，如果当前区域为空且操作者是区域管理员，默认选操作者自己的区域
  if (form.value.role === 'REGION_ADMIN' && !form.value.regionId && !userStore.isSuperAdmin && userStore.regionId) {
    form.value.regionId = Number(userStore.regionId)
  }
}

function openCreate() {
  isEdit.value = false
  form.value = {
    username: '',
    name: '',
    email: '',
    role: '',
    regionId: filterRegionId.value, // 自动带入筛选区域
    regionCode: '',
    status: 1,
    password: ''
  }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  // 如果 regionId 在区域树中找不到，清空为 null 避免显示数字 ID
  const rid = row.regionId
  const validRegion = rid ? regions.value.find(r => Number(r.id) === Number(rid)) : null
  form.value = {
    id: row.id,
    username: row.username,
    name: row.name,
    email: row.email || '',
    role: row.role,
    regionId: validRegion ? rid : null,
    regionCode: validRegion ? (validRegion.code || validRegion.regionCode || '') : '',
    status: row.status,
    password: ''
  }
  nextTick(() => {
    formRef.value?.clearValidate()
  })
  dialogVisible.value = true
}

/**
 * 统一处理下拉菜单操作
 */
function handleAction(cmd, row) {
  switch (cmd) {
    case 'activate': handleActivate(row); break
    case 'edit': openEdit(row); break
    case 'resetPwd': handleResetPwd(row); break
    case 'permission': openPermissionDialog(row); break
  }
}

const statusSelectDisabled = computed(() => {
  if (form.value.status === 0) return true
  if (!isEdit.value) return false
  if (form.value.role === 'SUPER_ADMIN') return true
  return Number(form.value.id) === Number(userStore.userId)
})

function canDelete(row) {
  if (!userStore.isSuperAdmin) return false
  if (row.role === 'SUPER_ADMIN') return false
  return true
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!isEdit.value && !form.value.password) {
    ElMessage.warning('请输入密码')
    return
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await request.put(`/admin/users/${form.value.id}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/admin/users', form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

function canActivate(row) {
  if (row.status !== 0) return false
  if (userStore.isSuperAdmin) return row.role !== 'SUPER_ADMIN'
  if (userStore.isRegionAdmin) {
    if (row.role === 'REGION_ADMIN' || row.role === 'SUPER_ADMIN') return false
    if (!userStore.regionId || !row.regionId) return false
    const allowedIds = getDescendantRegionIds(Number(userStore.regionId), regions.value)
    allowedIds.add(Number(userStore.regionId))
    return allowedIds.has(row.regionId)
  }
  return false
}

async function handleActivate(row) {
  try {
    await ElMessageBox.confirm(`确认激活账号 "${row.username}" 吗？激活后用户可登录系统。`, '确认激活', {
      confirmButtonText: '激活',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.post(`/admin/users/${row.id}/activate`)
    ElMessage.success('已激活')
    await fetchList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message || '激活失败')
  }
}

function statusLabel(status) {
  if (status === 1) return '启用'
  if (status === 2) return '禁用'
  return '待激活'
}

function statusTagType(status) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

async function handleResetPwd(row) {
  // 生成随机密码
  const randomPwd = generateRandomPassword()

  try {
    const { value: pwdValue, action } = await ElMessageBox.prompt(
      `为用户 "${row.username}" 重置密码`,
      '重置密码',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputType: 'text',
        inputValue: randomPwd,
        inputPlaceholder: '留空则使用随机密码，或输入自定义密码',
        inputValidator: (val) => {
          if (val === null || val === '') return true // 留空使用随机密码
          if (!val.trim()) return '密码不能为空或空格'
          if (val.length < 6) return '密码长度不能少于6位'
          return true
        },
        inputErrorMessage: '密码格式不正确'
      }
    )

    if (action === 'cancel') return

    // 如果输入为空或全是空格，使用随机密码
    const finalPwd = (pwdValue && pwdValue.trim()) ? pwdValue.trim() : randomPwd

    await request.post(`/admin/users/${row.id}/reset-password`, { password: finalPwd })
    ElMessageBox.alert(
      `新密码为：<strong style="color:#409eff;font-size:18px">${finalPwd}</strong>`,
      '密码已重置',
      { confirmButtonText: '我知道了', dangerouslyUseHTMLString: true }
    )
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message || '重置失败')
  }
}

// 生成8位随机密码（大写字母+小写字母+数字）
function generateRandomPassword() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  let pwd = ''
  for (let i = 0; i < 8; i++) {
    pwd += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return pwd
}

async function handleDelete(row) {
  if (!canDelete(row)) return
  try {
    await ElMessageBox.confirm(`确定要删除用户 "${row.username}" 吗？此操作不可恢复。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
    await request.delete(`/admin/users/${row.id}`)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message)
  }
}

// ==================== 权限管理 ====================

async function openPermissionDialog(row) {
  permTarget.value = row
  permDialogVisible.value = true

  try {
    const [menus, buttons, perm] = await Promise.all([
      userPermissionApi.getAvailableMenus(row.id),
      userPermissionApi.getAvailableButtons(row.id),
      userPermissionApi.get(row.id)
    ])
    availableMenus.value = menus || []
    availableButtons.value = buttons || []

    const hasUserPermissionConfig = perm?.menuIds != null || perm?.buttonIds != null
    if (hasUserPermissionConfig) {
      permForm.value = {
        menuIds: perm.menuIds || [],
        buttonIds: perm.buttonIds || []
      }
    } else {
      permForm.value = {
        menuIds: availableMenus.value.map(menu => menu.id),
        buttonIds: availableButtons.value.map(button => button.id)
      }
    }
  } catch {
    availableMenus.value = []
    availableButtons.value = []
    permForm.value = { menuIds: [], buttonIds: [] }
  }
}

async function handlePermSave() {
  permSaving.value = true
  try {
    await userPermissionApi.set(permTarget.value.id, {
      menuIds: permForm.value.menuIds,
      buttonIds: permForm.value.buttonIds
    })
    ElMessage.success('权限配置已更新')
    permDialogVisible.value = false
  } catch (err) {
    ElMessage.error(err.message || '权限配置失败')
  } finally {
    permSaving.value = false
  }
}

function getRoleName(role) {
  const hit = (roles.value || []).find(r => r.code === role)
  if (hit && hit.name) return hit.name
  const map = {
    SUPER_ADMIN: '超级管理员',
    REGION_ADMIN: '区域管理员',
    WINDOW_OPERATOR: '窗口操作员'
  }
  return map[role] || role
}

function getRoleType(role) {
  const map = {
    SUPER_ADMIN: 'danger',
    REGION_ADMIN: 'warning',
    WINDOW_OPERATOR: 'success'
  }
  return map[role] || 'info'
}

function getRegionName(regionId) {
  const normalizedRegionId = normalizeRegionId(regionId)
  if (normalizedRegionId == null) return '—'
  const region = regionMap.value.get(normalizedRegionId)
  return region ? region.name : '—'
}

function getRegionPathText(regionId) {
  const normalizedRegionId = normalizeRegionId(regionId)
  if (normalizedRegionId == null) return '—'
  const pathNames = []
  const visited = new Set()
  let currentId = normalizedRegionId

  while (currentId != null && !visited.has(currentId)) {
    visited.add(currentId)
    const current = regionMap.value.get(currentId)
    if (!current) {
      break
    }
    pathNames.unshift(current.name)
    currentId = normalizeRegionId(current.parentId)
  }

  return pathNames.length > 0 ? pathNames.join(' / ') : '—'
}

function rowClassName({ row }) {
  return hasUserRegionChildren(row) ? 'user-region-row user-region-row-expandable' : (row.rowType === REGION_ROW_TYPE ? 'user-region-row' : '')
}

function formatTime(time) {
  return formatDateTime(time, '—')
}

// ==================== 批量导入 ====================

/**
 * 下载批量导入模板
 */
async function downloadTemplate() {
  downloadingTemplate.value = true
  try {
    const token = localStorage.getItem('token')
    const response = await axios.get('/api/v1/admin/users/import/template', {
      responseType: 'blob',
      headers: {
        Authorization: token ? `Bearer ${token}` : ''
      }
    })

    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'user_import_template.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    ElMessage.success('模板下载成功')
  } catch (error) {
    console.error('下载模板失败:', error)
    ElMessage.error(error.response?.data?.message || error.message || '下载模板失败')
  } finally {
    downloadingTemplate.value = false
  }
}

/**
 * 处理导入文件选择
 */
async function handleImportFile(uploadFile) {
  if (!uploadFile || !uploadFile.raw) {
    return
  }

  const file = uploadFile.raw
  const isExcel = file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    || file.type === 'application/vnd.ms-excel'
    || file.name.endsWith('.xlsx')
    || file.name.endsWith('.xls')

  if (!isExcel) {
    ElMessage.error('只能上传 Excel 文件（.xlsx 或 .xls）')
    return
  }

  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过 10MB')
    return
  }

  // 确认导入
  try {
    await ElMessageBox.confirm(
      '确定要导入该文件中的用户数据吗？',
      '批量导入确认',
      {
        confirmButtonText: '确定导入',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    // 用户取消
    if (importUploadRef.value) {
      importUploadRef.value.clearFiles()
    }
    return
  }

  // 执行导入
  importingUsers.value = true
  const formData = new FormData()
  formData.append('file', file)

  try {
    const result = await request.post('/admin/users/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    // 显示导入结果
    if (result.details && result.details.length > 0) {
      // 有自动生成的密码，需要展示给用户
      showImportResult(result)
    } else {
      ElMessage.success(`成功导入 ${result.importedCount} 个用户`)
    }

    // 刷新用户列表
    await fetchList()

  } catch (error) {
    console.error('导入失败:', error)
    const errorMsg = error.message || '导入失败'
    ElMessage.error(errorMsg)
  } finally {
    importingUsers.value = false
    if (importUploadRef.value) {
      importUploadRef.value.clearFiles()
    }
  }
}

/**
 * 显示导入结果（包含自动生成的密码）
 */
function showImportResult(result) {
  const passwordList = result.details
    .map(d => `${d.username}: ${d.generatedPassword}`)
    .join('\n')

  ElMessageBox.alert(
    `成功导入 ${result.importedCount} 个用户\n\n以下用户使用了自动生成的密码，请妥善保存：\n\n${passwordList}`,
    '导入成功',
    {
      confirmButtonText: '我已保存',
      type: 'success',
      dangerouslyUseHTMLString: false
    }
  )
}

onMounted(async () => {
  syncTableMaxHeight()
  window.addEventListener('resize', syncTableMaxHeight)
  // 先加载区域数据，再加载用户列表
  await fetchRegions()
  await fetchList()
  fetchRoles()
})

watch(userTreeData, (rows) => {
  const validKeys = new Set(collectUserRegionExpandKeys(rows))
  expandedUserRegionRowKeys.value = expandedUserRegionRowKeys.value.filter(key => validKeys.has(key))
})

onUnmounted(() => {
  window.removeEventListener('resize', syncTableMaxHeight)
})

function collectUserRegionExpandKeys(nodes) {
  return (nodes || []).flatMap(node => {
    if (!hasUserRegionChildren(node)) return []
    return [node.rowKey, ...collectUserRegionExpandKeys(node.children || [])]
  })
}
</script>

<style scoped>
.user-panel {
  width: 100%;
  max-width: none;
}

.section-tabs {
  display: flex;
  align-items: center;
  margin-bottom: var(--sp-4);
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-4);
  margin-bottom: var(--sp-3);
  background: var(--bg-card);
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.filter-group--compact {
  margin-left: auto;
}

.filter-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  white-space: nowrap;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.view-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-3);
  margin: -2px 0 var(--sp-3);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.view-summary__mode {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.08);
  color: var(--primary);
  font-weight: 600;
}

.view-summary__stats {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-left: 6px;
}

.view-stat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.25);
}

.view-stat__label {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.view-stat__value {
  color: var(--text-primary);
  font-size: var(--text-sm);
  line-height: 1;
}

.view-stat__value.is-success {
  color: var(--success);
}

.view-stat__value.is-danger {
  color: var(--danger);
}

.btn-icon {
  font-weight: 700;
  margin-right: var(--sp-1);
}

.table-container {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  overflow: hidden;
}

.table-container :deep(.el-table .cell) {
  padding-top: 6px;
  padding-bottom: 6px;
  line-height: 1.35;
}

.table-container :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.table-container :deep(.el-table__body-wrapper) {
  overflow-y: auto;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding: 10px 14px 12px;
  background: var(--bg-card);
  border-top: 1px solid var(--border);
}

.region-node {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 0;
}

.region-node__title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.region-node__icon {
  width: 18px;
  height: 18px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(64, 158, 255, 0.12);
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
}

.region-node__icon.is-virtual {
  background: rgba(230, 162, 60, 0.16);
  color: var(--warning);
}

.region-node__name {
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.region-node__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  background: transparent;
  color: #909399;
  cursor: pointer;
}

.region-node__toggle:hover {
  color: #409eff;
}

.region-node__toggle-icon {
  font-size: 14px;
  transition: transform 0.2s ease, color 0.2s ease;
}

.region-node__toggle-icon.is-expanded {
  transform: rotate(90deg);
}

.region-path,
.region-summary,
.cell-dash {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  position: relative;
  padding-left: 14px;
}

.user-cell::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 8px;
  height: 1px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.85);
  transform: translateY(-50%);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), var(--accent-dim));
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: var(--text-sm);
}

.username {
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.2;
}

.name-text,
.region-text,
.time-text {
  color: var(--text-secondary);
}

.region-cell {
  display: flex;
  align-items: center;
  min-width: 0;
}

.region-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.user-region-row > td.el-table__cell) {
  background: rgba(64, 158, 255, 0.05);
}

:deep(.user-region-row:hover > td.el-table__cell) {
  background: rgba(64, 158, 255, 0.08) !important;
}

:deep(.user-region-row-expandable .el-table__expand-icon) {
  display: none;
}

/* 操作列 - 小螺丝 + 小垃圾桶 */
.settings-btn {
  padding: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.gear-icon {
  width: 18px;
  height: 18px;
  transition: transform 0.3s;
}

.settings-btn:hover .gear-icon {
  transform: rotate(90deg);
}

.delete-icon-btn {
  padding: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: var(--sp-1);
}

.trash-icon {
  width: 18px;
  height: 18px;
}

.action-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-1);
}

.user-form .el-input {
  width: 100%;
}

/* 权限配置弹窗 */
.perm-dialog :deep(.el-dialog) {
  resize: both;
  min-width: 500px;
  min-height: 300px;
  max-width: 95vw;
  max-height: 95vh;
  overflow: hidden;
}

.perm-dialog :deep(.el-dialog__body) {
  overflow: auto;
  max-height: calc(95vh - 120px);
}

/* 权限配置样式 */
.perm-info {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  background: var(--bg-raised);
  border-radius: var(--radius-md);
  margin-bottom: var(--sp-4);
  color: var(--text-primary);
}

.perm-info strong {
  color: var(--primary);
}

.perm-groups {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  max-height: 520px;
  overflow-y: auto;
  padding-right: var(--sp-2);
}

.perm-group {
  background: var(--bg-raised);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--sp-3) var(--sp-4);
  transition: border-color 0.2s;
}

.perm-group:hover {
  border-color: var(--accent-dim);
}

.perm-group-header {
  padding-bottom: var(--sp-2);
}

.perm-menu-label {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}

.perm-buttons {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-2) 0;
  margin-left: var(--sp-6);
  padding-left: var(--sp-3);
  border-left: 2px solid var(--border);
}

.perm-buttons-label {
  font-size: var(--text-xs);
  color: var(--text-muted);
  line-height: 32px;
  white-space: nowrap;
}

.perm-buttons-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1) var(--sp-4);
}

.perm-btn-item {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.perm-name {
  font-weight: 500;
  color: var(--text-primary);
}

.inline-upload {
  display: inline-block;
}

.inline-upload :deep(.el-upload) {
  display: inline-block;
}

.btn-i {
  margin-right: 4px;
}
</style>
