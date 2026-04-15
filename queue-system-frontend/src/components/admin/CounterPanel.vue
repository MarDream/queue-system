<template>
  <div class="counter-panel">
    <!-- 顶部筛选栏 -->
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
          @change="onFilterChange"
        />
      </div>
      <div class="filter-group">
        <span class="filter-label">状态</span>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width:140px" @change="onFilterChange">
          <el-option label="空闲" value="idle" />
          <el-option label="忙碌" value="busy" />
          <el-option label="暂停" value="paused" />
        </el-select>
      </div>
      <div class="filter-actions">
        <span v-if="selectedCounters.length > 0" class="selection-count">
          已选 {{ selectedCounters.length }} 项
        </span>
        <el-button-group>
          <el-button size="small" :disabled="selectedCounters.length === 0" @click="batchPause">
            批量暂停
          </el-button>
          <el-button size="small" :disabled="selectedCounters.length === 0" @click="batchResume">
            批量恢复
          </el-button>
        </el-button-group>
        <el-divider direction="vertical" class="action-divider" />
        <el-button type="primary" size="small" @click="openCreate">
          <el-icon class="btn-i"><Plus /></el-icon> 新增窗口
        </el-button>
      </div>
    </div>

    <div class="split-layout">
      <!-- 左侧：卡片列表 -->
      <div class="left-pane">
        <div v-if="loading" class="loading-placeholder">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <div v-else-if="filteredCounters.length === 0" class="empty-state">
          <div class="empty-state-content">
            <el-icon class="empty-icon"><Plus /></el-icon>
            <span>暂无匹配的窗口数据</span>
            <el-button v-if="filterRegionId || filterStatus" size="small" link type="primary" @click="resetFilters">
              清除筛选
            </el-button>
          </div>
        </div>
        <div v-else class="card-list">
          <div
            v-for="counter in filteredCounters"
            :key="counter.id"
            class="counter-card"
            :class="{
              'card-selected': selectedCounters.includes(counter.id),
              'card-active': selectedId === counter.id
            }"
            tabindex="0"
            role="button"
            @click="selectCounter(counter)"
            @keydown.enter="selectCounter(counter)"
            @keydown.space.prevent="selectCounter(counter)"
          >
            <div class="card-header">
              <el-checkbox
                v-model="checkedMap[counter.id]"
                @click.stop
                @change="updateSelection"
                class="card-checkbox"
              />
              <span class="card-name">{{ counter.name }}</span>
              <StatusBadge :status="counter.status" type="counter" class="card-status" />
            </div>
            <div class="card-body">
              <div class="card-row">
                <span class="card-label">区域</span>
                <span class="card-value">{{ getRegionName(counter.regionId) }}</span>
              </div>
              <div class="card-row">
                <span class="card-label">操作员</span>
                <span class="card-value" :title="(counter.operatorNames && counter.operatorNames.length) ? counter.operatorNames.join(', ') : ''">{{ (counter.operatorNames && counter.operatorNames.length) ? counter.operatorNames.join(', ') : '—' }}</span>
              </div>
              <div class="card-row">
                <span class="card-label">业务</span>
                <span class="card-value" :title="(counter.businessTypes && counter.businessTypes.length) ? counter.businessTypes.map(b => b.name).join(', ') : ''">{{ (counter.businessTypes && counter.businessTypes.length) ? counter.businessTypes.map(b => b.name).join(', ') : '—' }}</span>
              </div>
            </div>
            <div class="card-footer">
              <el-button size="small" link type="primary" @click.stop="openEdit(counter)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button size="small" link type="danger" @click.stop="handleDelete(counter)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：详情面板 -->
      <div class="right-pane">
        <div v-if="!selectedId" class="detail-empty">
          <p>选择一个窗口查看详情</p>
        </div>
        <div v-else v-loading="statsLoading" class="detail-content">
          <div class="detail-header">
            <h3>{{ selectedCounter?.name }}</h3>
            <StatusBadge :status="selectedCounter?.status" type="counter" />
          </div>

          <!-- 今日统计 -->
          <div class="stat-grid">
            <div class="stat-card">
              <span class="stat-value">{{ stats.todayServedCount }}</span>
              <span class="stat-label">今日已服务</span>
            </div>
            <div class="stat-card">
              <span class="stat-value">{{ stats.todayCalledCount }}</span>
              <span class="stat-label">今日呼叫</span>
            </div>
            <div class="stat-card">
              <span class="stat-value">{{ stats.todaySkippedCount }}</span>
              <span class="stat-label">今日跳过</span>
            </div>
            <div class="stat-card">
              <span class="stat-value">{{ stats.avgServiceMinutes }}</span>
              <span class="stat-label">均服务时长(min)</span>
            </div>
            <div class="stat-card">
              <span class="stat-value">{{ stats.avgWaitMinutes }}</span>
              <span class="stat-label">均等待时长(min)</span>
            </div>
            <div class="stat-card hero-stat">
              <span class="stat-value">{{ stats.currentTicketNo || '—' }}</span>
              <span class="stat-label">当前票号</span>
            </div>
          </div>

          <!-- 当前排队情况 -->
          <div class="section-title">排队情况</div>
          <div v-if="stats.waitingByBusiness && stats.waitingByBusiness.length > 0" class="waiting-list">
            <div v-for="item in stats.waitingByBusiness" :key="item.businessTypeId" class="waiting-item">
              <span class="waiting-name">{{ item.businessTypeName }} ({{ item.prefix }})</span>
              <span class="waiting-count" :class="{ 'has-waiting': item.waitingCount > 0 }">
                {{ item.waitingCount }} 人等待
              </span>
            </div>
          </div>
          <div v-else class="empty-mini">暂无排队数据</div>

          <!-- 最近服务记录 -->
          <div class="section-title">最近服务记录</div>
          <div v-if="stats.recentServices && stats.recentServices.length > 0" class="recent-table-wrapper">
            <div class="recent-table">
              <div class="recent-header">
                <span class="rh">票号</span>
                <span class="rh">业务</span>
                <span class="rh">客户</span>
                <span class="rh">状态</span>
                <span class="rh">服务时长</span>
              </div>
              <div v-for="rec in stats.recentServices" :key="rec.ticketNo" class="recent-row">
                <span class="rd mono">{{ rec.ticketNo }}</span>
                <span class="rd">{{ rec.businessTypeName }}</span>
                <span class="rd">{{ rec.customerName || '—' }}</span>
                <span class="rd">
                  <StatusBadge :status="rec.status" type="ticket" />
                </span>
                <span class="rd">{{ rec.serviceMinutes ? rec.serviceMinutes + ' min' : '—' }}</span>
              </div>
            </div>
          </div>
          <div v-else class="empty-mini">暂无服务记录</div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" width="500px" draggable :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ isEdit ? '编辑窗口' : '新增窗口' }}</span>
          <el-button circle size="large" class="icon-close-btn" @click="dialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="所属区域" prop="regionId" required>
          <el-tree-select
            v-model="form.regionId"
            :data="regionTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="选择区域"
            check-strictly
            :render-after-expand="false"
            filterable
            :filter-method="filterRegionByName"
            style="width:100%"
            @change="onRegionChange"
          />
        </el-form-item>
        <el-form-item label="窗口编号" prop="number" required>
          <el-input-number v-model="form.number" :min="1" />
        </el-form-item>
        <el-form-item label="名称" prop="name" required>
          <el-input v-model="form.name" placeholder="如：1号窗口" />
        </el-form-item>
        <el-form-item label="支持业务" prop="businessTypeIds" required>
          <el-select v-model="form.businessTypeIds" multiple placeholder="选择业务类型" style="width:100%">
            <el-option v-for="bt in regionBusinessTypes" :key="bt.id" :label="bt.name" :value="bt.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作员">
          <el-select v-model="form.operatorIds" multiple placeholder="选择操作员" style="width:100%">
            <el-option v-for="op in regionOperators" :key="op.id" :label="op.name + ' (' + op.username + ')'" :value="op.id" />
          </el-select>
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Loading } from '@element-plus/icons-vue'
import { StatusBadge } from './index.js'
import request from '../../api/index'
import { counterApi } from '../../api/admin'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

