<template>
  <div class="dashboard">
    <div class="summary-grid">
      <div class="summary-card clickable" @click="openDetail('total')">
        <div class="sc-value mono">{{ data.totalTickets ?? 0 }}</div>
        <div class="sc-label">今日取号</div>
      </div>
      <div class="summary-card clickable" @click="openDetail('completed')">
        <div class="sc-value mono">{{ data.completedCount ?? 0 }}</div>
        <div class="sc-label">已办结</div>
      </div>
      <div class="summary-card clickable" @click="openDetail('waiting')">
        <div class="sc-value mono">{{ data.waitingCount ?? 0 }}</div>
        <div class="sc-label">当前等待</div>
      </div>
      <div class="summary-card">
        <div class="sc-value mono">{{ data.avgWaitMinutes != null ? data.avgWaitMinutes.toFixed(1) : '—' }}</div>
        <div class="sc-label">平均等待(分钟)</div>
      </div>
      <div class="summary-card">
        <div class="sc-value mono">{{ data.avgServeMinutes != null ? data.avgServeMinutes.toFixed(1) : '—' }}</div>
        <div class="sc-label">平均办理(分钟)</div>
      </div>
    </div>

    <div class="section-title-row">
      <div class="section-title">各窗口办理量</div>
      <el-button
        v-if="sortedCounterStats.length > counterTopLimit"
        link
        type="primary"
        @click="openCounterDialog"
      >
        查看全部（{{ sortedCounterStats.length }}）
      </el-button>
    </div>
    <div class="counter-chart" v-if="topCounterStats.length">
      <div v-for="(item, idx) in topCounterStats" :key="item.counterId || item.counterName" class="counter-bar-row">
        <span class="counter-rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span>
        <span class="counter-name" :title="item.counterName">{{ item.counterName }}</span>
        <div class="counter-bar-track">
          <div class="counter-bar-fill" :style="{ width: barWidth(item.completedCount, maxCounterCount) + '%' }"></div>
        </div>
        <span class="counter-count">{{ item.completedCount ?? 0 }}</span>
      </div>
    </div>
    <div v-else class="empty-tip">暂无数据！</div>

    <div class="section-title" style="margin-top:32px">各业务占比</div>
    <div class="biz-chart" v-if="sortedBizStats.length">
      <div
        v-for="(item, idx) in sortedBizStats"
        :key="item.businessTypeId"
        class="biz-bar-row"
        @click="openBizDetail(item)"
      >
        <div class="biz-bar-label">
          <span class="biz-bar-dot" :style="{ background: bizColor(idx).fg }"></span>
          <span class="biz-bar-name">{{ item.businessType }}</span>
          <span class="biz-bar-count">{{ item.count }} 票</span>
        </div>
        <div class="biz-bar-track">
          <div
            class="biz-bar-fill"
            :style="{
              width: Math.max(item.percentage || 0, 1.5) + '%',
              background: bizColor(idx).gradient
            }"
          ></div>
          <span class="biz-bar-pct" :class="{ 'pct-inside': (item.percentage || 0) > 28 }">
            {{ item.percentage != null ? item.percentage.toFixed(1) + '%' : '—' }}
          </span>
        </div>
      </div>
    </div>
    <div v-else class="empty-tip">暂无数据！</div>

    <!-- 业务类型明细弹窗 -->
    <el-dialog
      v-model="bizDialogVisible"
      width="700px"
      class="biz-detail-dialog"
      :show-close="false"
      draggable
      resizable
      align-center
    >
      <template #header>
        <div class="biz-dlg-header">
          <div class="biz-dlg-left">
            <span class="biz-dlg-title">{{ bizDialogTitle }}</span>
            <span class="biz-dlg-count">共 <strong>{{ bizDetailList.length }}</strong> 条</span>
          </div>
          <el-button circle size="small" class="icon-close-btn" @click="bizDialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <el-table :data="bizDetailList" stripe class="biz-detail-table" empty-text="暂无数据！">
        <el-table-column prop="regionName" label="区域名称" />
        <el-table-column prop="counterNumber" label="窗口号" width="80" />
        <el-table-column prop="counterName" label="窗口名称" />
        <el-table-column prop="operatorName" label="操作人员" />
        <el-table-column prop="ticketCount" label="取号数" width="80" align="center" />
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="counterDialogVisible"
      width="760px"
      class="counter-detail-dialog"
      :show-close="false"
      draggable
      resizable
      align-center
    >
      <template #header>
        <div class="counter-dlg-header">
          <div class="counter-dlg-left">
            <span class="counter-dlg-title">各窗口办理量</span>
            <span class="counter-dlg-count">共 <strong>{{ filteredCounterStats.length }}</strong> 条</span>
          </div>
          <div class="counter-dlg-right">
            <el-input
              v-model="counterKeyword"
              placeholder="搜索窗口"
              clearable
              style="width:220px"
            />
            <el-button circle size="small" class="icon-close-btn" @click="counterDialogVisible = false" title="关闭">
              <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M15 9l-6 6M9 9l6 6"/>
              </svg>
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="pagedCounterStats" stripe class="counter-detail-table" empty-text="暂无数据！" table-layout="auto">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="counterName" label="窗口" min-width="200" show-overflow-tooltip />
        <el-table-column label="已办结" width="180" align="center">
          <template #default="{ row }">
            <div class="counter-spark-cell">
              <div class="counter-spark-track">
                <div
                  class="counter-spark-fill"
                  :style="{ width: barWidth(row.completedCount, dialogMaxCount) + '%' }"
                ></div>
              </div>
              <span class="counter-spark-num">{{ row.completedCount ?? 0 }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="counter-dlg-pagination">
        <el-pagination
          v-model:current-page="counterPage"
          v-model:page-size="counterPageSize"
          :total="filteredCounterStats.length"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          small
        />
      </div>
    </el-dialog>

<el-dialog
      v-model="dialogVisible"
      :fullscreen="dialogFullscreen"
      :width="dialogFullscreen ? '100%' : '1160px'"
      class="ticket-detail-dialog"
      :show-close="false"
      draggable
      resizable
      align-center
    >
      <!-- 头部 -->
      <template #header>
        <div class="dlg-header">
          <div class="dlg-title-row">
            <div class="dlg-title-group">
              <span class="dlg-title">{{ dialogTitle }}</span>
              <span class="dlg-count">
                <span class="count-num">{{ detailList.length }}</span> 条记录
                <span v-if="filterActive" class="filter-active-tag">已筛选</span>
              </span>
            </div>
            <div class="dlg-actions">
              <el-button
                circle
                size="small"
                class="icon-btn fullscreen-btn"
                @click="dialogFullscreen = !dialogFullscreen"
                :title="dialogFullscreen ? '还原' : '全屏'"
              >
                <svg class="icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path v-if="dialogFullscreen" d="M5 9h4V5M15 19h4v-4M9 19v4h4M15 5v4h4"/>
                  <path v-else d="M8 3v4H4M16 21v-4h4M8 21v-4H4M16 3v4h4"/>
                </svg>
              </el-button>
              <el-button
                circle
                size="small"
                class="icon-btn close-btn"
                @click="dialogVisible = false"
                title="关闭"
              >
                <svg class="icon-svg close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M15 9l-6 6M9 9l6 6"/>
                </svg>
              </el-button>
            </div>
          </div>
          <!-- 筛选栏 -->
          <div class="filter-bar">
            <el-date-picker
              v-model="filter.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              clearable
              style="width:220px"
            />
            <el-input v-model="filter.phone" placeholder="手机号" clearable style="width:130px" />
            <el-input v-model="filter.name" placeholder="姓名" clearable style="width:110px" />
            <el-input v-model="filter.ticketNo" placeholder="票号" clearable style="width:110px" />
            <el-button type="primary" class="btn-search" @click="doSearch">
              <el-icon class="btn-i"><Search /></el-icon> 查询
            </el-button>
            <el-button class="btn-reset" @click="resetFilter">
              <el-icon class="btn-i"><Refresh /></el-icon> 重置
            </el-button>
          </div>
        </div>
      </template>

      <!-- 表格 -->
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>票号</th>
              <th>业务类型</th>
              <th>区域</th>
              <th>姓名</th>
              <th>手机号</th>
              <th>状态</th>
              <th>窗口</th>
              <th>取号时间</th>
              <th>叫号时间</th>
              <th>完成时间</th>
            </tr>
          </thead>
          <tbody v-loading="dialogLoading">
            <tr v-if="detailList.length === 0 && !dialogLoading">
              <td colspan="10" class="empty-cell">
                <span class="empty-icon">📋</span>
                <span>暂无数据！</span>
              </td>
            </tr>
            <tr v-for="row in detailList" :key="row.id">
              <td class="cell-ticket-no">{{ getDisplayTicketNo(row.ticketNo) }}</td>
              <td>{{ row.businessType }}</td>
              <td>{{ row.regionName || '—' }}</td>
              <td>{{ row.name || '—' }}</td>
              <td>{{ row.phone || '—' }}</td>
              <td>
                <span class="status-tag" :class="'status-' + row.status">
                  <span class="status-dot"></span>
                  {{ statusTextCn(row.status) }}
                </span>
              </td>
              <td>{{ row.counterName || '—' }}</td>
              <td>{{ formatTime(row.createdAt) }}</td>
              <td>{{ formatTime(row.calledAt) }}</td>
              <td>{{ formatTime(row.completedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getDashboard, getTicketList, getBusinessTypeDetail } from '../../api/screen'
import { useUserStore } from '../../stores/user'
import { getDisplayTicketNo } from '../../utils/ticketUtils'

const userStore = useUserStore()

const data = ref({})
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogStatus = ref('')
const dialogFullscreen = ref(false)
const dialogLoading = ref(false)
const detailList = ref([])
const filter = ref({
  dateRange: [],
  phone: '',
  name: '',
  ticketNo: ''
})
const bizDialogVisible = ref(false)
const bizDialogTitle = ref('')
const bizDetailList = ref([])
const bizLoading = ref(false)

const counterTopLimit = 10
const counterDialogVisible = ref(false)
const counterKeyword = ref('')
const counterPage = ref(1)
const counterPageSize = ref(10)

const filterActive = computed(() => {
  return filter.value.phone || filter.value.name || filter.value.ticketNo ||
    (filter.value.dateRange && filter.value.dateRange.length === 2)
})

const sortedCounterStats = computed(() => {
  if (!data.value.counterStats) return []
  return [...data.value.counterStats]
    .filter(s => (s.completedCount ?? 0) > 0 || s.counterName)
    .sort((a, b) => (b.completedCount ?? 0) - (a.completedCount ?? 0))
})

const topCounterStats = computed(() => sortedCounterStats.value.slice(0, counterTopLimit))

const filteredCounterStats = computed(() => {
  const kw = String(counterKeyword.value || '').trim().toLowerCase()
  if (!kw) return sortedCounterStats.value
  return sortedCounterStats.value.filter(r => String(r.counterName || '').toLowerCase().includes(kw))
})

const pagedCounterStats = computed(() => {
  const start = (counterPage.value - 1) * counterPageSize.value
  const end = start + counterPageSize.value
  return filteredCounterStats.value.slice(start, end)
})

const sortedBizStats = computed(() => {
  if (!data.value.businessTypeStats) return []
  return [...data.value.businessTypeStats]
    .filter(s => s.count > 0)
    .sort((a, b) => (b.percentage || 0) - (a.percentage || 0))
})

const maxCounterCount = computed(() => {
  if (!topCounterStats.value.length) return 1
  return Math.max(...topCounterStats.value.map(s => s.completedCount ?? 0), 1)
})

const dialogMaxCount = computed(() => {
  if (!sortedCounterStats.value.length) return 1
  return Math.max(...sortedCounterStats.value.map(s => s.completedCount ?? 0), 1)
})

function barWidth(count, max) {
  const v = count ?? 0
  const m = max ?? 1
  if (v <= 0) return 0
  return Math.max(3, Math.round((v / m) * 100))
}

const BIZ_COLORS = [
  { fg: '#2563eb', gradient: 'linear-gradient(90deg, #bfdbfe, #3b82f6)' },   // Blue
  { fg: '#059669', gradient: 'linear-gradient(90deg, #a7f3d0, #10b981)' },   // Emerald
  { fg: '#7c3aed', gradient: 'linear-gradient(90deg, #ddd6fe, #8b5cf6)' },   // Violet
  { fg: '#d97706', gradient: 'linear-gradient(90deg, #fef3c7, #f59e0b)' },   // Amber
  { fg: '#db2777', gradient: 'linear-gradient(90deg, #fce7f3, #ec4899)' },   // Pink
  { fg: '#0891b2', gradient: 'linear-gradient(90deg, #cffafe, #06b6d4)' },   // Cyan
  { fg: '#ea580c', gradient: 'linear-gradient(90deg, #fed7aa, #f97316)' },   // Orange
  { fg: '#4f46e5', gradient: 'linear-gradient(90deg, #e0e7ff, #6366f1)' },   // Indigo
]

function bizColor(idx) {
  return BIZ_COLORS[idx % BIZ_COLORS.length]
}

onMounted(async () => {
  try { data.value = await getDashboard({ userId: Number(userStore.userId) || undefined }) }
  catch {}
})

function openCounterDialog() {
  counterKeyword.value = ''
  counterPage.value = 1
  counterPageSize.value = 10
  counterDialogVisible.value = true
}

watch(counterKeyword, () => {
  counterPage.value = 1
})

watch([filteredCounterStats, counterPageSize], () => {
  const total = filteredCounterStats.value.length
  const maxPage = Math.max(1, Math.ceil(total / counterPageSize.value))
  if (counterPage.value > maxPage) counterPage.value = maxPage
})

async function openBizDetail(item) {
  bizDialogTitle.value = item.businessType + ' 明细'
  bizDialogVisible.value = true
  bizLoading.value = true
  bizDetailList.value = []
  try {
    bizDetailList.value = await getBusinessTypeDetail(item.businessTypeId, { userId: Number(userStore.userId) || undefined })
  } catch {
    bizDetailList.value = []
  } finally {
    bizLoading.value = false
  }
}

async function openDetail(type) {
  dialogFullscreen.value = false
  dialogVisible.value = true
  dialogLoading.value = true
  detailList.value = []
  resetFilter()
  if (type === 'total') {
    dialogTitle.value = '今日取号详情'
    dialogStatus.value = ''
  } else if (type === 'completed') {
    dialogTitle.value = '已办结详情'
    dialogStatus.value = 'COMPLETED'
  } else if (type === 'waiting') {
    dialogTitle.value = '当前等待详情'
    dialogStatus.value = 'WAITING'
  }
  await doFetch()
  dialogLoading.value = false
}

async function doSearch() {
  dialogLoading.value = true
  await doFetch()
  dialogLoading.value = false
}

async function doFetch() {
  const params = { userId: Number(userStore.userId) || undefined }
  if (dialogStatus.value) params.status = dialogStatus.value
  if (filter.value.dateRange && filter.value.dateRange.length === 2) {
    params.startDate = filter.value.dateRange[0]
    params.endDate = filter.value.dateRange[1]
  }
  if (filter.value.phone) params.phone = filter.value.phone
  if (filter.value.name) params.name = filter.value.name
  if (filter.value.ticketNo) params.ticketNo = filter.value.ticketNo
  try {
    detailList.value = await getTicketList(params)
  } catch { detailList.value = [] }
}

function resetFilter() {
  filter.value = { dateRange: [], phone: '', name: '', ticketNo: '' }
}

function statusTagType(status) {
  const key = status?.toUpperCase?.()
  const map = {
    WAITING: 'warning',
    CALLED: 'primary',
    SERVING: 'success',
    COMPLETED: 'info',
    CANCELLED: 'danger',
    SKIPPED: 'warning'
  }
  return map[key] || 'info'
}

const STATUS_TEXT_CN = {
  WAITING: '等待中',
  CALLED: '已叫号',
  SERVING: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  SKIPPED: '已跳过'
}
function statusTextCn(status) {
  const key = status?.toUpperCase?.()
  return STATUS_TEXT_CN[key] || status || '—'
}

function formatTime(time) {
  if (!time) return '—'
  let d
  if (Array.isArray(time)) {
    d = new Date(time[0], time[1] - 1, time[2], time[3], time[4], time[5])
  } else {
    d = new Date(time)
  }
  if (isNaN(d.getTime())) return '—'
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>

<style scoped>
.dashboard { max-width: 900px; }
.summary-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; margin-bottom: 32px; }
.summary-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 20px 16px;
  text-align: center;
}
.summary-card.clickable { cursor: pointer; transition: border-color 0.2s, background 0.2s; }
.summary-card.clickable:hover { border-color: var(--primary); background: var(--primary-light); }
.sc-value { font-size: 28px; color: var(--primary); margin-bottom: 4px; font-family: var(--mono); }
.sc-label { font-size: 11px; color: var(--text-muted); letter-spacing: 0.06em; text-transform: uppercase; }
.section-title {
  font-size: 12px; font-weight: 700; letter-spacing: 0.1em; text-transform: uppercase;
  color: var(--text-muted); margin-bottom: 12px;
}
.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.section-title-row .section-title { margin-bottom: 0; }

/* ── 各窗口办理量 — 横向柱状图 ──────────────────────────── */
.counter-chart { max-width: 640px; }
.counter-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 4px;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.counter-bar-row:hover {
  background: var(--bg-raised);
  border-color: var(--border-hi);
}
.counter-rank {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  font-family: var(--mono);
  color: var(--text-muted);
  background: rgba(0,0,0,0.05);
  flex-shrink: 0;
}
.counter-rank.rank-1 { background: #fef3c7; color: #b45309; }
.counter-rank.rank-2 { background: #e5e7eb; color: #4b5563; }
.counter-rank.rank-3 { background: #fed7aa; color: #9a3412; }
.counter-name {
  width: 140px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.counter-bar-track {
  flex: 1;
  height: 22px;
  background: rgba(0,0,0,0.05);
  border-radius: 3px;
  overflow: hidden;
}
.counter-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-hover), var(--primary));
  border-radius: 3px;
  transition: width 0.6s ease;
  min-width: 3px;
}
.counter-count {
  width: 42px;
  text-align: right;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--mono);
  color: var(--text-primary);
}

/* ── 业务占比横向柱状图 ─────────────────────────────── */
.biz-chart { max-width: 600px; }
.biz-bar-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  cursor: pointer;
  padding: 9px 12px;
  border-radius: 6px;
  border: 1px solid transparent;
  transition: all 0.2s;
}
.biz-bar-row:hover {
  background: var(--bg-raised);
  border-color: var(--border-hi);
}
.biz-bar-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.biz-bar-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-right: 2px;
}
.biz-bar-name { font-size: 13.5px; color: var(--text-primary); font-weight: 500; }
.biz-bar-count { font-size: 12px; color: var(--text-secondary); font-family: var(--mono); }
.biz-bar-track {
  position: relative;
  height: 24px;
  background: rgba(0,0,0,0.04);
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
}
.biz-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.6s ease;
  min-width: 2px;
}
.biz-bar-pct {
  position: absolute;
  right: 10px;
  font-size: 11.5px;
  color: var(--text-primary);
  font-family: var(--mono);
  font-weight: 700;
  line-height: 24px;
  z-index: 1;
}
.biz-bar-pct.pct-inside {
  right: 10px;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0,0,0,0.25);
}
.empty-tip { color: var(--text-muted); font-size: 13px; padding: 8px 0; }

