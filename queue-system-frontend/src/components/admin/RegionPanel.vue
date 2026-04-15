<template>
  <div class="region-panel">
    <!-- 搜索栏 -->
    <div class="filter-bar">
      <span class="filter-label">区域名称</span>
      <el-input v-model="filterKeyword" placeholder="请输入区域名称" clearable style="width:200px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">
        <el-icon class="btn-i"><Search /></el-icon> 搜索
      </el-button>
      <el-button v-if="canEditCity" type="primary" @click="openCreate('city')">
        <el-icon class="btn-i"><Plus /></el-icon> 新增
      </el-button>
    </div>

    <!-- 树形表格 -->
    <el-table
      ref="tableRef"
      :data="treeData"
      v-loading="tableLoading"
      row-key="id"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      :default-expand-all="false"
      :row-class-name="rowClassName"
      empty-text="暂无数据！"
      style="width:100%"
    >
      <el-table-column prop="name" label="区域名称" min-width="180" />
      <el-table-column prop="code" label="区划代码" width="130" />
      <el-table-column prop="parentName" label="所属父级" width="120">
        <template #default="{ row }">
          {{ row.parentName || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="完整路径" min-width="180">
        <template #default="{ row }">
          {{ getFullPath(row) }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="120" align="center">
        <template #default="{ row }">
          {{ formatCreatedAt(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="级别" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="levelTagType(row.level)" size="small">{{ levelLabel(row.level) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="序号" width="80" align="center" />
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canEdit(row)" size="small" link type="primary" @click="openEdit(row)">
            <el-icon><Edit /></el-icon> 修改
          </el-button>
          <el-button v-if="canAddChild(row)" size="small" link type="success" @click="openCreate(row.level === 'city' ? 'town' : row.level === 'town' ? 'street' : null, row.id, row.level)">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
          <el-button v-if="canDelete(row)" size="small" link type="danger" @click="handleDelete(row)">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑/新增弹窗 -->
    <el-dialog v-model="dialogVisible" width="700px" draggable resizable :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ dialogTitle }}</span>
          <el-button circle size="small" class="icon-close-btn" @click="dialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <div class="section-title">基本信息</div>
      <el-form :model="form" label-width="100px" style="margin-top:12px">
        <el-form-item label="区域名称" required>
          <el-input v-model="form.name" :placeholder="levelPlaceholder(form.level)" />
        </el-form-item>
        <el-form-item label="区划代码">
          <el-input ref="codeInputRef" v-model="form.code" placeholder="如：440300" :class="{ 'code-error': codeHasError }" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>

      <div v-if="isEdit" class="business-config">
        <div class="config-header">
          <span>关联的业务类型</span>
          <el-button type="primary" size="small" @click="openSelectBusiness">关联业务类型</el-button>
        </div>
        <el-table :data="regionBusinessList" empty-text="暂无数据！" style="margin-top:12px">
          <el-table-column prop="name" label="业务名称" />
          <el-table-column prop="prefix" label="前缀" width="80" />
          <el-table-column prop="dailyAppointmentLimit" label="每日预约限额" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.isEnabled" size="small" @change="handleBusinessStatusChange(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-tooltip content="编辑" placement="top">
                <el-button size="small" link type="primary" @click="openEditBusiness(row)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="isEdit" class="announcement-config">
        <div class="section-title" style="margin-top:16px">公告管理</div>
        <div style="padding:12px">
          <el-input
            v-model="form.announcementText"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请输入叫号大屏底部滚动公告内容，如：营业时间、服务热线等"
          />
        </div>
      </div>

      <template #footer>
        <!-- 区划代码重复轻提示 -->
        <div v-if="codeToast.show" class="code-toast" @click="closeCodeToast">
          <div class="code-toast-inner">
            <div class="code-toast-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
            </div>
            <div class="code-toast-body">
              <div class="code-toast-title">区划代码重复</div>
              <div class="code-toast-msg">
                与 <span class="code-toast-region">{{ codeToast.region }}</span>
                （区划代码 <span class="code-toast-code">{{ codeToast.code }}</span>）重复
              </div>
            </div>
            <button class="code-toast-close" @click.stop="closeCodeToast">&times;</button>
            <div class="code-toast-timer">
              <div class="code-toast-timer-bar" :style="{ animationDuration: codeToast.duration + 'ms' }"></div>
            </div>
          </div>
        </div>
        <div class="dialog-footer-row">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 选择业务类型弹窗 -->
    <el-dialog v-model="selectBusinessVisible" width="700px" class="business-select-dialog" draggable resizable :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">选择业务类型</span>
          <el-button circle size="small" class="icon-close-btn" @click="selectBusinessVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <div class="select-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>请选择要关联到「{{ currentRegion?.name }}」的业务类型</span>
      </div>
      <el-table
        :data="availableBusinessList"
        empty-text="暂无数据"
        @selection-change="handleSelectionChange"
        :row-class-name="tableRowClassName"
        highlight-current-row
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="name" label="业务名称" min-width="120">
          <template #default="{ row }">
            <div class="business-name">
              <el-tag :type="row.isEnabled ? 'success' : 'info'" size="small">{{ row.prefix }}</el-tag>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="dailyAppointmentLimit" label="每日限额" width="100" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isEnabled ? 'success' : 'danger'" size="small">
              {{ row.isEnabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="selectedBusinessIds.length" class="selected-count">
        已选择 <strong>{{ selectedBusinessIds.length }}</strong> 项
      </div>
      <template #footer>
        <el-button @click="selectBusinessVisible = false">取消</el-button>
        <el-button type="primary" :loading="linking" @click="linkSelectedBusiness" :disabled="!selectedBusinessIds.length">
          确认关联 {{ selectedBusinessIds.length ? `(${selectedBusinessIds.length})` : '' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑业务类型弹窗 -->
    <el-dialog v-model="editBusinessDialogVisible" width="450px" draggable resizable :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">编辑业务类型</span>
          <el-button circle size="small" class="icon-close-btn" @click="editBusinessDialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <el-form label-width="120px">
        <el-form-item label="业务类型">
          <el-input :value="editingBusiness?.name" disabled />
        </el-form-item>
        <el-form-item label="每日预约限额">
          <el-input-number v-model="editingBusinessForm.dailyAppointmentLimit" :min="1" :max="500" />
        </el-form-item>
        <el-form-item label="区域启用">
          <el-switch v-model="editingBusinessForm.isEnabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editBusinessDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingBusiness" @click="saveBusiness">保存</el-button>
        <el-button type="danger" @click="unlinkBusiness">取消关联</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DCaret, InfoFilled, Plus, Edit, Delete, Search } from '@element-plus/icons-vue'
import request from '../../api/index'
import { regionBusinessApi } from '../../api/admin'
import Sortable from 'sortablejs'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

// 表格数据（树形，支持折叠）
const treeData = ref([])
const allFlatData = ref([]) // 全量扁平数据，用于搜索过滤
const tableLoading = ref(false)
const tableRef = ref(null)

// 搜索
const filterKeyword = ref('')

// 权限
const canEditCity = computed(() => userStore.isSuperAdmin)

// 级别标签
function levelTagType(level) {
  const map = { city: 'danger', town: 'warning', street: 'success' }
  return map[level] || 'info'
}

function levelLabel(level) {
  const map = { city: '市级', town: '镇/区级', street: '街道级' }
  return map[level] || level
}

function levelPlaceholder(level) {
  const map = { city: '如：深圳市', town: '如：南山区', street: '如：粤海街道' }
  return map[level] || '请输入区域名称'
}

// 将扁平数据构建为树形结构
function buildTree(flatList, parentId = null) {
  const children = []
  const nodes = flatList
    .filter(item => (item.parentId ?? null) === (parentId ?? null))
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  for (const item of nodes) {
    const node = { ...item }
    const subs = buildTree(flatList, item.id)
    if (subs.length > 0) {
      node.children = subs
      node.hasChildren = true
    }
    children.push(node)
  }
  return children
}

// 找到数据集中的根节点（parentId 不在数据集 id 中的记录）
function findRoots(flatList) {
  const ids = new Set(flatList.map(r => r.id))
  return flatList.filter(r => r.parentId == null || !ids.has(r.parentId))
}

// 获取全量数据
async function fetchData() {
  tableLoading.value = true
  try {
    const params = { pageNum: 1, pageSize: 9999 }
    if (!userStore.isSuperAdmin) {
      params.userId = Number(userStore.userId) || undefined
    }
    const result = await request.get('/regions/list', { params })
    allFlatData.value = result.records || []
    applyFilter()
  } catch {
    allFlatData.value = []
    treeData.value = []
  } finally {
    tableLoading.value = false
  }
}

// 根据关键词过滤并构建树
function applyFilter() {
  let data = allFlatData.value
  if (filterKeyword.value.trim()) {
    const kw = filterKeyword.value.trim().toLowerCase()
    const matchedIds = new Set()
    data.forEach(r => {
      if ((r.name || '').toLowerCase().includes(kw) || (r.code || '').toLowerCase().includes(kw)) {
        matchedIds.add(r.id)
      }
    })
    const filtered = data.filter(r => isMatchOrAncestor(r, matchedIds, data))
    treeData.value = buildTreeFromRoots(filtered)
  } else {
    treeData.value = buildTreeFromRoots(data)
  }
  nextTick(() => initDragSort())
}

// 从数据集中找到根节点并构建树
function buildTreeFromRoots(flatList) {
  const roots = findRoots(flatList)
  if (roots.length === 0 && flatList.length > 0) {
    // 兜底：所有数据都作为根
    return flatList.sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  }
  return roots
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
    .map(root => {
      const node = { ...root }
      const subs = buildTree(flatList, root.id)
      if (subs.length > 0) {
        node.children = subs
        node.hasChildren = true
      }
      return node
    })
}

function isMatchOrAncestor(item, matchedIds, allData) {
  if (matchedIds.has(item.id)) return true
  if (item.parentId != null) {
    const parent = allData.find(r => r.id === item.parentId)
    if (parent) return isMatchOrAncestor(parent, matchedIds, allData)
  }
  return false
}

function handleSearch() {
  applyFilter()
}

// 为表格行添加 class，标识 parentId 用于拖拽分组
function rowClassName({ row }) {
  const pid = row.parentId ?? 'root'
  return `region-row-pid-${pid}`
}

// 拖拽排序 — 全局一个 Sortable，通过 move 回调限制仅同 parentId 可排序
function initDragSort() {
  nextTick(() => {
    const tbody = tableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!tbody) return

    if (tbody._sortable) {
      tbody._sortable.destroy()
    }

    tbody._sortable = Sortable.create(tbody, {
      handle: '.drag-handle',
      animation: 150,
      // 限制仅同层级可拖拽
      move: (evt) => {
        const fromPid = extractPid(evt.dragged)
        const toPid = extractPid(evt.related)
        return fromPid === toPid
      },
      onEnd: async (evt) => {
        const fromPid = extractPid(evt.item)
        // 获取同组所有行，按当前 DOM 顺序重新分配 sortOrder
        const parentRows = tbody.querySelectorAll(`.region-row-pid-${fromPid}`)
        const updates = []
        parentRows.forEach((tr, idx) => {
          const id = tr.getAttribute('data-row-key')
          if (id) updates.push({ id: Number(id), sortOrder: idx })
        })
        if (updates.length > 0) {
          try {
            await request.post('/regions/batch-sort', updates)
            ElMessage.success('排序已更新')
          } catch (e) {
            ElMessage.error(e.message || '排序更新失败')
          }
        }
        await fetchData()
      }
    })
  })
}

// 从 DOM 行元素中提取 parentId
function extractPid(el) {
  while (el && el.tagName !== 'TR') {
    el = el.parentElement
  }
  if (!el) return null
  for (const cls of el.classList) {
    if (cls.startsWith('region-row-pid-')) {
      return cls.replace('region-row-pid-', '')
    }
  }
  return 'root'
}

// 格式化创建时间
function formatCreatedAt(val) {
  if (!val) return '-'
  const d = Array.isArray(val) ? new Date(val[0], val[1] - 1, val[2], val[3] || 0, val[4] || 0, val[5] || 0) : new Date(val)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

// 获取完整区域路径（如：深圳市-南山区）
function getFullPath(row) {
  if (!row.parentId) {
    // 根节点（city级），无父级
    return '—'
  }
  const pathParts = []
  let current = row
  // 向上递归查找所有祖先
  while (current && current.parentId) {
    const parent = allFlatData.value.find(r => r.id === current.parentId)
    if (parent) {
      pathParts.unshift(parent.name)
      current = parent
    } else {
      break
    }
  }
  pathParts.push(row.name)
  return pathParts.join('-')
}

// 能否新增子级区域（已由后端过滤数据，表格中均为可管辖区域）
function canAddChild(row) {
  if (row.level === 'street') return false // street 是最低级别，不能再新增子级
  if (userStore.isSuperAdmin) return true
  if (userStore.isRegionAdmin) return true
  return false
}

// 能否编辑某区域
function canEdit(row) {
  if (userStore.isSuperAdmin) return true
  if (userStore.isRegionAdmin) return true
  return false
}

// 能否删除某区域（不能删除自己直接所属的区域）
function canDelete(row) {
  if (userStore.isSuperAdmin) return true
  if (userStore.isRegionAdmin) {
    // 不能删除自己直接所属的区域
    if (row.id === userStore.regionId) return false
    return true
  }
  return false
}

// 编辑/新增
const dialogVisible = ref(false)
const dialogTitle = ref('')
const saving = ref(false)
const isEdit = ref(false)
const form = ref({ name: '', code: '', sortOrder: 0, level: 'city', parentId: null, announcementText: '' })
const currentRegion = ref(null)
const regionBusinessList = ref([])
const availableBusinessList = ref([])
const selectedBusinessIds = ref([])
const editingBusiness = ref(null)
const editingBusinessForm = ref({ dailyAppointmentLimit: 50, isEnabled: true })
const selectBusinessVisible = ref(false)
const editBusinessDialogVisible = ref(false)
const linking = ref(false)
const savingBusiness = ref(false)
const codeInputRef = ref(null)
const codeHasError = ref(false)
const codeToast = ref({ show: false, region: '', code: '', duration: 3000 })
let editingId = null
let codeToastTimer = null

async function openCreate(level, parentId = null, parentLevel = null) {
  isEdit.value = false
  editingId = null
  codeHasError.value = false

  let defaultSort = 0
  if (parentId != null) {
    try {
      const siblings = allFlatData.value.filter(r => r.parentId === parentId)
      if (siblings.length > 0) {
        defaultSort = Math.max(...siblings.map(s => s.sortOrder)) + 1
      }
    } catch {
      defaultSort = 0
    }
  }

  form.value = { name: '', code: '', sortOrder: defaultSort, level, parentId }

  if (level === 'city') {
    dialogTitle.value = '新增市级区域'
  } else if (level === 'town') {
    dialogTitle.value = '新增镇/区级区域'
  } else {
    dialogTitle.value = '新增街道级区域'
  }

  regionBusinessList.value = []

  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editingId = row.id
  codeHasError.value = false
  form.value = { name: row.name, code: row.code, sortOrder: row.sortOrder, level: row.level, parentId: row.parentId, announcementText: row.announcementText || '' }
  dialogTitle.value = '编辑区域'
  currentRegion.value = row
  loadRegionBusiness(row.id)
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name) { ElMessage.warning('请输入区域名称'); return }
  saving.value = true
  codeHasError.value = false
  try {
    if (isEdit.value) {
      await request.put(`/regions/${editingId}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/regions', form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchData()
  } catch (e) {
    const msg = e.message || '操作失败'
    // 后端返回格式："区域完整路径|区划代码"
    const pipeIdx = msg.indexOf('|')
    if (pipeIdx > 0) {
      codeHasError.value = true
      const region = msg.substring(0, pipeIdx)
      const code = msg.substring(pipeIdx + 1)
      showCodeToast(region, code)
    } else {
      ElMessage.error(msg)
    }
  } finally {
    saving.value = false
  }
}

function showCodeToast(region, code) {
  closeCodeToast()
  codeToast.value = { show: true, region, code, duration: 3000 }
  // 强制重新触发动画：先移除再添加 timer-bar
  nextTick(() => {
    const bar = document.querySelector('.code-toast-timer-bar')
    if (bar) {
      bar.style.animation = 'none'
      void bar.offsetWidth // reflow
      bar.style.animation = ''
    }
  })
  codeToastTimer = setTimeout(() => closeCodeToast(), 3000)
}

function closeCodeToast() {
  codeToast.value.show = false
  if (codeToastTimer) {
    clearTimeout(codeToastTimer)
    codeToastTimer = null
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？`, '确认')
    await request.delete(`/regions/${row.id}`)
    ElMessage.success('删除成功')
    await fetchData()
  } catch {}
}

// 业务类型关联
async function loadRegionBusiness(regionId) {
  try {
    regionBusinessList.value = await regionBusinessApi.listByRegion(regionId)
  } catch {
    regionBusinessList.value = []
  }
}

async function openSelectBusiness() {
  try {
    availableBusinessList.value = await regionBusinessApi.listAvailable(currentRegion.value.id)
    selectBusinessVisible.value = true
  } catch {
    ElMessage.error('加载失败')
  }
}

function handleSelectionChange(selection) {
  selectedBusinessIds.value = selection.map(s => s.id)
}

function tableRowClassName({ row }) {
  return row.isEnabled ? '' : 'disabled-row'
}

async function linkSelectedBusiness() {
  if (!selectedBusinessIds.value.length) {
    ElMessage.warning('请选择业务类型')
    return
  }
  linking.value = true
  try {
    await regionBusinessApi.batchLink(currentRegion.value.id, selectedBusinessIds.value)
    ElMessage.success('关联成功')
    selectBusinessVisible.value = false
    await loadRegionBusiness(currentRegion.value.id)
  } catch (e) {
    ElMessage.error(e.message || '关联失败')
  } finally {
    linking.value = false
  }
}

function openEditBusiness(row) {
  editingBusiness.value = row
  editingBusinessForm.value = { dailyAppointmentLimit: row.dailyAppointmentLimit, isEnabled: row.isEnabled !== false }
  editBusinessDialogVisible.value = true
}

async function handleBusinessStatusChange(row) {
  try {
    await regionBusinessApi.updateStatus(currentRegion.value.id, row.id, row.isEnabled)
    ElMessage.success(row.isEnabled ? '已启用' : '已禁用')
  } catch (e) {
    row.isEnabled = !row.isEnabled // 回滚
    ElMessage.error(e.message || '操作失败')
  }
}

async function saveBusiness() {
  savingBusiness.value = true
  try {
    // 更新业务类型的每日限额（全局）
    await request.put(`/admin/business-types/${editingBusiness.value.id}`, {
      ...editingBusiness.value,
      dailyAppointmentLimit: editingBusinessForm.value.dailyAppointmentLimit
    })
    // 更新区域级别启用状态
    await regionBusinessApi.updateStatus(currentRegion.value.id, editingBusiness.value.id, editingBusinessForm.value.isEnabled)
    ElMessage.success('修改成功')
    editBusinessDialogVisible.value = false
    await loadRegionBusiness(currentRegion.value.id)
  } catch (e) {
    ElMessage.error(e.message || '修改失败')
  } finally {
    savingBusiness.value = false
  }
}

async function unlinkBusiness() {
  try {
    await ElMessageBox.confirm(`确定取消关联「${editingBusiness.value.name}」？`, '确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    await regionBusinessApi.unlink(currentRegion.value.id, editingBusiness.value.id)
    ElMessage.success('已取消关联')
    editBusinessDialogVisible.value = false
    await loadRegionBusiness(currentRegion.value.id)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '操作失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.region-panel { max-width: 1200px; }

/* 搜索栏 */
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
}
.filter-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 表格 */
:deep(.el-table) {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}

.drag-handle {
  font-size: 16px;
  color: var(--text-muted);
  cursor: move;
  user-select: none;
}
.drag-handle:hover {
  color: var(--accent);
}

/* 弹窗区块标题 */
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  padding: 12px;
  background: var(--bg-raised);
  border-radius: 4px;
  margin-top: 8px;
}

/* 关联业务 */
.business-config {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}
.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--bg-raised);
  border-radius: 4px;
  font-weight: 600;
  color: var(--text-primary);
}

/* 选择业务类型弹窗 */
.select-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(0,229,255,0.1);
  border: 1px solid var(--accent);
  border-radius: 4px;
  margin-bottom: 16px;
  color: var(--accent);
  font-size: 14px;
}
.business-name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.selected-count {
  margin-top: 12px;
  padding: 8px 12px;
  background: rgba(0,229,255,0.1);
  border-left: 3px solid var(--accent);
  color: var(--text-primary);
  font-size: 14px;
}
.selected-count strong {
  color: var(--accent);
  font-size: 16px;
}
:deep(.disabled-row) {
  background-color: var(--bg-void) !important;
  opacity: 0.7;
}
:deep(.business-select-dialog .el-table) {
  border-radius: 4px;
  overflow: hidden;
}

/* 公告管理区块 */
.announcement-config {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

/* 区划代码输入框错误高亮 */
:deep(.code-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset !important;
}
:deep(.code-error .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset !important;
}

/* 区划代码重复轻提示框 */
.code-toast {
  margin-bottom: 16px;
  cursor: pointer;
}
.code-toast-inner {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 40px 14px 16px;
  background: linear-gradient(135deg, #fef0f0 0%, #fff5f5 100%);
  border: 1px solid #fbc4c4;
  border-radius: 8px;
  overflow: hidden;
}
.code-toast-icon {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  color: #f56c6c;
  margin-top: 1px;
}
.code-toast-body {
  flex: 1;
  min-width: 0;
}
.code-toast-title {
  font-size: 14px;
  font-weight: 600;
  color: #f56c6c;
  margin-bottom: 4px;
}
.code-toast-msg {
  font-size: 13px;
  color: #666;
  line-height: 1.5;
}
.code-toast-region {
  font-weight: 600;
  color: #f56c6c;
}
.code-toast-code {
  display: inline-block;
  background: rgba(245,108,108,0.12);
  color: #f56c6c;
  padding: 0 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-weight: 600;
  font-size: 13px;
}
.code-toast-close {
  position: absolute;
  top: 8px;
  right: 10px;
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  color: #c0c4cc;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
  line-height: 1;
}
.code-toast-close:hover {
  color: #f56c6c;
  background: rgba(245,108,108,0.1);
}
.code-toast-timer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: rgba(245,108,108,0.15);
}
.code-toast-timer-bar {
  height: 100%;
  background: linear-gradient(90deg, #f56c6c, #fab6b6);
  animation: timer-shrink linear forwards;
}
@keyframes timer-shrink {
  from { width: 100%; }
  to { width: 0%; }
}
.dialog-footer-row {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