// 列表数据
const list = ref([])
const regions = ref([])
const regionBusinessTypes = ref([])
const regionOperators = ref([])
const loading = ref(false)

// 表单引用
const formRef = ref(null)

// 表单验证规则
const formRules = {
  regionId: [{ required: true, message: '请选择所属区域', trigger: 'change' }],
  number: [{ required: true, message: '请输入窗口编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入窗口名称', trigger: 'blur' }],
  businessTypeIds: [{ required: true, message: '请至少选择一个支持业务', trigger: 'change', type: 'array', min: 1 }]
}

// 区域树形结构
const regionTree = computed(() => buildRegionTree(regions.value))

/**
 * 将扁平区域列表转换为树形结构
 * @param {Array} flatRegions - 扁平区域列表
 * @returns {Array} 树形结构
 */
function buildRegionTree(flatRegions) {
  if (!flatRegions || flatRegions.length === 0) return []

  const map = {}
  const roots = []

  // 初始化映射
  flatRegions.forEach(r => {
    map[r.id] = { ...r, children: [] }
  })

  // 构建树
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

/**
 * 区域树过滤方法（模糊匹配名称，同时匹配祖先/后代）
 * @param {string} query - 搜索关键词
 * @param {object} node - 树节点
 * @returns {boolean} 是否匹配
 */
function filterRegionByName(query, node) {
  if (!query) return true
  const lowerQuery = query.toLowerCase()
  // 检查当前节点
  if (node.name && node.name.toLowerCase().includes(lowerQuery)) return true
  // 检查子节点是否匹配
  if (node.children && node.children.some(child => filterRegionByName(query, child))) return true
  return false
}

// 筛选
const filterRegionId = ref(null)
const filterStatus = ref(null)

// 选择
const selectedId = ref(null)
const selectedCounters = ref([])
const checkedMap = ref({})

// 详情
const stats = ref({})
const statsLoading = ref(false)
let pollingTimer = null

// 对话框
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = ref({ regionId: null, number: null, name: '', businessTypeIds: [], operatorIds: [] })

// 计算属性
const filteredCounters = computed(() => {
  let result = list.value
  if (filterRegionId.value) {
    const allowedIds = getDescendantRegionIds(filterRegionId.value, regions.value)
    allowedIds.add(filterRegionId.value)
    result = result.filter(c => allowedIds.has(c.regionId))
  }
  if (filterStatus.value) {
    result = result.filter(c => c.status === filterStatus.value)
  }
  return result
})

/**
 * 获取指定区域的所有后代 ID（递归）
 * @param {number} parentId - 父区域 ID
 * @param {Array} flatRegions - 扁平区域列表
 * @returns {Set<number>} 后代区域 ID 集合
 */
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

const selectedCounter = computed(() => {
  return list.value.find(c => c.id === selectedId.value) || null
})

async function fetchList() {
  loading.value = true
  try {
    const params = {}
    if (!userStore.isSuperAdmin) {
      params.userId = Number(userStore.userId) || undefined
    }
    list.value = await request.get('/admin/counters', { params })
    // 保持已勾选的状态
    const newMap = {}
    list.value.forEach(c => { newMap[c.id] = checkedMap.value[c.id] || false })
    checkedMap.value = newMap
    // 如果当前选中的窗口还在列表中，刷新其详情
    if (selectedId.value) {
      fetchStats(selectedId.value)
    }
  } catch { list.value = [] }
  finally { loading.value = false }
}

async function fetchRegions() {
  try {
    const params = {}
    if (!userStore.isSuperAdmin) {
      params.userId = Number(userStore.userId) || undefined
    }
    regions.value = await request.get('/regions', { params })
  } catch {}
}

async function fetchStats(counterId) {
  statsLoading.value = true
  try {
    stats.value = await counterApi.getStats(counterId)
  } catch {
    stats.value = {}
  } finally {
    statsLoading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollingTimer = setInterval(() => {
    if (selectedId.value) {
      fetchStats(selectedId.value)
    }
  }, 5000)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// 当选中窗口时开始轮询，取消选中时停止
watch(selectedId, (newId) => {
  newId ? startPolling() : stopPolling()
})

function selectCounter(counter) {
  selectedId.value = counter.id
  fetchStats(counter.id)
}

function onFilterChange() {
  selectedId.value = null
  stats.value = {}
}

function resetFilters() {
  filterRegionId.value = null
  filterStatus.value = null
  selectedId.value = null
  stats.value = {}
}

function updateSelection() {
  selectedCounters.value = Object.entries(checkedMap.value)
    .filter(([_, checked]) => checked)
    .map(([id]) => Number(id))
}

async function batchPause() {
  await batchToggleStatus('paused')
}

async function batchResume() {
  await batchToggleStatus('idle')
}

async function batchToggleStatus(targetStatus) {
  if (selectedCounters.value.length === 0) return
  const actionText = targetStatus === 'paused' ? '暂停' : '恢复'
  try {
    await ElMessageBox.confirm(`确认将选中的 ${selectedCounters.value.length} 个窗口${actionText}吗？`, '批量操作')
    // 逐个调用更新接口，使用 allSettled 处理部分失败
    const promises = selectedCounters.value.map(id => {
      const counter = list.value.find(c => c.id === id)
      if (!counter) return Promise.resolve()
      return request.put(`/admin/counters/${id}`, {
        regionId: counter.regionId,
        number: counter.number,
        name: counter.name,
        businessTypeIds: counter.businessTypeIds || [],
        operatorIds: counter.operatorIds || [],
        status: targetStatus
      })
    })
    const results = await Promise.allSettled(promises)
    const success = results.filter(r => r.status === 'fulfilled').length
    const failed = results.length - success
    if (failed > 0) {
      ElMessage.warning(`批量${actionText}：${success} 成功，${failed} 失败`)
    } else {
      ElMessage.success(`批量${actionText}成功`)
    }
    await fetchList()
    selectedCounters.value = []
    checkedMap.value = {}
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message)
  }
}

// 对话框相关
async function openCreate() {
  isEdit.value = false
  form.value = {
    regionId: regions.value.length > 0 ? regions.value[0].id : null,
    number: null, name: '', businessTypeIds: [], operatorIds: []
  }
  dialogVisible.value = true
  await onRegionChange()
  if (form.value.regionId && regionOperators.value.length > 0) {
    form.value.operatorIds = regionOperators.value.map(op => op.id)
  }
}

async function openEdit(row) {
  isEdit.value = true
  // 先加载区域关联数据
  form.value.regionId = row.regionId
  await onRegionChange()
  // 只保留区域可选范围内的业务类型和操作员，避免 el-select 显示 raw value
  const availableBtIds = new Set(regionBusinessTypes.value.map(bt => bt.id))
  const availableOpIds = new Set(regionOperators.value.map(op => op.id))
  form.value = {
    id: row.id,
    regionId: row.regionId,
    number: row.number,
    name: row.name,
    businessTypeIds: (row.businessTypeIds || []).filter(id => availableBtIds.has(id)),
    operatorIds: (row.operatorIds || []).filter(id => availableOpIds.has(id)),
  }
  dialogVisible.value = true
}

async function onRegionChange() {
  form.value.businessTypeIds = []
  form.value.operatorIds = []
  if (!form.value.regionId) {
    regionBusinessTypes.value = []
    regionOperators.value = []
    return
  }
  try {
    regionBusinessTypes.value = await request.get('/admin/business-types', { params: { regionId: form.value.regionId } })
  } catch {
    regionBusinessTypes.value = []
  }
  try {
    regionOperators.value = await request.get('/admin/operators', { params: { regionId: form.value.regionId } })
  } catch {
    regionOperators.value = []
  }
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await request.put(`/admin/counters/${form.value.id}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/admin/counters', form.value)
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

async function handleDelete(row) {
  if (row.status !== 'idle') {
    ElMessage.warning('窗口当前非空闲状态，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `此操作将删除窗口 "${row.name}" 及其关联的业务类型/操作员绑定关系。确认继续吗？`,
      '确认删除',
      { type: 'warning' }
    )
    await request.delete(`/admin/counters/${row.id}`)
    ElMessage.success('删除成功')
    if (selectedId.value === row.id) {
      selectedId.value = null
      stats.value = {}
    }
    await fetchList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message)
  }
}

function getRegionName(regionId) {
  const region = regions.value.find(r => r.id === regionId)
  return region ? region.name : '—'
}

onMounted(() => {
  fetchList()
  fetchRegions()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.counter-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  margin-bottom: var(--sp-4);
  flex-wrap: wrap;
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
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.action-divider {
  margin: 0 var(--sp-1);
  border-color: var(--border);
}

.selection-count {
  font-size: var(--text-sm);
  color: var(--accent);
  font-weight: 600;
  margin-right: var(--sp-2);
  white-space: nowrap;
}

/* 左右分栏 */
.split-layout {
  display: flex;
  gap: var(--sp-4);
  flex: 1;
  min-height: 0;
}

.left-pane {
  width: 380px;
  flex-shrink: 0;
  overflow-y: auto;
  padding-right: var(--sp-2);
}

.right-pane {
  flex: 1;
  min-width: 0;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow-y: auto;
  padding: var(--sp-4);
}

/* 卡片列表 */
.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.counter-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--sp-3);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  position: relative;
}

.counter-card:hover {
  border-color: var(--border-hi);
  background: var(--bg-raised);
}

.card-active {
  border-color: var(--accent);
  box-shadow: var(--glow-accent);
}

.card-selected {
  border-color: var(--accent-dim);
  border-left: 3px solid var(--accent);
}

.card-selected.card-active {
  border-color: var(--accent);
  border-left: 3px solid var(--accent);
  box-shadow: var(--glow-accent);
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-2);
}

.card-checkbox {
  flex-shrink: 0;
}

.card-name {
  flex: 1;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-status {
  flex-shrink: 0;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: var(--sp-1);
  margin-bottom: var(--sp-2);
}

.card-row {
  display: flex;
  align-items: center;
  font-size: var(--text-sm);
}

.card-label {
  color: var(--text-secondary);
  width: 50px;
  flex-shrink: 0;
}

.card-value {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
  border-top: 1px solid var(--border);
  padding-top: var(--sp-2);
}

/* 详情面板 */
.detail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  font-size: var(--text-base);
}

.detail-header {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
  padding-bottom: var(--sp-3);
  border-bottom: 1px solid var(--border);
}

.detail-header h3 {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-primary);
}

/* 统计网格 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--sp-3);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-1);
}

.hero-stat {
  grid-column: 1 / -1;
  flex-direction: row;
  justify-content: center;
  gap: var(--sp-3);
  padding: var(--sp-4);
}

.hero-stat .stat-value {
  font-size: var(--text-2xl);
}

.stat-value {
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--accent);
  font-family: var(--mono);
}

.stat-label {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  text-align: center;
}

/* 区域标题 */
.section-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: var(--sp-2);
  margin-top: var(--sp-4);
  padding-bottom: var(--sp-1);
  border-bottom: 1px solid var(--border);
}

/* 排队列表 */
.waiting-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.waiting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: var(--sp-2) var(--sp-3);
}

.waiting-name {
  font-size: var(--text-sm);
  color: var(--text-primary);
}

.waiting-count {
  font-size: var(--text-sm);
  color: var(--text-muted);
  font-family: var(--mono);
}

.has-waiting {
  color: var(--warn);
}

/* 最近服务表格 */
.recent-table-wrapper {
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.recent-table {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.recent-header,
.recent-row {
  display: grid;
  grid-template-columns: 80px 1fr 80px 80px 80px;
  gap: 0;
  padding: var(--sp-2) var(--sp-3);
  background: var(--bg-card);
  font-size: var(--text-sm);
}

.recent-header {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--bg-panel);
  color: var(--text-secondary);
  font-weight: 600;
  font-size: var(--text-xs);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.rh, .rd {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rd {
  color: var(--text-primary);
}

.rd.mono {
  font-family: var(--mono);
  color: var(--accent);
}

/* 空状态 */
.loading-placeholder,
.empty-state,
.empty-mini {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-8) var(--sp-4);
  color: var(--text-muted);
  font-size: var(--text-sm);
  text-align: center;
}

.loading-placeholder {
  gap: var(--sp-2);
}

.empty-state-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-3);
}

.empty-state .empty-icon {
  font-size: var(--text-3xl);
  color: var(--border-hi);
  margin-bottom: var(--sp-2);
}

.empty-mini {
  padding: var(--sp-4);
}

/* 响应式 */
@media (max-width: 768px) {
  .split-layout {
    flex-direction: column;
  }

  .left-pane {
    width: 100%;
    max-height: 300px;
  }

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
    gap: var(--sp-2);
  }

  .filter-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}
</style>