/* ── 业务明细弹窗 ─────────────────────────────────── */
.biz-detail-dialog .el-dialog {
  background: #f7f8fa !important;
  border: 1px solid var(--border) !important;
  border-radius: 8px !important;
}
.biz-dlg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
}
.biz-dlg-left {
  display: flex;
  align-items: baseline;
  gap: 14px;
}
.biz-dlg-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }
.biz-dlg-count { font-size: 12px; color: var(--text-muted); }
.biz-dlg-count strong { color: var(--primary); font-size: 14px; }
.biz-detail-table { margin-top: 0; }

.counter-detail-dialog .el-dialog {
  background: #f7f8fa !important;
  border: 1px solid var(--border) !important;
  border-radius: 8px !important;
}
.counter-dlg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
}
.counter-dlg-left {
  display: flex;
  align-items: baseline;
  gap: 14px;
}
.counter-dlg-title { font-size: 16px; font-weight: 700; color: var(--text-primary); }
.counter-dlg-count { font-size: 12px; color: var(--text-muted); }
.counter-dlg-count strong { color: var(--primary); font-size: 14px; }
.counter-dlg-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.counter-dlg-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px 0;
}

/* ── 窗口弹窗内联进度条 ──────────────────────────────── */
.counter-spark-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.counter-spark-track {
  flex: 1;
  height: 14px;
  background: rgba(0,0,0,0.05);
  border-radius: 3px;
  overflow: hidden;
}
.counter-spark-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-hover), var(--primary));
  border-radius: 3px;
  transition: width 0.4s ease;
  min-width: 2px;
}
.counter-spark-num {
  width: 36px;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--mono);
  color: var(--text-primary);
}
</style>

