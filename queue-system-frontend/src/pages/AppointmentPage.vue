<template>
  <div class="appointment-page">
    <div class="header">
      <span class="live-dot"></span>
      <h1>扫码取号</h1>
      <p class="subtitle">{{ regionName || '加载中...' }}</p>
    </div>

    <div class="content">
      <!-- 错误状态：无效区域 -->
      <div v-if="error" class="error-state">
        <div class="error-icon">!</div>
        <p>{{ error }}</p>
        <button class="btn-secondary" @click="retry">重试</button>
      </div>

      <!-- 阶段1：手机号查询 -->
      <div v-else-if="pageState === 'query'" class="query-section">
        <p class="query-desc">请输入手机号码查询排队情况，或取新号</p>
        <div class="form-group">
          <label>手机号码</label>
          <input
            v-model="phone"
            type="tel"
            class="input"
            placeholder="请输入手机号码"
            maxlength="11"
            @keyup.enter="checkActiveTicket"
          />
        </div>
        <div class="action-row">
          <button
            class="btn-primary"
            :disabled="!phoneValid || queryLoading"
            @click="checkActiveTicket"
          >
            {{ queryLoading ? '查询中...' : '查询排队' }}
          </button>
        </div>
        <div class="action-row" style="margin-top: var(--sp-3);">
          <button class="btn-secondary" @click="goToTicketForm">跳过，直接取号</button>
        </div>
      </div>

      <!-- 阶段2a：有未完成票据 → 显示排队进度 -->
      <div v-else-if="pageState === 'tracking'" class="tracking-section">
        <div class="status-badge" :class="activeTicket.status?.toLowerCase()">
          {{ activeTicket.statusText || '等待中' }}
        </div>
        <div class="ticket-card">
          <div class="ticket-no">{{ getDisplayTicketNo(activeTicket.ticketNo) }}</div>
          <div class="ticket-biz">{{ activeTicket.businessTypeName }}</div>
        </div>
        <div class="ticket-info">
          <div v-if="activeTicket.waitingCount != null && activeTicket.waitingCount > 0" class="info-item">
            <span class="info-label">前方等待</span>
            <span class="info-value">{{ activeTicket.waitingCount }} 人</span>
          </div>
          <div v-if="activeTicket.waitingCount != null && activeTicket.waitingCount > 0" class="info-item">
            <span class="info-label">预计等待</span>
            <span class="info-value">约 {{ activeTicket.estimatedWaitMinutes || 0 }} 分钟</span>
          </div>
          <div v-if="activeTicket.counterName" class="info-item">
            <span class="info-label">服务窗口</span>
            <span class="info-value">{{ activeTicket.counterName }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">取号时间</span>
            <span class="info-value">{{ formatTime(activeTicket.createdAt) }}</span>
          </div>
        </div>
        <div class="action-row">
          <button class="btn-secondary" @click="goToTicketForm">取新号</button>
          <button class="btn-primary" @click="refreshTracking">刷新进度</button>
        </div>
      </div>

      <!-- 阶段2b：无票据 → 取号表单 -->
      <div v-else-if="pageState === 'ticket'" class="form-section">
        <div class="form-group">
          <label>业务类型</label>
          <select v-model="selectedBusinessId" class="input select" :disabled="loading" @focus="onBusinessSelectFocus">
            <option value="">请选择业务类型</option>
            <option v-for="biz in businessTypes" :key="biz.id" :value="biz.id">
              {{ biz.name }}（{{ biz.prefix }}）— 等待 {{ biz.waitingCount || 0 }} 人
            </option>
          </select>
        </div>

        <div class="form-group">
          <label>手机号码</label>
          <input v-model="phone" type="tel" class="input" placeholder="请输入手机号码" maxlength="11" />
        </div>

        <div class="form-group">
          <label>姓名</label>
          <input v-model="name" type="text" class="input" placeholder="请输入您的姓名" />
        </div>

        <div class="action-row">
          <button
            class="btn-primary"
            :disabled="!canSubmit || submitting"
            @click="takeTicket"
          >
            {{ submitting ? '取号中...' : '立即取号' }}
          </button>
        </div>

        <div class="tips">
          <p>· 同一业务类型每天仅可取一个号</p>
          <p>· 取号后请留意叫号大屏</p>
        </div>
      </div>

      <!-- 取号成功 -->
      <div v-else-if="pageState === 'success'" class="success-section">
        <div class="success-icon">✓</div>
        <h2>取号成功</h2>
        <div class="ticket-card">
          <div class="ticket-no">{{ getDisplayTicketNo(ticketResult.ticketNo) }}</div>
          <div class="ticket-biz">{{ ticketResult.businessType }}</div>
        </div>
        <div class="ticket-info">
          <div class="info-item">
            <span class="info-label">前方等待</span>
            <span class="info-value">{{ ticketResult.waitingCount }} 人</span>
          </div>
          <div class="info-item">
            <span class="info-label">预计等待</span>
            <span class="info-value">约 {{ ticketResult.estimatedWaitMinutes }} 分钟</span>
          </div>
        </div>
        <div class="action-row">
          <button class="btn-secondary" @click="goToTicketForm">继续取号</button>
          <button class="btn-primary" @click="goToTracking">查询进度</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { getDisplayTicketNo } from '../utils/ticketUtils'

const API_BASE = ''

const regionCode = ref('')
const regionId = ref(null)
const regionName = ref('')
const businessTypes = ref([])
const selectedBusinessId = ref('')
const phone = ref('')
const name = ref('')
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const ticketResult = ref({})

// 页面状态: 'query' | 'tracking' | 'ticket' | 'success'
const pageState = ref('query')
const queryLoading = ref(false)
const activeTicket = ref({})

// 轮询定时器
let refreshTimer = null

const phoneValid = computed(() => {
  return phone.value.length === 11 && /^1[3-9]\d{9}$/.test(phone.value)
})

const canSubmit = computed(() => {
  return selectedBusinessId.value && phone.value.length === 11 && name.value.trim()
})

const route = useRoute()

onMounted(() => {
  regionCode.value = route.query.region || route.query.regionCode
  if (!regionCode.value) {
    error.value = '无效的二维码，缺少区域信息'
    return
  }
  fetchRegionAndTypes()
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})

async function fetchRegionAndTypes() {
  loading.value = true
  error.value = ''
  try {
    const regionRes = await axios.get(`/api/v1/regions/code/${regionCode.value}`)
    if (regionRes.data.code !== 200 || !regionRes.data.data) {
      error.value = '无效的区域信息'
      return
    }
    const region = regionRes.data.data
    regionId.value = region.id
    const fullnameRes = await axios.get(`/api/v1/regions/code/${regionCode.value}/fullname`)
    if (fullnameRes.data.code === 200 && fullnameRes.data.data) {
      regionName.value = fullnameRes.data.data
    } else {
      regionName.value = region.name
    }
    const typesRes = await axios.get(`/api/v1/business-types`, { params: { regionId: regionId.value } })
    if (typesRes.data.code === 200) {
      businessTypes.value = typesRes.data.data || []
    }
  } catch (e) {
    error.value = '网络错误，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

/** 仅刷新业务类型（带等待人数） */
async function fetchBusinessTypes() {
  if (!regionId.value) return
  try {
    const typesRes = await axios.get(`/api/v1/business-types`, { params: { regionId: regionId.value } })
    if (typesRes.data.code === 200) {
      businessTypes.value = typesRes.data.data || []
    }
  } catch (e) {
    // 静默失败，不影响用户操作
  }
}

/** 查询未完成票据 */
async function checkActiveTicket() {
  if (!phoneValid.value) return
  queryLoading.value = true
  try {
    const res = await axios.post(`/api/v1/ticket/active`, null, {
      params: { regionCode: regionCode.value, phone: phone.value }
    })
    if (res.data.code === 200) {
      const data = res.data.data
      if (data.hasActive) {
        activeTicket.value = data
        pageState.value = 'tracking'
        // 等待中则轮询
        if (data.status === 'WAITING') {
          startRefreshTimer()
        }
      } else {
        // 无未完成票据，进入取号表单
        pageState.value = 'ticket'
      }
    }
  } catch (e) {
    ElMessage.error('查询失败，请重试')
  } finally {
    queryLoading.value = false
  }
}

/** 切换到取号表单 */
function goToTicketForm() {
  if (refreshTimer) clearInterval(refreshTimer)
  pageState.value = 'ticket'
  // 刷新业务类型等待人数
  fetchBusinessTypes()
}

/** 切换到排队进度 */
function goToTracking() {
  if (refreshTimer) clearInterval(refreshTimer)
  activeTicket.value = {
    ticketNo: ticketResult.value.ticketNo,
    businessTypeName: ticketResult.value.businessType,
    status: 'WAITING',
    statusText: '等待中',
    waitingCount: ticketResult.value.waitingCount,
    estimatedWaitMinutes: ticketResult.value.estimatedWaitMinutes,
    createdAt: ticketResult.value.createdAt
  }
  pageState.value = 'tracking'
  startRefreshTimer()
}

/** 刷新排队进度 */
async function refreshTracking() {
  try {
    const res = await axios.post(`/api/v1/ticket/active`, null, {
      params: { regionCode: regionCode.value, phone: phone.value }
    })
    if (res.data.code === 200 && res.data.data.hasActive) {
      activeTicket.value = res.data.data
      if (res.data.data.status !== 'WAITING' && refreshTimer) {
        clearInterval(refreshTimer)
        refreshTimer = null
      }
    } else {
      ElMessage.info('票据状态已变更')
      if (refreshTimer) clearInterval(refreshTimer)
      refreshTimer = null
    }
  } catch (e) {
    ElMessage.error('刷新失败')
  }
}

function startRefreshTimer() {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = setInterval(() => {
    refreshTracking()
  }, 10000) // 每10秒刷新
}

async function takeTicket() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const res = await axios.post(`/api/v1/ticket/take`, {
      regionId: Number(regionId.value),
      businessTypeId: Number(selectedBusinessId.value),
      phone: phone.value,
      name: name.value
    })
    if (res.data.code === 200) {
      ticketResult.value = res.data.data
      pageState.value = 'success'
      ElMessage.success('取号成功')
    } else {
      ElMessage.error(res.data.message || '取号失败')
    }
  } catch (e) {
    const msg = e.response?.data?.message || '取号失败，请重试'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

function retry() {
  error.value = ''
  fetchRegionAndTypes()
}

function formatTime(timeStr) {
  if (!timeStr) return ''
  // 兼容 LocalDateTime 数组格式 [year, month, day, hour, minute, second] 和 ISO 字符串格式
  let d
  if (Array.isArray(timeStr)) {
    // 注意：JS 的 month 是 0-11，比 LocalDateTime 小 1
    d = new Date(timeStr[0], timeStr[1] - 1, timeStr[2], timeStr[3], timeStr[4], timeStr[5] || 0)
  } else {
    d = new Date(timeStr)
  }
  if (isNaN(d.getTime())) return '—'
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function onBusinessSelectFocus() {
  if (businessTypes.value.length === 0) {
    ElMessage({
      message: '您所在的区域暂未开通可预约业务，请联系管理员处理，谢谢！',
      type: 'warning',
      duration: 3000
    })
  }
}
</script>

<style scoped>
.appointment-page {
  min-height: 100vh;
  background: var(--bg-body);
  padding: var(--sp-8) var(--sp-4);
}

.header {
  text-align: center;
  margin-bottom: var(--sp-8);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
}

.header h1 {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--text-primary);
}

.subtitle {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.content {
  max-width: 520px;
  margin: 0 auto;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--sp-8);
  box-shadow: var(--shadow-lg);
}

.error-state,
.query-section,
.tracking-section,
.success-section {
  text-align: center;
}

.error-icon,
.success-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  margin: 0 auto var(--sp-4);
}

.error-icon {
  background: var(--danger-light);
  color: var(--danger);
  border: 1px solid rgba(227, 77, 89, 0.2);
}

.success-icon {
  background: var(--success-light);
  color: var(--success);
  border: 1px solid rgba(43, 196, 138, 0.2);
}

.error-state p,
.query-desc {
  color: var(--text-secondary);
  margin-bottom: var(--sp-6);
}

.tracking-section,
.success-section {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.status-badge {
  display: inline-block;
  padding: 6px 16px;
  border-radius: 999px;
  font-size: var(--text-sm);
  font-weight: 600;
  margin-bottom: var(--sp-5);
}

.status-badge.waiting {
  background: var(--warning-light);
  color: var(--warning);
  border: 1px solid rgba(255, 160, 0, 0.2);
}

.status-badge.called {
  background: var(--info-light);
  color: var(--info);
  border: 1px solid rgba(0, 82, 217, 0.2);
}

.status-badge.serving {
  background: var(--success-light);
  color: var(--success);
  border: 1px solid rgba(43, 196, 138, 0.2);
}

.form-group {
  margin-bottom: var(--sp-5);
}

.form-group label {
  display: block;
  font-size: var(--text-sm);
  color: var(--text-primary);
  font-weight: 500;
  margin-bottom: var(--sp-2);
}

.input {
  width: 100%;
  padding: var(--sp-3) var(--sp-4);
  background: #fff;
  border: 1px solid var(--border-input);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: var(--text-base);
  box-sizing: border-box;
  transition: border-color var(--duration-fast), box-shadow var(--duration-fast);
  min-height: var(--touch-md);
}

.input:focus {
  outline: none;
  border-color: var(--primary);
}

.input::placeholder {
  color: var(--text-muted);
}

.select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23666666' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  cursor: pointer;
}

.action-row {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-6);
  flex-wrap: wrap;
}

.btn-primary,
.btn-secondary {
  flex: 1;
  min-width: 0;
  padding: var(--sp-3) var(--sp-6);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  border: 1px solid;
  min-height: var(--touch-md);
  white-space: nowrap;
}

.btn-primary {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--primary-hover);
  border-color: var(--primary-hover);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: #fff;
  border-color: var(--border-input);
  color: var(--text-primary);
}

