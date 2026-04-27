<template>
  <div class="ai-wrap">
    <div v-if="!props.hideHeader" class="ai-head">
      <div class="ai-title">智能问数</div>
      <div class="ai-sub">规则解析版（支持会话、表格明细与导出）</div>
    </div>

    <div class="ai-main">
      <div class="ai-sessions">
        <div class="ai-sessions-head">
          <div class="ai-sessions-title">会话</div>
          <el-button size="small" @click="newSession" :disabled="loadingSessions">新建</el-button>
        </div>
        <div class="ai-sessions-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            :class="['ai-session-item', { active: s.id === activeSessionId }]"
            @click="selectSession(s.id)"
          >
            <div class="ai-session-title">{{ s.title || s.id }}</div>
            <div class="ai-session-meta">
              <span>{{ formatDateText(s.updatedAt || s.createdAt) }}</span>
              <button class="ai-session-del" title="删除" @click.stop="deleteSession(s.id)">×</button>
            </div>
          </div>
          <div v-if="!loadingSessions && sessions.length === 0" class="ai-sessions-empty">
            暂无会话
          </div>
        </div>
      </div>

      <div class="ai-right">
        <div v-if="props.workspace === 'admin'" class="ai-config">
          <el-select
            v-model="selectedRegionId"
            clearable
            filterable
            placeholder="区域"
            style="width: 240px"
            :loading="loadingOptions"
          >
            <el-option
              v-for="r in regions"
              :key="r.id"
              :label="formatRegionLabel(r)"
              :value="r.id"
            />
          </el-select>

          <el-select
            v-model="selectedBusinessTypeId"
            clearable
            filterable
            placeholder="业务类型"
            style="width: 220px"
            :disabled="loadingOptions || businessTypes.length === 0"
          >
            <el-option v-for="b in businessTypes" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>

          <el-input-number
            v-model="selectedLimit"
            :min="10"
            :max="2000"
            :step="10"
            controls-position="right"
          />
        </div>

        <div v-if="cards.length" class="ai-cards">
          <div v-for="(c, idx) in cards" :key="idx" class="ai-card">
            <div class="ai-card-title">{{ c.title }}</div>
            <div class="ai-card-value">
              <span class="ai-card-num">{{ c.value }}</span>
              <span v-if="c.unit" class="ai-card-unit">{{ c.unit }}</span>
            </div>
          </div>
        </div>

        <div v-if="tableColumns.length && tableRows.length" class="ai-table">
          <div class="ai-table-head">
            <div class="ai-table-title">明细表</div>
            <div class="ai-table-actions">
              <el-button size="small" :disabled="exporting || !activeSessionId" @click="exportTable">导出</el-button>
            </div>
          </div>
          <el-table :data="tableRows" size="small" border style="width: 100%">
            <el-table-column
              v-for="c in tableColumns"
              :key="c.key"
              :prop="c.key"
              :label="c.label"
              :min-width="c.minWidth || 120"
            />
          </el-table>
        </div>

        <div class="ai-chat">
          <div v-if="messages.length === 0" class="ai-empty">
            <div v-if="props.workspace === 'counter' && !props.counterId">请先选择窗口</div>
            <div v-else>支持示例：当前等待人数；今日取号量；昨日办结量；近7天过号量；今日取号明细</div>
          </div>
          <div v-else class="ai-msg-list">
            <div v-for="m in messages" :key="m.id" :class="['ai-msg', m.role]">
              <div class="ai-bubble">{{ m.text }}</div>
              <div class="ai-meta">{{ formatTimeText(m.createdAt) }}</div>
            </div>
          </div>
        </div>

        <div class="ai-input">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            resize="none"
            :disabled="sending || (props.workspace === 'counter' && !props.counterId)"
            placeholder="输入问题，回车发送（Shift+Enter 换行）"
            @keydown.enter.prevent="handleEnter"
          />
          <div class="ai-actions">
            <el-button :disabled="sending || !question.trim() || (props.workspace === 'counter' && !props.counterId)" type="primary" @click="send">
              发送
            </el-button>
            <el-button :disabled="sending || !activeSessionId" @click="deleteSession(activeSessionId)">删除会话</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { aiQueryApi } from '../../api/admin'
import request from '../../api'
import { businessTypeApi, regionBusinessApi } from '../../api/admin'

type AiSession = {
  id: string
  workspace?: string
  title?: string
  createdAt?: any
  updatedAt?: any
}

type AiMessage = {
  id: string
  role: 'user' | 'assistant'
  text: string
  createdAt?: any
}

type AiCard = {
  title: string
  value: any
  unit?: string
}

type AiTableColumn = {
  key: string
  label: string
  minWidth?: number
}

type RegionOption = {
  id: number
  name?: string
  regionName?: string
  code?: string
  regionCode?: string
}

type BusinessTypeOption = {
  id: number
  name: string
}