<style>
/* ── Dialog ─────────────────────────────────────────────────── */
.ticket-detail-dialog .el-dialog {
  background: #f7f8fa !important;
  border: 1px solid var(--border) !important;
  border-radius: 8px !important;
  box-shadow: 0 24px 80px rgba(0,0,0,0.6) !important;
}
.ticket-detail-dialog .el-dialog__header { padding: 0 !important; margin: 0 !important; }
.ticket-detail-dialog .el-dialog__body { padding: 0 !important; }
.ticket-detail-dialog .el-dialog__headerbtn .el-dialog__close { color: var(--text-secondary) !important; }
.ticket-detail-dialog .el-dialog__headerbtn:hover .el-dialog__close { color: var(--primary) !important; }

/* ── 头部 ─────────────────────────────────────────────────── */
.dlg-header {
  padding: 18px 24px 0;
  border-bottom: 1px solid var(--border);
  background: #f7f8fa;
  border-radius: 8px 8px 0 0;
}
.dlg-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}
.dlg-title-group { display: flex; align-items: baseline; gap: 14px; }
.dlg-title {
  font-size: 16px; font-weight: 700; color: var(--text-primary); letter-spacing: 0.02em;
}
.dlg-count { font-size: 12px; color: var(--text-muted); display: flex; align-items: center; gap: 6px; }
.count-num { font-family: var(--mono); font-size: 14px; color: var(--primary); font-weight: 700; }
.dlg-actions { display: flex; align-items: center; gap: 8px; }
.icon-btn {
  background: transparent !important;
  border: 1px solid var(--border) !important;
  color: var(--text-secondary) !important;
  transition: all 0.15s;
}
.icon-btn:hover {
  border-color: var(--primary) !important;
  color: var(--primary) !important;
  background: var(--primary-light) !important;
}
.icon-svg {
  width: 16px;
  height: 16px;
}
.close-btn:hover .close-icon {
  animation: rotate-close 0.4s ease;
}
@keyframes rotate-close {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(90deg); }
}
.filter-active-tag {
  display: inline-block; padding: 1px 7px; font-size: 11px;
  background: rgba(244,67,54,0.12); color: var(--danger);
  border: 1px solid rgba(244,67,54,0.25); border-radius: 10px; letter-spacing: 0.03em;
}