.btn-secondary:hover {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-light);
}

.tips {
  margin-top: var(--sp-6);
  padding: var(--sp-4);
  background: #f7f8fa;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
}

.tips p {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  line-height: 1.8;
  margin: 0;
}

.success-section h2 {
  color: var(--text-primary);
  margin-bottom: var(--sp-6);
  font-size: var(--text-xl);
}

.ticket-card {
  width: 100%;
  box-sizing: border-box;
  background: linear-gradient(180deg, #ffffff 0%, #f7faff 100%);
  border: 1px solid rgba(0, 82, 217, 0.16);
  border-top: 4px solid var(--primary);
  border-radius: var(--radius-lg);
  padding: var(--sp-8);
  margin-bottom: var(--sp-6);
}

.ticket-no {
  font-size: var(--text-hero);
  font-weight: 700;
  letter-spacing: 0.08em;
  font-family: var(--mono);
  color: var(--primary);
  line-height: 1.1;
}

.ticket-biz {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  margin-top: var(--sp-2);
}

.ticket-info {
  display: flex;
  gap: var(--sp-4);
  margin-bottom: var(--sp-6);
  width: 100%;
}

.info-item {
  flex: 1;
  background: #f7f8fa;
  border-radius: var(--radius-md);
  padding: var(--sp-4);
  border: 1px solid var(--border);
}

.info-label {
  display: block;
  font-size: var(--text-xs);
  color: var(--text-muted);
  margin-bottom: var(--sp-1);
}

.info-value {
  display: block;
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-primary);
}

@media (max-width: 768px) {
  .content {
    padding: var(--sp-6);
  }
}

@media (max-width: 480px) {
  .appointment-page {
    padding: var(--sp-4) var(--sp-3);
  }

  .content {
    padding: var(--sp-5);
    border-radius: var(--radius-md);
  }

  .input {
    font-size: 16px;
  }

  .action-row,
  .ticket-info {
    flex-direction: column;
  }

  .btn-primary,
  .btn-secondary {
    width: 100%;
  }
}
</style>
