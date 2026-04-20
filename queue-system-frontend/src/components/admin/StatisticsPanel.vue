<template>
  <div class="statistics-panel">
    <!-- 筛选区 -->
    <div class="filter-bar">
      <div class="filter-row">
        <el-tree-select
          v-model="filter.regionId"
          :data="regionTree"
          :props="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="全部区域"
          clearable
          check-strictly
          :render-after-expand="false"
          filterable
          :filter-method="filterRegionByName"
          style="width:200px"
          class="region-select"
          popper-class="region-select-popper"
        />
        <el-select v-model="filter.businessTypeId" placeholder="选择业务类型" clearable style="width:180px">
          <el-option v-for="b in businessOptions" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
        <el-date-picker v-model="filter.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
          end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width:260px" />
        <el-button type="primary" @click="loadData" :loading="loading">
          <el-icon><Search /></el-icon> 查询
        </el-button>
        <el-button @click="resetFilter">
          <el-icon><RefreshRight /></el-icon> 重置
        </el-button>
      </div>
    </div>

    <!-- 工具栏：导出 + 列配置 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <span class="data-hint">
          <el-icon><Tickets /></el-icon>
          共 {{ pagination.total }} 条记录
        </span>
      </div>
      <div class="toolbar-right">
        <el-button type="success" @click="handleExport">
          <el-icon><Download /></el-icon> 导出
        </el-button>
        <el-button @click="columnDrawerVisible = true">
          <el-icon><Setting /></el-icon> 列配置
        </el-button>
      </div>
    </div>

    <!-- 表格区域（横向滚动） -->
    <div class="table-wrapper">
      <el-table :data="tableData" v-loading="loading" stripe empty-text="暂无数据！" class="statistics-table" @sort-change="handleSortChange">
        <el-table-column v-if="isColumnVisible('regionName')" prop="regionName" label="区域名称" min-width="120" sortable="custom" />
        <el-table-column v-if="isColumnVisible('businessName')" prop="businessName" label="业务名称" min-width="120" sortable="custom" />
        <el-table-column v-if="isColumnVisible('counterName')" prop="counterName" label="办理窗口" min-width="120" sortable="custom" />
        <el-table-column v-if="isColumnVisible('operatorName')" prop="operatorName" label="窗口操作人员" min-width="140" sortable="custom" />
        <el-table-column v-if="isColumnVisible('ticketStatus')" prop="ticketStatus" label="业务处理状态" min-width="120" sortable="custom">
          <template #default="{ row }">
            <span :class="statusClass(row.ticketStatus)">{{ statusText(row.ticketStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('customerName')" prop="customerName" label="预约人姓名" min-width="120" sortable="custom" />
        <el-table-column v-if="isColumnVisible('ticketNo')" prop="ticketNo" label="票号" min-width="100" sortable="custom">
          <template #default="{ row }">{{ getTicketNo(row.ticketNo) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('createdAt')" prop="createdAt" label="预约时间" min-width="180" sortable="custom">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('calledAt')" prop="calledAt" label="叫号时间" min-width="180" sortable="custom">
          <template #default="{ row }">{{ formatDateTime(row.calledAt) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('servedAt')" prop="servedAt" label="办理开始时间" min-width="180" sortable="custom">
          <template #default="{ row }">{{ formatDateTime(row.servedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('completedAt')" prop="completedAt" label="办理结束时间" min-width="180" sortable="custom">
          <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column v-if="isColumnVisible('durationSeconds')" prop="durationSeconds" label="办理时长(秒)" min-width="120" sortable="custom" />
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>

    <!-- 列配置抽屉 -->
    <el-drawer v-model="columnDrawerVisible" title="列配置" direction="rtl" size="280px">
      <div class="column-config">
        <div class="column-config-header">
          <span>选择显示列</span>
          <el-button link type="primary" @click="selectAll">全选</el-button>
        </div>
        <el-scrollbar height="calc(100vh - 120px)">
          <div class="column-config-list">
            <div v-for="col in allColumns" :key="col.key" class="column-config-item">
              <span class="column-label">{{ col.label }}</span>
              <el-switch v-model="col.visible" size="small" />
            </div>
          </div>
        </el-scrollbar>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { statisticsApi } from '../../api/admin'
import { businessTypeApi } from '../../api/admin'
import request from '../../api/index'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'
import { Search, RefreshRight, Download, Setting, Tickets } from '@element-plus/icons-vue'

const userStore = useUserStore()

const loading = ref(false)
const tableData = ref([])
const regions = ref([])
const businessOptions = ref([])
const columnDrawerVisible = ref(false)

const filter = reactive({
  regionId: null,
  businessTypeId: null,
  dateRange: null,
  startDate: null,
  endDate: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 排序配置
const sortConfig = reactive({
  prop: '',
  order: ''
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

async function fetchRegions() {
  try {
    const params = {}
    if (!userStore.isSuperAdmin) {
      params.userId = Number(userStore.userId) || undefined
    }
    regions.value = await request.get('/regions', { params })
  } catch (e) {
    console.error('加载区域失败:', e)
    regions.value = []
  }
}

// 列配置
const STORAGE_KEY = 'statistics_columns_v1'
const allColumns = ref([
  { key: 'regionName', label: '区域名称', visible: true },
  { key: 'businessName', label: '业务名称', visible: true },
  { key: 'counterName', label: '办理窗口', visible: true },
  { key: 'operatorName', label: '窗口操作人员', visible: true },
  { key: 'ticketStatus', label: '业务处理状态', visible: true },
  { key: 'customerName', label: '预约人姓名', visible: true },
  { key: 'ticketNo', label: '票号', visible: true },
  { key: 'createdAt', label: '预约时间', visible: true },
  { key: 'calledAt', label: '叫号时间', visible: true },
  { key: 'servedAt', label: '办理开始时间', visible: true },
  { key: 'completedAt', label: '办理结束时间', visible: true },
  { key: 'durationSeconds', label: '办理时长(秒)', visible: true }
])

// 从 LocalStorage 恢复用户偏好
function loadColumnPrefs() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const prefs = JSON.parse(saved)
      allColumns.value.forEach(col => {
        if (prefs[col.key] !== undefined) {
          col.visible = prefs[col.key]
        }
      })
    }
  } catch {}
}

// 保存用户偏好到 LocalStorage
function saveColumnPrefs() {
  const prefs = {}
  allColumns.value.forEach(col => { prefs[col.key] = col.visible })
  localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
}

watch(allColumns, saveColumnPrefs, { deep: true })

function isColumnVisible(key) {
  return allColumns.value.find(c => c.key === key)?.visible ?? true
}

function selectAll() {
  allColumns.value.forEach(col => { col.visible = true })
}

async function loadBusinessTypes() {
  try {
    const list = await businessTypeApi.list()
    businessOptions.value = list || []
  } catch {}
}

async function loadData() {
  loading.value = true
  try {
    filter.startDate = filter.dateRange?.[0] || null
    filter.endDate = filter.dateRange?.[1] || null
    const res = await statisticsApi.list({
      regionId: filter.regionId,
      businessTypeId: filter.businessTypeId,
      startDate: filter.startDate,
      endDate: filter.endDate,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      sortProp: sortConfig.prop,
      sortOrder: sortConfig.order === 'ascending' ? 'asc' : sortConfig.order === 'descending' ? 'desc' : null
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch (e) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filter.regionId = null
  filter.businessTypeId = null
  filter.dateRange = null
  pagination.pageNum = 1
  loadData()
}

function handleExport() {
  filter.startDate = filter.dateRange?.[0] || null
  filter.endDate = filter.dateRange?.[1] || null
  statisticsApi.export({
    regionId: filter.regionId,
    businessTypeId: filter.businessTypeId,
    startDate: filter.startDate,
    endDate: filter.endDate
  })
}

function formatDateTime(val) {
  if (!val) return '-'

  // 处理数组格式 [year, month, day, hour, minute, second]
  if (Array.isArray(val)) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = val
    return `${String(y).padStart(4, '0')}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }

  // 处理 LocalDateTime 对象
  if (typeof val === 'object' && val !== null && 'year' in val) {
    const y = String(val.year).padStart(4, '0')
    const mo = String(val.monthValue).padStart(2, '0')
    const d = String(val.dayOfMonth).padStart(2, '0')
    const h = String(val.hour).padStart(2, '0')
    const mi = String(val.minute).padStart(2, '0')
    const s = String(val.second).padStart(2, '0')
    return `${y}-${mo}-${d} ${h}:${mi}:${s}`
  }

  // ISO 字符串格式化
  const str = String(val)
  const datePart = str.substring(0, 10)
  const timePart = str.substring(11, 19)
  return `${datePart} ${timePart}`
}

// 票号去区域前缀，如 "440305A004" -> "A004"
function getTicketNo(ticketNo) {
  if (!ticketNo) return '-'
  // 区域编码通常是6位数字开头
  const match = ticketNo.match(/^\d{6}(.+)$/)
  if (match) return match[1]
  // 也处理 "GZ-A001" 格式
  const parts = ticketNo.split('-')
  return parts.length > 1 ? parts.slice(1).join('-') : ticketNo
}

function statusText(status) {
  const map = { waiting: '等待中', called: '已叫号', serving: '服务中', completed: '已完成', skipped: '已过号', cancelled: '已取消' }
  return map[status] || status
}

function statusClass(status) {
  const map = {
    waiting: 'text-muted',
    called: 'text-primary',
    serving: 'text-warning',
    completed: 'text-success',
    skipped: 'text-overdue',
    cancelled: 'text-danger'
  }
  return map[status] || ''
}

function handleSortChange({ prop, order }) {
  sortConfig.prop = prop || ''
  sortConfig.order = order || ''
  pagination.pageNum = 1
  loadData()
}

onMounted(() => {
  loadColumnPrefs()
  fetchRegions()
  loadBusinessTypes()
  loadData()
})
</script>

<style scoped>
.statistics-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
  height: 100%;
}
.filter-bar {
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  margin-bottom: 12px;
}
.filter-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 0 4px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.data-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.table-wrapper {
  flex: 1;
  overflow-x: auto;
  min-height: 0;
}
.statistics-table {
  min-width: 100%;
}
.statistics-table :deep(.el-table__header th) {
  background: var(--bg-body) !important;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}
.statistics-table :deep(.el-table__body td) {
  white-space: nowrap;
}
.statistics-table :deep(.el-table__body tr:hover > td) {
  background: var(--primary-light) !important;
}
.statistics-table :deep(.cell) {
  white-space: nowrap;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
/* 列配置抽屉 */
.column-config {
  padding: 0 16px;
}
.column-config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 16px;
  font-weight: 600;
}
.column-config-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.column-config-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--bg-body);
  border-radius: var(--radius-sm);
}
.column-label {
  font-size: 14px;
  color: var(--text-primary);
}
.text-muted { color: #909399; }
.text-primary { color: #409eff; }
.text-warning { color: #e6a23c; }
.text-success { color: #67c23a; }
.text-secondary { color: #909399; }
.text-danger { color: #f56c6c; }
.text-overdue { color: #909399; font-weight: 500; }
</style>