/* ── 筛选栏 ─────────────────────────────────────────────────── */
.filter-bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; padding-bottom: 14px; }
.filter-bar .el-date-editor.el-input__wrapper,
.filter-bar .el-input__wrapper {
  background: var(--bg-raised) !important; border: 1px solid var(--border) !important; box-shadow: none !important;
}
.filter-bar .el-input__wrapper:hover,
.filter-bar .el-date-editor.el-input__wrapper:hover { border-color: var(--border-hi) !important; }
.filter-bar .el-input__wrapper.is-focus,
.filter-bar .el-date-editor.el-input__wrapper:focus {
  border-color: var(--primary) !important;
  box-shadow: 0 0 0 1px var(--primary-light) !important;
}
.filter-bar .el-input__inner { color: var(--text-primary) !important; font-family: var(--sans); font-size: 13px; }
.filter-bar .el-range-input { color: var(--text-primary) !important; font-family: var(--sans); font-size: 13px; }
.filter-bar .el-range-separator { color: var(--text-muted) !important; }
.filter-bar .el-input__placeholder { color: var(--text-muted) !important; }
.btn-search {
  background: var(--primary) !important; border: none !important;
  color: #0a0b0e !important; font-weight: 700 !important;
  letter-spacing: 0.06em !important; font-family: var(--cond) !important; transition: all 0.15s;
}
.btn-search:hover { opacity: 0.85; box-shadow: 0 0 20px var(--primary-light); }
.btn-reset {
  background: transparent !important; border: 1px solid var(--border) !important;
  color: var(--text-secondary) !important; font-family: var(--cond) !important; letter-spacing: 0.04em;
}
.btn-reset:hover { border-color: var(--border-hi) !important; color: var(--text-primary) !important; }

