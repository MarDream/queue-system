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
          <div class="ticket-no">{{ activeTicket.ticketNo }}</div>
          <div class="ticket-biz">{{ activeTicket.businessTypeName }}</div>
        </div>
        <div class="ticket-info">
          <div v-if="activeTicket.status === 'WAITING'" class="info-item">
            <span class="info-label">前方等待</span>
            <span class="info-value">{{ activeTicket.waitingCount || 0 }} 人</span>
          </div>
          <div v-if="activeTicket.status === 'WAITING'" class="info-item">
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
          <div class="ticket-no">{{ ticketResult.ticketNo }}</div>
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
  const d = new Date(timeStr)
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
  background: var(--bg-void);
  padding: var(--sp-6) var(--sp-4);
  position: relative;
}

.appointment-page::before {
  content: '';
  position: absolute;
  top: -30%; right: -20%;
  width: 70vw;
  height: 70vw;
  max-width: 600px;
  max-height: 600px;
  background: radial-gradient(circle, var(--accent-glow) 0%, transparent 70%);
  opacity: 0.3;
  pointer-events: none;
}

.header {
  text-align: center;
  color: var(--text-primary);
  margin-bottom: var(--sp-8);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  position: relative;
  z-index: 1;
}

.header .live-dot {
  margin-bottom: var(--sp-1);
}

.header h1 {
  font-size: var(--text-2xl);
  margin-bottom: 0;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.subtitle {
  font-size: var(--text-xs);
  color: var(--text-muted);
  letter-spacing: 0.1em;
}

.content {
  max-width: 480px;
  margin: 0 auto;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--sp-8);
  box-shadow: var(--shadow-lg), 0 0 60px rgba(0,229,255,0.03);
  position: relative;
  z-index: 1;
}

.error-state {
  text-align: center;
  padding: var(--sp-10) 0;
}

.error-icon {
  width: 64px;
  height: 64px;
  background: var(--danger-glow);
  color: var(--danger);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-3xl);
  font-weight: bold;
  margin: 0 auto var(--sp-4);
  border: 1px solid rgba(244,67,54,0.3);
}

.error-state p {
  color: var(--text-secondary);
  margin-bottom: var(--sp-6);
}

/* 手机号查询 */
.query-section {
  text-align: center;
}

.query-desc {
  color: var(--text-secondary);
  font-size: var(--text-sm);
  margin-bottom: var(--sp-6);
}

/* 排队进度 */
.tracking-section {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.status-badge {
  display: inline-block;
  padding: var(--sp-1) var(--sp-4);
  border-radius: 20px;
  font-size: var(--text-sm);
  font-weight: 600;
  margin-bottom: var(--sp-5);
}

.status-badge.waiting {
  background: rgba(255, 193, 7, 0.15);
  color: #ffc107;
  border: 1px solid rgba(255, 193, 7, 0.3);
}

.status-badge.called {
  background: rgba(0, 229, 255, 0.15);
  color: var(--accent);
  border: 1px solid rgba(0, 229, 255, 0.3);
  animation: pulse 1.5s infinite;
}

.status-badge.serving {
  background: rgba(0, 230, 118, 0.15);
  color: var(--success);
  border: 1px solid rgba(0, 230, 118, 0.3);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.form-group {
  margin-bottom: var(--sp-5);
}

.form-group label {
  display: block;
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: 500;
  margin-bottom: var(--sp-2);
}

.input {
  width: 100%;
  padding: var(--sp-3) var(--sp-4);
  background: var(--bg-raised);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: var(--text-base);
  box-sizing: border-box;
  transition: border-color var(--duration-fast);
  min-height: var(--touch-md);
}

.input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-glow);
}

.input::placeholder {
  color: var(--text-muted);
}

.select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%238892a4' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
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

.btn-primary, .btn-secondary {
  flex: 1;
  min-width: 0;
  padding: var(--sp-3) var(--sp-6);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  border: 1.5px solid;
  min-height: var(--touch-lg);
}

.btn-primary {
  background: transparent;
  border-color: var(--accent);
  color: var(--accent);
  box-shadow: var(--glow-accent);
}

.btn-primary:hover:not(:disabled) {
  background: var(--accent-glow);
  box-shadow: var(--glow-accent-lg);
}

.btn-primary:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  box-shadow: none;
}

.btn-secondary {
  background: transparent;
  border-color: var(--border-hi);
  color: var(--text-secondary);
}

.btn-secondary:hover {
  border-color: var(--text-secondary);
  background: var(--bg-raised);
}

.tips {
  margin-top: var(--sp-6);
  padding: var(--sp-4);
  background: var(--bg-raised);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
}

.tips p {
  font-size: var(--text-xs);
  color: var(--text-muted);
  line-height: 1.8;
  margin: 0;
}

.success-section {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.success-icon {
  width: 72px;
  height: 72px;
  background: var(--success-glow);
  color: var(--success);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  margin-bottom: var(--sp-4);
  border: 1px solid rgba(0,230,118,0.3);
}

.success-section h2 {
  color: var(--text-primary);
  margin-bottom: var(--sp-6);
  font-size: var(--text-xl);
}

.ticket-card {
  background: linear-gradient(135deg, rgba(0,229,255,0.1) 0%, rgba(0,151,167,0.05) 100%);
  border: 1px solid rgba(0,229,255,0.3);
  border-radius: var(--radius-lg);
  padding: var(--sp-8);
  color: var(--accent);
  margin-bottom: var(--sp-6);
  width: 100%;
  box-sizing: border-box;
}

.ticket-no {
  font-size: var(--text-hero);
  font-weight: bold;
  letter-spacing: 0.08em;
  font-family: var(--mono);
  text-shadow: 0 0 30px var(--accent-glow);
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
  background: var(--bg-raised);
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

/* Tablet */
@media (max-width: 768px) {
  .content {
    padding: var(--sp-6);
  }
}

/* Mobile - thumb-friendly */
@media (max-width: 480px) {
  .appointment-page {
    padding: var(--sp-4) var(--sp-3);
  }

  .header {
    margin-bottom: var(--sp-6);
  }

  .content {
    padding: var(--sp-5);
    border-radius: var(--radius-md);
  }

  .input {
    font-size: 16px; /* Prevents iOS zoom on focus */
  }

  .action-row {
    flex-direction: column;
  }

  .btn-primary, .btn-secondary {
    width: 100%;
  }

  .ticket-info {
    flex-direction: column;
    gap: var(--sp-3);
  }
}
</style>