const props = withDefaults(defineProps<{
  workspace?: 'admin' | 'counter'
  counterId?: number | null
  regionId?: number | null
  businessTypeId?: number | null
  hideHeader?: boolean
}>(), {
  workspace: 'admin',
  counterId: null,
  regionId: null,
  businessTypeId: null,
  hideHeader: false
})

const question = ref('')
const sending = ref(false)
const cards = ref<AiCard[]>([])
const sessions = ref<AiSession[]>([])
const activeSessionId = ref('')
const messages = ref<AiMessage[]>([])
const tableColumns = ref<AiTableColumn[]>([])
const tableRows = ref<Array<Record<string, any>>>([])
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const exporting = ref(false)
const loadingOptions = ref(false)
const regions = ref<RegionOption[]>([])
const businessTypes = ref<BusinessTypeOption[]>([])
const selectedRegionId = ref<number | null>(props.regionId ?? null)
const selectedBusinessTypeId = ref<number | null>(props.businessTypeId ?? null)
const selectedLimit = ref<number>(props.workspace === 'admin' ? 200 : 200)

function formatRegionLabel(r: RegionOption) {
  const name = r.name || r.regionName || ''
  const code = r.code || r.regionCode || ''
  return code ? `${name}（${code}）` : name
}

function formatTimeText(v: any) {
  if (!v) return ''
  if (Array.isArray(v) && v.length >= 5) {
    const hh = String(v[3]).padStart(2, '0')
    const mm = String(v[4]).padStart(2, '0')
    return `${hh}:${mm}`
  }
  if (typeof v === 'string') {
    const d = new Date(v)
    if (!Number.isNaN(d.getTime())) {
      const hh = String(d.getHours()).padStart(2, '0')
      const mm = String(d.getMinutes()).padStart(2, '0')
      return `${hh}:${mm}`
    }
    return v
  }
  return ''
}

function formatDateText(v: any) {
  if (!v) return ''
  if (Array.isArray(v) && v.length >= 3) {
    const y = v[0]
    const m = String(v[1]).padStart(2, '0')
    const d = String(v[2]).padStart(2, '0')
    return `${y}-${m}-${d}`
  }
  if (typeof v === 'string') {
    const d = new Date(v)
    if (!Number.isNaN(d.getTime())) {
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    }
    return v
  }
  return ''
}

function normalizeMessageList(list: any): AiMessage[] {
  return (list || []).map((m: any) => ({
    id: m.id || `${Date.now()}_${Math.random().toString(16).slice(2)}`,
    role: m.role || 'assistant',
    text: m.text || '',
    createdAt: m.createdAt
  }))
}

function handleEnter(e: KeyboardEvent) {
  if (e.shiftKey) return
  send()
}