/* ── 自定义表格 ─────────────────────────────────────────────── */
.table-wrap { overflow-y: auto; max-height: 520px; }
.data-table { width: 100%; border-collapse: collapse; font-family: var(--sans); font-size: 13.5px; color: var(--text-primary); }
.data-table thead { position: sticky; top: 0; z-index: 2; }
.data-table thead th {
  background: #f7f8fa;
  color: var(--text-secondary);
  font-size: 11px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase;
  padding: 11px 14px; text-align: left;
  border-bottom: 1px solid var(--border); white-space: nowrap;
}
.data-table tbody tr { border-bottom: 1px solid var(--border); transition: background 0.1s; }
.data-table tbody tr:nth-child(odd) { background: var(--bg-card); }
.data-table tbody tr:nth-child(even) { background: rgba(30,35,48,0.5); }
.data-table tbody tr:hover { background: var(--bg-raised) !important; }
.data-table tbody tr:last-child { border-bottom: none; }
.data-table tbody td { padding: 11px 14px; color: var(--text-primary); vertical-align: middle; }
.data-table tbody td.cell-ticket-no { font-family: var(--mono); font-size: 14px; color: var(--primary); letter-spacing: 0.06em; }
.empty-cell { text-align: center; padding: 56px 0 !important; color: var(--text-muted); }
.empty-icon { display: block; font-size: 32px; margin-bottom: 10px; opacity: 0.5; }

