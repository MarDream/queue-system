<template>
  <div class="statistics-panel">
    <!-- 筛选条件 -->
    <div class="filter-bar">
      <div class="filter-row">
        <el-select v-model="filter.regionId" placeholder="选择区域" clearable style="width:180px">
          <el-option v-for="r in regionOptions" :key="r.id" :label="r.regionName" :value="r.id" />
        </el-select>
        <el-select v-model="filter.businessTypeId" placeholder="选择业务类型" clearable style="width:180px">
          <el-option v-for="b in businessOptions" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
        <el-date-picker v-model="filter.dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
          end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width:260px" />
        <el-button type="primary" @click="loadData" :loading="loading">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </div>
      <div class="filter-row" style="margin-top:8px">
        <el-button type="success" @click="handleExport">导出 Excel</el-button>
        <el-popover placement="bottom-start" :width="280" trigger="click">
          <template #reference>
            <el-button>选择显示列</el-button>
          </template>
          <div class="column-select">
            <div class="column-select-header">
              <span>选择显示列</span>
              <el-button link type="primary" @click="selectAll">全选</el-button>
            </div>
            <el-checkbox v-for="col in allColumns" :key="col.key" v-model="col.visible" :label="col.label" />
          </div>
        </el-popover>
      </div>
    </div>

    <!-- 统计表格 -->
    <el-table :data="tableData" v-loading="loading" stripe empty-text="暂无数据！" class="statistics-table">
      <el-table-column v-if="isColumnVisible('regionName')" prop="regionName" label="区域名称" min-width="120" />
      <el-table-column v-if="isColumnVisible('businessName')" prop="businessName" label="业务名称" min-width="100" />
      <el-table-column v-if="isColumnVisible('counterName')" prop="counterName" label="办理窗口" min-width="100" />
      <el-table-column v-if="isColumnVisible('operatorName')" prop="operatorName" label="窗口操作人员" min-width="120" />
      <el-table-column v-if="isColumnVisible('ticketStatus')" prop="ticketStatus" label="业务处理状态" min-width="100">
        <template #default="{ row }">
          <span :class="statusClass(row.ticketStatus)">{{ statusText(row.ticketStatus) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('customerName')" prop="customerName" label="预约人姓名" min-width="100" />
      <el-table-column v-if="isColumnVisible('ticketNo')" prop="ticketNo" label="票号" min-width="80" />
      <el-table-column v-if="isColumnVisible('createdAt')" prop="createdAt" label="预约时间" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('calledAt')" prop="calledAt" label="叫号时间" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.calledAt) }}</template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('servedAt')" prop="servedAt" label="办理开始时间" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.servedAt) }}</template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('completedAt')" prop="completedAt" label="办理结束时间" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
      </el-table-column>
      <el-table-column v-if="isColumnVisible('durationSeconds')" prop="durationSeconds" label="办理时长(秒)" min-width="110" />
    </el-table>

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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { statisticsApi } from '../../api/admin'
import { regionApi, businessTypeApi } from '../../api/admin'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const regionOptions = ref([])
const businessOptions = ref([])

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

async function loadRegions() {
  try {
    const cities = await regionApi.getCities()
    regionOptions.value = cities || []
    for (const city of regionOptions.value) {
      const towns = await regionApi.getTowns(city.id)
      if (towns?.length) regionOptions.value.push(...towns)
    }
  } catch {}
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
      pageSize: pagination.pageSize
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
  return val.replace('T', ' ').substring(0, 19)
}

function statusText(status) {
  const map = { waiting: '等待中', called: '已叫号', serving: '服务中', completed: '已完成', skipped: '已跳过', cancelled: '已取消' }
  return map[status] || status
}

function statusClass(status) {
  const map = {
    waiting: 'text-muted',
    called: 'text-primary',
    serving: 'text-warning',
    completed: 'text-success',
    skipped: 'text-secondary',
    cancelled: 'text-danger'
  }
  return map[status] || ''
}

onMounted(() => {
  loadColumnPrefs()
  loadRegions()
  loadBusinessTypes()
  loadData()
})
</script>

<style scoped>
.statistics-panel {
  padding: 16px;
}
.filter-bar {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}
.filter-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.column-select {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.column-select-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.statistics-table {
  margin-top: 12px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.text-muted { color: #909399; }
.text-primary { color: #409eff; }
.text-warning { color: #e6a23c; }
.text-success { color: #67c23a; }
.text-secondary { color: #909399; }
.text-danger { color: #f56c6c; }
</style>
