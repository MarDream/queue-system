<template>
  <div class="menu-panel">
    <!-- 搜索栏 -->
    <div class="filter-bar">
      <span class="filter-label">菜单名称</span>
      <el-input v-model="filterKeyword" placeholder="请输入菜单名称或路径" clearable style="width:220px" @keyup.enter="handleSearch" />
      <el-button type="primary" @click="handleSearch">
        <el-icon class="btn-i"><Search /></el-icon> 搜索
      </el-button>
      <el-button type="primary" v-permission="'btn:add'" @click="openCreate()">
        <el-icon class="btn-i"><Plus /></el-icon> 新增菜单
      </el-button>
    </div>

    <!-- 树形表格 -->
    <el-table
      ref="tableRef"
      :data="treeData"
      v-loading="tableLoading"
      row-key="id"
      :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      :default-expand-all="true"
      :row-class-name="rowClassName"
      empty-text="暂无数据！"
      style="width:100%"
    >
      <el-table-column prop="name" label="菜单名称" min-width="180" />
      <el-table-column prop="path" label="路径" min-width="180">
        <template #default="{ row }">
          <span class="path-text">{{ row.path || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="icon" label="图标" width="80" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.icon && !isEmoji(row.icon)" :size="18">
            <component :is="row.icon" />
          </el-icon>
          <span v-else-if="row.icon" class="icon-preview">{{ row.icon }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
      <el-table-column label="操作" width="200" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" v-permission="'btn:edit'" @click="openEdit(row)">
            <el-icon><Edit /></el-icon> 修改
          </el-button>
          <el-button size="small" link type="danger" v-permission="'btn:delete'" @click="handleDelete(row)">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑/新增弹窗 -->
    <el-dialog v-model="dialogVisible" width="550px" draggable :show-close="false">
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
      <el-form :model="form" label-width="80px">
        <el-form-item label="菜单名称" required>
          <el-input v-model="form.name" placeholder="如：首页" />
        </el-form-item>
        <el-form-item label="路径">
          <el-input v-model="form.path" placeholder="如：/home 或 /admin?tab=region" />
        </el-form-item>
        <el-form-item label="图标">
          <IconPicker v-model="form.icon" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width:100%">
            <el-option label="菜单（独立页面）" value="menu" />
            <el-option label="页面（管理后台子页）" value="page" />
            <el-option label="分组（容器）" value="group" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="form.parentId" clearable placeholder="无（根菜单）" style="width:100%">
            <el-option
              v-for="g in groupOptions"
              :key="g.id"
              :label="g.name"
              :value="g.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search } from '@element-plus/icons-vue'
import { menuApi } from '../../api/admin'
import IconPicker from '../../components/IconPicker.vue'
import Sortable from 'sortablejs'

// 表格数据
const treeData = ref([])
const allFlatData = ref([])
const tableLoading = ref(false)
const tableRef = ref(null)

// 搜索
const filterKeyword = ref('')

// 类型标签
function typeTagType(type) {
  const map = { menu: 'primary', page: 'success', group: 'warning' }
  return map[type] || 'info'
}

function typeLabel(type) {
  const map = { menu: '菜单', page: '页面', group: '分组' }
  return map[type] || type || '未知'
}

function isEmoji(str) {
  if (!str) return false
  // Emoji 通常不在 ASCII 范围
  return /[^\u0000-\u007F]/.test(str)
}

// 分组选项（用于下拉选择所属分组）
const groupOptions = computed(() => {
  return allFlatData.value.filter(m => m.type === 'group')
})

// 构建树形结构
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

// 获取数据
async function fetchData() {
  tableLoading.value = true
  try {
    const result = await menuApi.list()
    allFlatData.value = result || []
    applyFilter()
  } catch {
    allFlatData.value = []
    treeData.value = []
  } finally {
    tableLoading.value = false
  }
}

// 过滤
function applyFilter() {
  let data = allFlatData.value
  if (filterKeyword.value.trim()) {
    const kw = filterKeyword.value.trim().toLowerCase()
    const matchedIds = new Set()
    data.forEach(r => {
      if ((r.name || '').toLowerCase().includes(kw) || (r.path || '').toLowerCase().includes(kw)) {
        matchedIds.add(r.id)
      }
    })
    const filtered = data.filter(r => isMatchOrAncestor(r, matchedIds, data))
    treeData.value = buildTree(filtered)
  } else {
    treeData.value = buildTree(data)
  }
  nextTick(() => initDragSort())
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

// 拖拽排序
function rowClassName({ row }) {
  const pid = row.parentId ?? 'root'
  return `menu-row-pid-${pid}`
}

function initDragSort() {
  nextTick(() => {
    const tbody = tableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!tbody) return

    if (tbody._sortable) {
      tbody._sortable.destroy()
    }

    tbody._sortable = Sortable.create(tbody, {
      animation: 150,
      move: (evt) => {
        const fromPid = extractPid(evt.dragged)
        const toPid = extractPid(evt.related)
        return fromPid === toPid
      },
      onEnd: async (evt) => {
        const fromPid = extractPid(evt.item)
        const parentRows = tbody.querySelectorAll(`.menu-row-pid-${fromPid}`)
        const updates = []
        parentRows.forEach((tr, idx) => {
          const id = tr.getAttribute('data-row-key')
          if (id) updates.push({ id: Number(id), sortOrder: idx })
        })
        if (updates.length > 0) {
          try {
            await menuApi.updateSort(updates.map(u => u.id))
            ElMessage.success('排序已更新')
            emit('reload')
          } catch (e) {
            ElMessage.error(e.message || '排序更新失败')
          }
        }
        await fetchData()
      }
    })
  })
}

function extractPid(el) {
  while (el && el.tagName !== 'TR') {
    el = el.parentElement
  }
  if (!el) return null
  for (const cls of el.classList) {
    if (cls.startsWith('menu-row-pid-')) {
      return cls.replace('menu-row-pid-', '')
    }
  }
  return 'root'
}

// 编辑/新增
const dialogVisible = ref(false)
const dialogTitle = ref('')
const saving = ref(false)
const isEdit = ref(false)
const emit = defineEmits(['reload'])
const form = ref({ name: '', path: '', icon: '', type: 'menu', parentId: null, sortOrder: 0 })
let editingId = null

function openCreate() {
  isEdit.value = false
  editingId = null
  let defaultSort = 0
  try {
    const items = allFlatData.value
    if (items.length > 0) {
      defaultSort = Math.max(...items.map(s => s.sortOrder || 0)) + 10
    }
  } catch {}
  form.value = { name: '', path: '', icon: '', type: 'menu', parentId: null, sortOrder: defaultSort }
  dialogTitle.value = '新增菜单'
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  editingId = row.id
  form.value = {
    name: row.name,
    path: row.path || '',
    icon: row.icon || '',
    type: row.type || 'menu',
    parentId: row.parentId ?? null,
    sortOrder: row.sortOrder || 0
  }
  dialogTitle.value = '编辑菜单'
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name) { ElMessage.warning('请输入菜单名称'); return }
  saving.value = true
  try {
    const data = { ...form.value }
    if (isEdit.value) {
      await menuApi.update(editingId, data)
      ElMessage.success('更新成功')
    } else {
      await menuApi.create(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await fetchData()
    emit('reload')
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？${row.type === 'group' ? '（分组下不能有子菜单）' : ''}`, '确认')
    await menuApi.remove(row.id)
    ElMessage.success('删除成功')
    await fetchData()
    emit('reload')
  } catch {}
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.menu-panel { max-width: 1200px; }

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

:deep(.el-table) {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}

.path-text {
  font-family: monospace;
  font-size: 12px;
  color: var(--text-secondary);
}
.icon-preview {
  font-size: 18px;
}
.text-muted {
  color: var(--text-muted);
}
</style>