/* ── 状态标签 ─────────────────────────────────────────────────── */
.status-tag {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 9px; border-radius: 3px; font-size: 11.5px;
  font-weight: 700; letter-spacing: 0.04em; font-family: var(--cond);
  border: 1px solid;
}
.status-dot { width: 5px; height: 5px; border-radius: 50%; flex-shrink: 0; }
.status-WAITING   { background: rgba(255,152,0,0.1);  color: #ff9800; border-color: rgba(255,152,0,0.25); }
.status-WAITING  .status-dot { background: #ff9800; box-shadow: 0 0 4px #ff9800; }
.status-CALLED    { background: rgba(0,82,217,0.12);  color: #0052d9; border-color: rgba(0,82,217,0.22); }
.status-CALLED   .status-dot { background: #0052d9; box-shadow: 0 0 4px #0052d9; }
.status-SERVING   { background: rgba(0,230,118,0.1);  color: #00e676; border-color: rgba(0,230,118,0.25); }
.status-SERVING  .status-dot { background: #00e676; box-shadow: 0 0 4px #00e676; animation: pulse-dot 1.4s ease infinite; }
.status-COMPLETED { background: rgba(74,85,104,0.15); color: #8892a4; border-color: rgba(74,85,104,0.3); }
.status-COMPLETED .status-dot { background: #8892a4; }
.status-CANCELLED { background: rgba(244,67,54,0.1);  color: #f44336; border-color: rgba(244,67,54,0.25); }
.status-CANCELLED .status-dot { background: #f44336; }
.status-SKIPPED   { background: rgba(156,39,176,0.1);  color: #ce93d8; border-color: rgba(156,39,176,0.25); }
.status-SKIPPED   .status-dot { background: #ce93d8; }

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}
</style>
