<template>
  <div class="user-panel">
    <div class="section-tabs">
      <el-radio-group v-model="activeSubTab" size="default">
        <el-radio-button value="users">用户管理</el-radio-button>
        <el-radio-button v-if="userStore.isSuperAdmin" value="roles">角色管理</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="activeSubTab === 'users'">
    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-value">{{ list.length }}</div>
        <div class="stat-label">用户总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ enabledCount }}</div>
        <div class="stat-label">启用中</div>
      </div>
      <div v-if="userStore.isSuperAdmin" class="stat-card">
        <div class="stat-value">{{ superAdminCount }}</div>
        <div class="stat-label">超级管理员</div>
      </div>
    </div>

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
          placeholder="搜索用户名/姓名"
          prefix-icon="Search"
          clearable
          style="width: 220px"
          @input="handleSearch"
        />
      </div>
      <div class="filter-actions">
        <el-button type="primary" @click="openCreate">
          <el-icon class="btn-i"><Plus /></el-icon> 新增用户
        </el-button>
      </div>
    </div>

    <!-- 用户列表 -->
    <div class="table-container">
      <el-table :data="filteredList" v-loading="loading" stripe empty-text="暂无数据！">
        <el-table-column prop="username" label="用户名" min-width="120">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ row.username?.charAt(0).toUpperCase() }}</div>
              <span class="username">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" min-width="100">
          <template #default="{ row }">
            <span class="name-text">{{ row.name || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="name-text">{{ row.email || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.role)" effect="light" round>
              {{ getRoleName(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="管辖区域" width="150">
          <template #default="{ row }">
            <span class="region-text">{{ getRegionName(row.regionId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.lastLoginAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
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
        </el-table-column>
      </el-table>
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
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/index'
import { userPermissionApi } from '../../api/admin'
import { useUserStore } from '../../stores/user'
import RolePanel from './RolePanel.vue'

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
  return roots
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
  flatRegions.forEach(r => {
    if (r.parentId === parentId) {
      ids.add(r.id)
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
    allowedIds.add(filterRegionId.value)
    result = result.filter(u => u.regionId && allowedIds.has(u.regionId))
  }
  // 关键词搜索
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    result = result.filter(u =>
      u.username?.toLowerCase().includes(kw) ||
      u.name?.toLowerCase().includes(kw) ||
      u.email?.toLowerCase().includes(kw)
    )
  }
  return result
})

const enabledCount = computed(() => list.value.filter(u => u.status === 1).length)
const superAdminCount = computed(() => list.value.filter(u => u.role === 'SUPER_ADMIN').length)

async function fetchList() {
  loading.value = true
  try {
    list.value = await request.get('/admin/users')
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function fetchRegions() {
  try {
    const params = {}
    if (!userStore.isSuperAdmin && userStore.userId) {
      params.userId = userStore.userId
    }
    regions.value = await request.get('/regions', { params })
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
  form.value = {
    id: row.id,
    username: row.username,
    name: row.name,
    email: row.email || '',
    role: row.role,
    regionId: row.regionId,
    regionCode: '',
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
  if (!regionId) return '—'
  const region = regions.value.find(r => r.id === regionId)
  return region ? region.name : '—'
}

function formatTime(time) {
  if (!time) return '—'
  if (Array.isArray(time)) {
    return `${time[0]}-${String(time[1]).padStart(2, '0')}-${String(time[2]).padStart(2, '0')} ${String(time[3]).padStart(2, '0')}:${String(time[4]).padStart(2, '0')}`
  }
  return time
}

onMounted(() => {
  fetchList()
  fetchRegions()
  fetchRoles()
})
</script>

<style scoped>
.user-panel {
  max-width: 1200px;
}

.section-tabs {
  display: flex;
  align-items: center;
  margin-bottom: var(--sp-5);
}

.stats-row {
  display: flex;
  gap: var(--sp-4);
  margin-bottom: var(--sp-5);
}

.stat-card {
  background: linear-gradient(135deg, var(--primary) 0%, var(--accent-dim) 100%);
  color: #ffffff;
  padding: var(--sp-4) var(--sp-6);
  border-radius: var(--radius-lg);
  min-width: 140px;
  box-shadow: var(--glow-accent);
}

.stat-card:nth-child(2) {
  background: linear-gradient(135deg, var(--success) 0%, #529b2e 100%);
  box-shadow: 0 2px 8px var(--success-glow);
}

.stat-card:nth-child(3) {
  background: linear-gradient(135deg, var(--danger) 0%, #c45656 100%);
  box-shadow: 0 2px 8px var(--danger-glow);
}

.stat-value {
  font-size: var(--text-2xl);
  font-weight: 700;
  line-height: 1.2;
}

.stat-label {
  font-size: var(--text-xs);
  opacity: 0.9;
  margin-top: var(--sp-1);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);
  background: var(--bg-card);
  padding: var(--sp-4) var(--sp-5);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.filter-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  white-space: nowrap;
}

.filter-actions {
  margin-left: auto;
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

.user-cell {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.user-avatar {
  width: 32px;
  height: 32px;
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
}

.name-text,
.region-text,
.time-text {
  color: var(--text-secondary);
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
</style>