async function loadSessions() {
  loadingSessions.value = true
  try {
    const res: any = await aiQueryApi.listSessions(props.workspace)
    sessions.value = Array.isArray(res) ? (res as AiSession[]) : []
    if (activeSessionId.value && sessions.value.some(s => s.id === activeSessionId.value)) {
      return
    }
    activeSessionId.value = sessions.value[0]?.id || ''
    if (activeSessionId.value) {
      await loadMessages(activeSessionId.value)
    } else {
      messages.value = []
    }
  } catch (e) {
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

async function loadOptions() {
  if (props.workspace !== 'admin') return
  loadingOptions.value = true
  try {
    const rawUserId = localStorage.getItem('userId')
    const uid = rawUserId ? Number(rawUserId) : undefined
    const res: any = await request.get('/regions', { params: uid ? { userId: uid } : undefined })
    regions.value = Array.isArray(res) ? (res as RegionOption[]) : []
    if (selectedRegionId.value == null && props.regionId != null) {
      selectedRegionId.value = props.regionId
    }
    await loadBusinessTypes()
  } catch (e) {
    regions.value = []
    businessTypes.value = []
  } finally {
    loadingOptions.value = false
  }
}

async function loadBusinessTypes() {
  if (props.workspace !== 'admin') return
  try {
    if (selectedRegionId.value != null) {
      const res: any = await regionBusinessApi.listByRegion(selectedRegionId.value)
      businessTypes.value = Array.isArray(res) ? (res as BusinessTypeOption[]) : []
    } else {
      const res: any = await businessTypeApi.list()
      businessTypes.value = Array.isArray(res) ? (res as BusinessTypeOption[]) : []
    }
    if (
      selectedBusinessTypeId.value != null &&
      !businessTypes.value.some(b => Number(b.id) === Number(selectedBusinessTypeId.value))
    ) {
      selectedBusinessTypeId.value = null
    }
  } catch (e) {
    businessTypes.value = []
    selectedBusinessTypeId.value = null
  }
}

async function loadMessages(sessionId: string) {
  if (!sessionId) return
  loadingMessages.value = true
  try {
    const res: any = await aiQueryApi.getMessages(sessionId, props.workspace)
    messages.value = normalizeMessageList(Array.isArray(res) ? res : [])
  } catch (e) {
    messages.value = []
  } finally {
    loadingMessages.value = false
  }
}

function selectSession(sessionId: string) {
  activeSessionId.value = sessionId
  cards.value = []
  tableColumns.value = []
  tableRows.value = []
  loadMessages(sessionId)
}

async function newSession() {
  activeSessionId.value = ''
  messages.value = []
  cards.value = []
  tableColumns.value = []
  tableRows.value = []
}

async function deleteSession(sessionId: string) {
  if (!sessionId) return
  try {
    await aiQueryApi.deleteSession(sessionId, props.workspace)
    if (sessionId === activeSessionId.value) {
      activeSessionId.value = ''
      messages.value = []
      cards.value = []
      tableColumns.value = []
      tableRows.value = []
    }
    await loadSessions()
  } catch (e) {
    const err = e as any
    ElMessage.error(err?.message || '删除失败')
  }
}

async function send() {
  const q = question.value.trim()
  if (!q || sending.value) return
  sending.value = true
  try {
    const useRegionId = props.workspace === 'admin' ? selectedRegionId.value : props.regionId
    const useBusinessTypeId = props.workspace === 'admin' ? selectedBusinessTypeId.value : props.businessTypeId
    const useLimit = props.workspace === 'admin' ? selectedLimit.value : undefined
    const res: any = await aiQueryApi.ask({
      workspace: props.workspace,
      sessionId: activeSessionId.value || undefined,
      question: q,
      counterId: props.counterId || undefined,
      regionId: useRegionId || undefined,
      businessTypeId: useBusinessTypeId || undefined,
      limit: useLimit || undefined
    })
    const sid = res?.sessionId || activeSessionId.value
    if (sid) activeSessionId.value = sid
    cards.value = (res?.cards || []) as AiCard[]
    tableColumns.value = (res?.tableColumns || []) as AiTableColumn[]
    tableRows.value = (res?.tableRows || []) as Array<Record<string, any>>
    question.value = ''
    await Promise.all([
      loadSessions(),
      sid ? loadMessages(sid) : Promise.resolve()
    ])
  } catch (e) {
    const err = e as any
    ElMessage.error(err?.message || '请求失败')
  } finally {
    sending.value = false
  }
}

async function exportTable() {
  if (!activeSessionId.value) return
  exporting.value = true
  try {
    await aiQueryApi.exportXlsx({ sessionId: activeSessionId.value, workspace: props.workspace })
  } catch (e) {
    const err = e as any
    ElMessage.error(err?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadSessions(), loadOptions()])
})

watch(() => [props.workspace, props.counterId], async () => {
  await Promise.all([loadSessions(), loadOptions()])
})

watch(() => selectedRegionId.value, async () => {
  await loadBusinessTypes()
})
</script>

<style scoped>
.ai-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-main {
  display: flex;
  gap: 10px;
  min-height: 520px;
}

.ai-sessions {
  width: 220px;
  flex-shrink: 0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.ai-sessions-head {
  padding: 10px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.ai-sessions-title {
  font-size: 13px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.86);
}

.ai-sessions-list {
  padding: 8px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-session-item {
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
}

.ai-session-item.active {
  border-color: rgba(64, 158, 255, 0.45);
  background: rgba(64, 158, 255, 0.12);
}

.ai-session-title {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.9);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.ai-session-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
}

.ai-session-del {
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.55);
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
  font-size: 14px;
}

.ai-sessions-empty {
  padding: 18px 8px;
  text-align: center;
  color: rgba(255, 255, 255, 0.55);
  font-size: 12px;
}

.ai-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.ai-config {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ai-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.ai-title {
  font-size: 16px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
}

.ai-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.ai-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.ai-card {
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.ai-card-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.62);
}

.ai-card-value {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.ai-card-num {
  font-size: 22px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
}

.ai-card-unit {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

.ai-chat {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 12px;
  min-height: 280px;
  flex: 1;
  overflow: auto;
}

.ai-empty {
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
  line-height: 1.6;
}

.ai-msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-msg {
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.ai-msg.user {
  align-self: flex-end;
  text-align: right;
}

.ai-msg.assistant {
  align-self: flex-start;
}

.ai-bubble {
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.ai-msg.user .ai-bubble {
  background: rgba(64, 158, 255, 0.18);
}

.ai-msg.assistant .ai-bubble {
  background: rgba(255, 255, 255, 0.06);
}

.ai-meta {
  margin-top: 4px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
}

.ai-input {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.ai-table {
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  padding: 12px;
}

.ai-table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.ai-table-title {
  font-size: 13px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.86);
}

.ai-table-actions {
  display: flex;
  gap: 8px;
}
</style>
