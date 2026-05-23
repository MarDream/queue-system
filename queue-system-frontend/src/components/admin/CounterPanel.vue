<template>
  <div class="counter-panel">
    <div class="toolbar">
      <div class="toolbar-row">
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
          style="width:220px"
          class="region-select"
          popper-class="region-select-popper"
        />
        <el-radio-group v-model="filterStatus" class="status-tabs">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="idle">空闲</el-radio-button>
          <el-radio-button value="busy">忙碌</el-radio-button>
          <el-radio-button value="paused">暂停</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="filterKeyword"
          placeholder="搜索窗口/编号/操作员"
          clearable
          style="width:260px"
          @keyup.enter="refresh"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button @click="resetFilters">
          <el-icon><RefreshRight /></el-icon> 重置
        </el-button>
        <el-button @click="refresh" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button-group class="expand-collapse-btns">
          <el-button @click="expandAllCounterRows">展开全部</el-button>
          <el-button @click="collapseAllCounterRows">收起层级</el-button>
        </el-button-group>
        <div class="toolbar-actions">
          <el-button :loading="downloadingTemplate" @click="showImportTemplateNotice">
            <el-icon class="btn-i"><Download /></el-icon> 批量导入模板
          </el-button>
          <el-button :loading="exportingCounters" @click="handleExport">
            <el-icon class="btn-i"><Download /></el-icon> 批量导出
          </el-button>
          <el-upload
            ref="importUploadRef"
            class="inline-upload"
            :auto-upload="false"
            :show-file-list="false"
            :disabled="importingCounters"
            accept=".xlsx,.xls"
            :on-change="handleImportFile"
          >
            <el-button type="success" :loading="importingCounters">
              <el-icon class="btn-i"><Upload /></el-icon> 批量导入
            </el-button>
          </el-upload>
          <el-button type="primary" @click="openCreate">
            <el-icon class="btn-i"><Plus /></el-icon> 新增窗口
          </el-button>
        </div>
      </div>

      <div class="kpi-row">
        <button class="kpi-card" type="button" @click="filterStatus = ''">
          <div class="kpi-value">{{ kpi.total }}</div>
          <div class="kpi-label">窗口总数</div>
        </button>
        <button class="kpi-card" type="button" @click="filterStatus = 'idle'">
          <div class="kpi-value kpi-ok">{{ kpi.idle }}</div>
          <div class="kpi-label">空闲</div>
        </button>
        <button class="kpi-card" type="button" @click="filterStatus = 'busy'">
          <div class="kpi-value kpi-warn">{{ kpi.busy }}</div>
          <div class="kpi-label">忙碌</div>
        </button>
        <button class="kpi-card" type="button" @click="filterStatus = 'paused'">
          <div class="kpi-value kpi-muted">{{ kpi.paused }}</div>
          <div class="kpi-label">暂停</div>
        </button>
      </div>

      <div class="view-summary">
        <span class="view-summary__mode">按区域层级展示</span>
        <span class="view-summary__text">当前显示 {{ visibleRegionCount }} 个区域分组，{{ visibleCounterCount }} 个窗口</span>
        <div class="view-summary__stats">
          <span class="view-stat">
            <span class="view-stat__label">总窗口</span>
            <strong class="view-stat__value">{{ kpi.total }}</strong>
          </span>
          <span class="view-stat">
            <span class="view-stat__label">空闲</span>
            <strong class="view-stat__value is-success">{{ kpi.idle }}</strong>
          </span>
          <span class="view-stat">
            <span class="view-stat__label">忙碌</span>
            <strong class="view-stat__value is-warning">{{ kpi.busy }}</strong>
          </span>
          <span class="view-stat">
            <span class="view-stat__label">暂停</span>
            <strong class="view-stat__value is-muted">{{ kpi.paused }}</strong>
          </span>
        </div>
      </div>
    </div>

    <div class="table-wrap">
      <div ref="tableContainerRef" class="table-container" @wheel="onWheelPage">
        <el-table
          ref="tableRef"
          :data="pagedCounterTreeData"
          v-loading="loading"
          row-key="rowKey"
          :tree-props="{ children: 'children' }"
          :expand-row-keys="expandedCounterRegionRowKeys"
          highlight-current-row
          table-layout="auto"
          :height="tableHeight"
          empty-text="暂无数据"
          :row-class-name="rowClassName"
          @selection-change="onSelectionChange"
          @row-click="onRowClick"
          style="width:100%"
        >
          <el-table-column type="selection" width="52" :reserve-selection="true" />
          <el-table-column label="窗口" min-width="180" class-name="counter-tree-cell">
            <template #default="{ row }">
              <!-- 区域行 -->
              <div v-if="row.rowType === REGION_ROW_TYPE" class="region-node">
                <div class="region-node__title">
                  <span class="region-node__icon" :class="{ 'is-virtual': row.isVirtualRegion }">
                    {{ row.isVirtualRegion ? '⌘' : '◈' }}
                  </span>
                  <span class="region-node__name">{{ row.name }}</span>
                  <button
                    v-if="hasCounterRegionChildren(row)"
                    type="button"
                    class="region-node__toggle"
                    :aria-label="isCounterRegionExpanded(row) ? '折叠区域' : '展开区域'"
                    @click.stop="toggleCounterRegionRow(row)"
                  >
                    <el-icon :class="['region-node__toggle-icon', { 'is-expanded': isCounterRegionExpanded(row) }]">
                      <ArrowRight />
                    </el-icon>
                  </button>
                  <el-tag
                    size="small"
                    effect="plain"
                    :type="row.isVirtualRegion ? (row.virtualTagType || 'info') : levelTagType(row.level)"
                  >
                    {{ row.isVirtualRegion ? row.virtualTagLabel : levelLabel(row.level) }}
                  </el-tag>
                </div>
              </div>
              <!-- 窗口行 -->
              <div v-else class="cell-title">
                <span class="cell-name">{{ row.name }}</span>
                <el-tag size="small" class="cell-tag">#{{ row.number }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.rowType === REGION_ROW_TYPE" class="region-summary">
                本级 {{ row.directCounterCount }} / 累计 {{ row.counterCount }}
              </span>
              <StatusBadge v-else :status="row.status" type="counter" />
            </template>
          </el-table-column>
          <el-table-column label="所属区域" min-width="180">
            <template #default="{ row }">
              <span v-if="row.rowType === COUNTER_ROW_TYPE" class="cell-muted">{{ row.fullPath || regionNameMap[row.regionId] || '—' }}</span>
              <span v-else class="cell-muted">{{ row.fullPath || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="业务" min-width="320">
            <template #default="{ row }">
              <span v-if="row.rowType === REGION_ROW_TYPE" class="cell-dash">—</span>
              <div v-else class="tag-list">
                <el-tag v-for="t in displayBusinessTags(row.businessTypes)" :key="t" size="small" type="info">
                  {{ t }}
                </el-tag>
                <span v-if="!row.businessTypes || row.businessTypes.length === 0" class="cell-muted">—</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作员" min-width="180">
            <template #default="{ row }">
              <span v-if="row.rowType === REGION_ROW_TYPE" class="cell-dash">—</span>
              <span v-else-if="row.operatorNames && row.operatorNames.length">{{ row.operatorNames.join(', ') }}</span>
              <span v-else class="cell-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right" align="center">
            <template #default="{ row }">
              <template v-if="row.rowType === COUNTER_ROW_TYPE">
                <el-button size="small" link type="primary" @click.stop="openEdit(row)">
                  <el-icon><Edit /></el-icon> 编辑
                </el-button>
                <el-button
                  v-if="row.status !== 'paused'"
                  size="small"
                  link
                  type="warning"
                  @click.stop="updateCounterStatus(row, 'paused')"
                >
                  暂停
                </el-button>
                <el-button
                  v-else
                  size="small"
                  link
                  type="success"
                  @click.stop="updateCounterStatus(row, 'idle')"
                >
                  恢复
                </el-button>
                <el-button size="small" link type="danger" @click.stop="handleDelete(row)">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </template>
              <span v-else class="cell-dash">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="table-footer">
        <div class="selection-info">
          <span v-if="selectedCounters.length">已选 {{ selectedCounters.length }} 项</span>
        </div>
        <div class="footer-actions">
          <el-button-group v-if="selectedCounters.length">
            <el-button size="small" @click="batchPause">批量暂停</el-button>
            <el-button size="small" @click="batchResume">批量恢复</el-button>
          </el-button-group>
          <el-button v-if="selectedCounters.length" size="small" @click="clearSelection">清空选择</el-button>
        </div>
      </div>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="filteredCounters.length"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          size="small"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" size="520px" :with-header="false" class="detail-drawer">
      <div class="drawer-header">
        <div class="drawer-title">
          <div class="drawer-name">{{ selectedCounter?.name || '窗口详情' }}</div>
          <div class="drawer-sub">
            <StatusBadge v-if="selectedCounter?.status" :status="selectedCounter.status" type="counter" />
            <span class="drawer-meta">#{{ selectedCounter?.number }}</span>
            <span class="drawer-meta">{{ selectedCounter?.regionId ? (regionNameMap[selectedCounter.regionId] || '—') : '—' }}</span>
          </div>
        </div>
        <div class="drawer-actions">
          <el-button v-if="selectedCounter" size="small" @click="openEdit(selectedCounter)">编辑</el-button>
          <el-button
            v-if="selectedCounter && selectedCounter.status !== 'paused'"
            size="small"
            type="warning"
            plain
            @click="updateCounterStatus(selectedCounter, 'paused')"
          >
            暂停
          </el-button>
          <el-button
            v-if="selectedCounter && selectedCounter.status === 'paused'"
            size="small"
            type="success"
            plain
            @click="updateCounterStatus(selectedCounter, 'idle')"
          >
            恢复
          </el-button>
          <el-button v-if="selectedCounter" size="small" type="danger" plain @click="handleDelete(selectedCounter)">
            删除
          </el-button>
        </div>
      </div>

      <div v-loading="statsLoading" class="drawer-body">
        <div class="stat-grid">
          <div class="stat-card">
            <span class="stat-value">{{ stats.todayServedCount ?? 0 }}</span>
            <span class="stat-label">今日已服务</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ stats.todayCalledCount ?? 0 }}</span>
            <span class="stat-label">今日呼叫</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ stats.todaySkippedCount ?? 0 }}</span>
            <span class="stat-label">今日过号</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ stats.avgServiceMinutes ?? 0 }}</span>
            <span class="stat-label">均服务时长(min)</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ stats.avgWaitMinutes ?? 0 }}</span>
            <span class="stat-label">均等待时长(min)</span>
          </div>
          <div class="stat-card hero-stat">
            <div class="hero-left">
              <div class="hero-value">{{ getDisplayTicketNo(stats.currentTicketNo) || '—' }}</div>
              <div class="hero-label">当前票号</div>
            </div>
            <div class="hero-right">
              <div class="hero-muted">{{ stats.currentBusinessTypeName || '—' }}</div>
              <div class="hero-label">当前业务</div>
            </div>
          </div>
        </div>

        <div class="section-title">排队情况</div>
        <div v-if="stats.waitingByBusiness && stats.waitingByBusiness.length" class="waiting-list">
          <div v-for="item in stats.waitingByBusiness" :key="item.businessTypeId" class="waiting-item">
            <span class="waiting-name">{{ item.businessTypeName }} ({{ item.prefix }})</span>
            <span class="waiting-count" :class="{ 'has-waiting': item.waitingCount > 0 }">{{ item.waitingCount }} 人等待</span>
          </div>
        </div>
        <div v-else class="empty-mini">暂无排队数据</div>

        <div class="section-title">最近服务记录</div>
        <el-table
          v-if="stats.recentServices && stats.recentServices.length"
          :data="stats.recentServices"
          size="small"
          empty-text="暂无服务记录"
          class="mini-table"
        >
          <el-table-column label="票号" width="90">
            <template #default="{ row }">
              <span class="mono">{{ getDisplayTicketNo(row.ticketNo) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="businessTypeName" label="业务" min-width="110" show-overflow-tooltip />
          <el-table-column label="客户" width="90" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.customerName || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <StatusBadge :status="row.status" type="ticket" />
            </template>
          </el-table-column>
          <el-table-column label="时长" width="80" align="right">
            <template #default="{ row }">
              {{ row.serviceMinutes ? row.serviceMinutes + ' min' : '—' }}
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-mini">暂无服务记录</div>
      </div>
    </el-drawer>

    <el-drawer v-model="formVisible" size="520px" :with-header="false">
      <div class="drawer-header">
        <div class="drawer-title">
          <div class="drawer-name">{{ isEdit ? '编辑窗口' : '新增窗口' }}</div>
          <div class="drawer-sub">
            <span class="drawer-muted">配置所属区域、业务类型与操作员</span>
          </div>
        </div>
        <div class="drawer-actions">
          <el-button size="small" @click="formVisible = false">关闭</el-button>
        </div>
      </div>
      <div class="drawer-body">
        <el-form ref="formRef" :model="form" :rules="formRules" label-width="92px">
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
              class="region-select"
              popper-class="region-select-popper"
              @change="onFormRegionChange"
            />
          </el-form-item>
          <el-form-item label="窗口编号" prop="number" required>
            <el-input-number v-model="form.number" :min="1" style="width:100%" />
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
              <el-option
                v-for="op in regionOperators"
                :key="op.id"
                :label="op.name + ' (' + op.username + ')'"
                :value="op.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="isEdit" label="状态">
            <el-radio-group v-model="form.status">
              <el-radio value="idle">空闲</el-radio>
              <el-radio value="paused">暂停</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <div class="drawer-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search, RefreshRight, Refresh, Download, Upload, ArrowRight } from '@element-plus/icons-vue'
import { StatusBadge } from './index.js'
import request from '../../api/index'
import { counterApi } from '../../api/admin'
import { getDisplayTicketNo } from '../../utils/ticketUtils'

// 行类型常量
const REGION_ROW_TYPE = 'region'
const COUNTER_ROW_TYPE = 'counter'
const PLATFORM_NODE_KEY = 'platform-counters'
const DETACHED_NODE_KEY = 'detached-counters'
const ROOT_REGION_KEY = 'root'

const list = ref([])
const regions = ref([])
const regionBusinessTypes = ref([])
const regionOperators = ref([])
const loading = ref(false)

const tableRef = ref(null)
const tableContainerRef = ref(null)
const tableHeight = ref(360)
let tableResizeObserver = null
const formRef = ref(null)
const importUploadRef = ref(null)
const downloadingTemplate = ref(false)
const importingCounters = ref(false)
const exportingCounters = ref(false)

const formRules = {
  regionId: [{ required: true, message: '请选择所属区域', trigger: 'change' }],
  number: [{ required: true, message: '请输入窗口编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入窗口名称', trigger: 'blur' }],
  businessTypeIds: [{ required: true, message: '请至少选择一个支持业务', trigger: 'change', type: 'array', min: 1 }]
}

const regionTree = computed(() => buildRegionTree(regions.value))

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

function filterRegionByName(query, node) {
  if (!query) return true
  const lowerQuery = query.toLowerCase()
  if (node.name && node.name.toLowerCase().includes(lowerQuery)) return true
  if (node.children && node.children.some(child => filterRegionByName(query, child))) return true
  return false
}

const filterRegionId = ref(null)
const filterStatus = ref('')
const filterKeyword = ref('')

// 区域树形视图展开状态
const expandedCounterRegionRowKeys = ref([])

const selectedId = ref(null)
const selectedCounters = ref([])
const selectedRows = ref([])

const stats = ref({})
const statsLoading = ref(false)
let pollingTimer = null
let statsFetchInFlight = false

const detailVisible = ref(false)
const formVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = ref({ regionId: null, number: null, name: '', operatorName: '', businessTypeIds: [], operatorIds: [], status: 'idle' })

const regionNameMap = computed(() => {
  const map = {}
  regions.value.forEach(r => { map[r.id] = r.name })
  return map
})

const countersInScope = computed(() => {
  let result = list.value
  if (filterRegionId.value) {
    const allowedIds = getDescendantRegionIds(filterRegionId.value, regions.value)
    allowedIds.add(filterRegionId.value)
    result = result.filter(c => allowedIds.has(c.regionId))
  }
  if (filterKeyword.value) {
    const k = String(filterKeyword.value).trim().toLowerCase()
    if (k) {
      result = result.filter(c => {
        const name = String(c.name || '').toLowerCase()
        const number = String(c.number || '').toLowerCase()
        const operators = (c.operatorNames && c.operatorNames.length) ? c.operatorNames.join(',').toLowerCase() : ''
        return name.includes(k) || number.includes(k) || operators.includes(k)
      })
    }
  }
  return result
})

const filteredCounters = computed(() => {
  let result = countersInScope.value
  if (filterStatus.value) result = result.filter(c => c.status === filterStatus.value)
  return result
})

// 区域快速查找映射
const regionMap = computed(() => {
  const map = new Map()
  regions.value.forEach(r => map.set(r.id, r))
  return map
})

// 区域children映射（用于构建树形结构）
const regionChildrenMap = computed(() => {
  const map = new Map()
  regions.value.forEach(r => {
    const parentKey = r.parentId || ROOT_REGION_KEY
    if (!map.has(parentKey)) map.set(parentKey, [])
    map.get(parentKey).push(r)
  })
  return map
})

// 构建窗口树形数据（按区域分组）
const counterTreeData = computed(() => {
  const countersByRegion = new Map()
  const platformCounters = []
  const detachedCounters = []
  const matchedCounters = filteredCounters.value.slice()

  matchedCounters.forEach(counter => {
    const regionId = counter.regionId
    if (regionId == null) {
      platformCounters.push(counter)
      return
    }
    if (!regionMap.value.has(regionId)) {
      detachedCounters.push(counter)
      return
    }
    if (!countersByRegion.has(regionId)) {
      countersByRegion.set(regionId, [])
    }
    countersByRegion.get(regionId).push(counter)
  })

  const buildCounterNode = (counter) => ({
    ...counter,
    rowKey: `counter-${counter.id}`,
    rowType: COUNTER_ROW_TYPE
  })

  const buildRegionNode = (region) => {
    const regionId = region.id
    if (regionId == null) return null

    const childRegionNodes = (regionChildrenMap.value.get(regionId) || [])
      .map(buildRegionNode)
      .filter(Boolean)
    const directCounters = (countersByRegion.get(regionId) || [])
      .map(buildCounterNode)
    const counterCount = directCounters.length + childRegionNodes.reduce((sum, node) => sum + node.counterCount, 0)

    if (counterCount === 0) return null

    return {
      rowKey: `region-${regionId}`,
      rowType: REGION_ROW_TYPE,
      isVirtualRegion: false,
      virtualTagLabel: '',
      virtualTagType: '',
      regionId,
      name: region.name,
      level: region.level,
      fullPath: getRegionPathText(regionId),
      directCounterCount: directCounters.length,
      counterCount,
      children: [...childRegionNodes, ...directCounters]
    }
  }

  const buildVirtualNode = (key, name, counters, tagLabel, tagType) => ({
    rowKey: key,
    rowType: REGION_ROW_TYPE,
    isVirtualRegion: true,
    virtualTagLabel: tagLabel,
    virtualTagType: tagType,
    name,
    level: '',
    fullPath: name,
    directCounterCount: counters.length,
    counterCount: counters.length,
    children: counters.map(buildCounterNode)
  })

  const selectedRegionId = filterRegionId.value
  const rootRegions = selectedRegionId != null
    ? [regionMap.value.get(selectedRegionId)].filter(Boolean)
    : (regionChildrenMap.value.get(ROOT_REGION_KEY) || [])

  const tree = rootRegions.map(buildRegionNode).filter(Boolean)

  if (selectedRegionId == null && platformCounters.length > 0) {
    tree.unshift(buildVirtualNode(PLATFORM_NODE_KEY, '平台级窗口', platformCounters, '平台级', 'danger'))
  }
  if (selectedRegionId == null && detachedCounters.length > 0) {
    tree.push(buildVirtualNode(DETACHED_NODE_KEY, '未匹配区域', detachedCounters, '异常区域', 'warning'))
  }

  return tree
})

// 获取区域路径文本
function getRegionPathText(regionId) {
  if (!regionId) return ''
  const region = regionMap.value.get(regionId)
  if (!region) return ''
  return region.name || ''
}

// 统计可见区域数量
function countRegionNodes(nodes) {
  return (nodes || []).reduce((sum, node) => {
    if (node.rowType !== REGION_ROW_TYPE) return sum
    return sum + 1 + countRegionNodes(node.children || [])
  }, 0)
}

const rootGroupTotal = computed(() => counterTreeData.value.length)
const visibleRegionCount = computed(() => countRegionNodes(counterTreeData.value))
const visibleCounterCount = computed(() => filteredCounters.value.length)

const currentPage = ref(1)
const pageSize = ref(10)

const pagedCounters = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredCounters.value.slice(start, end)
})

// 树形视图分页数据
const pagedCounterTreeData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return counterTreeData.value.slice(start, end)
})

let wheelAccumulatedDelta = 0
let wheelResetTimer = null
let lastWheelFlipAt = 0

function getTableScrollWrap() {
  return tableRef.value?.$el?.querySelector?.('.el-scrollbar__wrap') || null
}

function scrollTableToTop() {
  const wrap = getTableScrollWrap()
  if (wrap && typeof wrap.scrollTo === 'function') {
    wrap.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

function flipPage(delta) {
  const total = filteredCounters.value.length
  const maxPage = Math.max(1, Math.ceil(total / pageSize.value))
  const next = Math.min(maxPage, Math.max(1, currentPage.value + delta))
  if (next === currentPage.value) return
  currentPage.value = next
  nextTick(() => {
    scrollTableToTop()
  })
}

function onWheelPage(e) {
  if (loading.value) return
  const total = filteredCounters.value.length
  if (!total) return
  const maxPage = Math.max(1, Math.ceil(total / pageSize.value))
  if (maxPage <= 1) return

  const wrap = getTableScrollWrap()
  const canScrollInPage = !!wrap && wrap.scrollHeight > wrap.clientHeight + 2
  if (canScrollInPage) {
    const atTop = wrap.scrollTop <= 1
    const atBottom = wrap.scrollTop + wrap.clientHeight >= wrap.scrollHeight - 1
    if ((e.deltaY > 0 && !atBottom) || (e.deltaY < 0 && !atTop)) {
      return
    }
  }

  const now = Date.now()
  if (now - lastWheelFlipAt < 220) return

  wheelAccumulatedDelta += e.deltaY
  if (wheelResetTimer) clearTimeout(wheelResetTimer)
  wheelResetTimer = setTimeout(() => {
    wheelAccumulatedDelta = 0
  }, 120)

  if (Math.abs(wheelAccumulatedDelta) < 60) return
  lastWheelFlipAt = now
  const dir = wheelAccumulatedDelta > 0 ? 1 : -1
  wheelAccumulatedDelta = 0
  e.preventDefault?.()
  flipPage(dir)
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

const selectedCounter = computed(() => {
  return list.value.find(c => c.id === selectedId.value) || null
})

const kpi = computed(() => {
  const total = countersInScope.value.length
  const idle = countersInScope.value.filter(c => c.status === 'idle').length
  const busy = countersInScope.value.filter(c => c.status === 'busy').length
  const paused = countersInScope.value.filter(c => c.status === 'paused').length
  return { total, idle, busy, paused }
})

async function fetchList() {
  loading.value = true
  try {
    list.value = await counterApi.list()
    await syncTableSelection()
    if (detailVisible.value && selectedId.value) fetchStats(selectedId.value)
  } catch { list.value = [] }
  finally { loading.value = false }
}

async function fetchRegions() {
  try {
    regions.value = await request.get('/regions')
  } catch {}
}

async function fetchStats(counterId) {
  if (!counterId || statsFetchInFlight) return
  statsFetchInFlight = true
  statsLoading.value = true
  try {
    stats.value = await counterApi.getStats(counterId)
  } catch {
    stats.value = {}
  } finally {
    statsLoading.value = false
    statsFetchInFlight = false
  }
}

function startPolling() {
  if (document.hidden || !detailVisible.value || !selectedId.value) return
  stopPolling()
  pollingTimer = setInterval(() => {
    if (!document.hidden && detailVisible.value && selectedId.value) {
      fetchStats(selectedId.value)
    }
  }, 10000)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function resetFilters() {
  filterRegionId.value = null
  filterStatus.value = ''
  filterKeyword.value = ''
  currentPage.value = 1
  closeDetail()
  clearSelection()
}

function refresh() {
  fetchList()
}

async function showImportTemplateNotice() {
  try {
    await ElMessageBox.confirm(
      '1. 模板中的“支持业务类型”请填写系统当前已存在的业务类型名称。<br/>2. 多个业务类型可用中文逗号、英文逗号、分号或顿号分隔。<br/>3. 下载的模板会附带“当前业务类型”工作表，便于填写时核对。',
      '批量导入说明',
      {
        confirmButtonText: '下载模板',
        cancelButtonText: '取消',
        type: 'info',
        dangerouslyUseHTMLString: true
      }
    )
    await downloadTemplate()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message)
  }
}

async function downloadTemplate() {
  downloadingTemplate.value = true
  try {
    await counterApi.downloadImportTemplate()
    ElMessage.success('模板已开始下载')
  } catch (err) {
    ElMessage.error(err.message || '模板下载失败')
  } finally {
    downloadingTemplate.value = false
  }
}

function clearImportFiles() {
  importUploadRef.value?.clearFiles?.()
}

async function handleImportFile(file) {
  const rawFile = file?.raw
  if (!rawFile) {
    clearImportFiles()
    return
  }

  const filename = rawFile.name.toLowerCase()
  if (!filename.endsWith('.xlsx') && !filename.endsWith('.xls')) {
    ElMessage.warning('请上传 Excel 文件')
    clearImportFiles()
    return
  }

  importingCounters.value = true
  try {
    const result = await counterApi.importExcel(rawFile)
    ElMessage.success(`成功导入 ${result?.importedCount ?? 0} 个窗口`)
    await fetchList()
  } catch (err) {
    await ElMessageBox.alert(err.message || '批量导入失败', '批量导入失败', {
      type: 'error'
    })
  } finally {
    importingCounters.value = false
    clearImportFiles()
  }
}

async function handleExport() {
  exportingCounters.value = true
  try {
    const response = await request.get('/admin/counters/export', {
      responseType: 'blob'
    })
    const blob = new Blob([response], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `counters_export_${Date.now()}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('窗口配置导出成功')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exportingCounters.value = false
  }
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
    const promises = selectedCounters.value.map(id => {
      const counter = list.value.find(c => c.id === id)
      if (!counter) return Promise.resolve()
      return counterApi.update(id, {
        regionId: counter.regionId,
        number: counter.number,
        name: counter.name,
        operatorName: counter.operatorName,
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
    clearSelection()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message)
  }
}

async function openCreate() {
  isEdit.value = false
  form.value = {
    regionId: regions.value.length > 0 ? regions.value[0].id : null,
    number: null,
    name: '',
    operatorName: '',
    businessTypeIds: [],
    operatorIds: [],
    status: 'idle'
  }
  formVisible.value = true
  await loadRegionOptions(form.value.regionId, true)
  if (form.value.regionId && regionOperators.value.length > 0) {
    form.value.operatorIds = regionOperators.value.map(op => op.id)
  }
}

async function openEdit(row) {
  isEdit.value = true
  await loadRegionOptions(row.regionId, true)
  const availableBtIds = new Set(regionBusinessTypes.value.map(bt => bt.id))
  const availableOpIds = new Set(regionOperators.value.map(op => op.id))
  form.value = {
    id: row.id,
    regionId: row.regionId,
    number: row.number,
    name: row.name,
    operatorName: row.operatorName || '',
    businessTypeIds: (row.businessTypeIds || []).filter(id => availableBtIds.has(id)),
    operatorIds: (row.operatorIds || []).filter(id => availableOpIds.has(id)),
    status: row.status === 'paused' ? 'paused' : 'idle'
  }
  formVisible.value = true
}

async function loadRegionOptions(regionId, resetSelections) {
  if (resetSelections) {
    form.value.businessTypeIds = []
    form.value.operatorIds = []
  }
  if (!regionId) {
    regionBusinessTypes.value = []
    regionOperators.value = []
    return
  }
  try {
    regionBusinessTypes.value = await request.get('/admin/business-types', { params: { regionId } })
  } catch {
    regionBusinessTypes.value = []
  }
  try {
    regionOperators.value = await request.get('/admin/operators', { params: { regionId } })
  } catch {
    regionOperators.value = []
  }
}

async function onFormRegionChange(newRegionId) {
  await loadRegionOptions(newRegionId, true)
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
      await counterApi.update(form.value.id, form.value)
      ElMessage.success('更新成功')
    } else {
      await counterApi.create(form.value)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
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
    await counterApi.delete(row.id)
    ElMessage.success('删除成功')
    if (selectedId.value === row.id) {
      closeDetail()
    }
    await fetchList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message)
  }
}

async function updateCounterStatus(row, targetStatus) {
  try {
    await counterApi.update(row.id, {
      regionId: row.regionId,
      number: row.number,
      name: row.name,
      operatorName: row.operatorName,
      businessTypeIds: row.businessTypeIds || [],
      operatorIds: row.operatorIds || [],
      status: targetStatus
    })
    ElMessage.success(targetStatus === 'paused' ? '已暂停' : '已恢复')
    await fetchList()
  } catch (err) {
    ElMessage.error(err.message)
  }
}

// 树形视图辅助函数
function hasCounterRegionChildren(row) {
  return Array.isArray(row.children) && row.children.length > 0
}

function isCounterRegionExpanded(row) {
  return expandedCounterRegionRowKeys.value.includes(row.rowKey)
}

function toggleCounterRegionRow(row) {
  if (!hasCounterRegionChildren(row)) return

  const nextExpanded = !isCounterRegionExpanded(row)
  const keySet = new Set(expandedCounterRegionRowKeys.value)

  if (nextExpanded) {
    keySet.add(row.rowKey)
  } else {
    keySet.delete(row.rowKey)
  }

  expandedCounterRegionRowKeys.value = Array.from(keySet)
  tableRef.value?.toggleRowExpansion?.(row, nextExpanded)
}

function expandAllCounterRows() {
  const keys = collectCounterRegionExpandKeys(counterTreeData.value)
  expandedCounterRegionRowKeys.value = keys
  // 全部展开
  if (tableRef.value) {
    counterTreeData.value.forEach(node => {
      if (node.rowType === REGION_ROW_TYPE && hasCounterRegionChildren(node)) {
        tableRef.value.toggleRowExpansion(node, true)
      }
    })
  }
}

function collapseAllCounterRows() {
  expandedCounterRegionRowKeys.value = []
  if (tableRef.value) {
    counterTreeData.value.forEach(node => {
      if (node.rowType === REGION_ROW_TYPE && hasCounterRegionChildren(node)) {
        tableRef.value.toggleRowExpansion(node, false)
      }
    })
  }
}

function collectCounterRegionExpandKeys(nodes) {
  const keys = []
  nodes.forEach(node => {
    if (node.rowType === REGION_ROW_TYPE && hasCounterRegionChildren(node)) {
      keys.push(node.rowKey)
      if (node.children) {
        keys.push(...collectCounterRegionExpandKeys(node.children))
      }
    }
  })
  return keys
}

function rowClassName({ row }) {
  // 区域行
  if (row.rowType === REGION_ROW_TYPE) {
    return hasCounterRegionChildren(row) ? 'counter-region-row counter-region-row-expandable' : 'counter-region-row'
  }
  // 窗口行
  if (row.status === 'paused') return 'row-paused'
  if (row.status === 'busy') return 'row-busy'
  return ''
}

// 区域级别标签类型
function levelTagType(level) {
  const map = { city: 'primary', town: 'success', street: 'warning' }
  return map[level] || 'info'
}

// 区域级别标签文本
function levelLabel(level) {
  const map = { city: '市级', town: '镇/区级', street: '街道级' }
  return map[level] || level
}

function onSelectionChange(selection) {
  // 只处理窗口行的选择，忽略区域行
  selectedRows.value = (selection || []).filter(r => r.rowType === COUNTER_ROW_TYPE)
  selectedCounters.value = selectedRows.value.map(r => r.id)
}

function clearSelection() {
  selectedRows.value = []
  selectedCounters.value = []
  if (tableRef.value) tableRef.value.clearSelection()
}

async function syncTableSelection() {
  await nextTick()
  if (!tableRef.value) return
  tableRef.value.clearSelection()
  if (!selectedCounters.value.length) return
  const idSet = new Set(selectedCounters.value)
  // 遍历树形数据找到窗口行
  function syncRows(nodes) {
    nodes.forEach(row => {
      if (row.rowType === COUNTER_ROW_TYPE && idSet.has(row.id)) {
        tableRef.value.toggleRowSelection(row, true)
      }
      if (row.children) syncRows(row.children)
    })
  }
  syncRows(pagedCounterTreeData.value)
}

function closeDetail() {
  detailVisible.value = false
  selectedId.value = null
  stats.value = {}
}

function handleVisibilityChange() {
  if (document.hidden) {
    stopPolling()
    return
  }
  if (detailVisible.value && selectedId.value) {
    fetchStats(selectedId.value)
    startPolling()
  }
}

function onRowClick(row, column) {
  if (column && column.type === 'selection') return
  // 区域行不响应点击
  if (row.rowType === REGION_ROW_TYPE) return
  selectedId.value = row.id
  detailVisible.value = true
  fetchStats(row.id)
}

function displayBusinessTags(businessTypes) {
  if (!businessTypes || businessTypes.length === 0) return []
  return businessTypes.map(b => b.name)
}

onMounted(() => {
  fetchList()
  fetchRegions()
  document.addEventListener('visibilitychange', handleVisibilityChange)
  if (typeof ResizeObserver !== 'undefined') {
    tableResizeObserver = new ResizeObserver((entries) => {
      const entry = entries && entries[0]
      const h = entry?.contentRect?.height
      if (typeof h === 'number' && h > 0) tableHeight.value = Math.max(240, Math.floor(h))
    })
    if (tableContainerRef.value) tableResizeObserver.observe(tableContainerRef.value)
  }
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  if (tableResizeObserver) {
    tableResizeObserver.disconnect()
    tableResizeObserver = null
  }
})

watch(detailVisible, (open) => {
  if (open && selectedId.value) startPolling()
  if (!open) stopPolling()
})

watch([filterRegionId, filterStatus], () => {
  currentPage.value = 1
  closeDetail()
  clearSelection()
})

watch(filterKeyword, () => {
  currentPage.value = 1
  closeDetail()
})

watch([currentPage, pageSize, counterTreeData], () => {
  const total = counterTreeData.value.length
  const maxPage = Math.max(1, Math.ceil(total / pageSize.value))
  if (currentPage.value > maxPage) currentPage.value = maxPage
  syncTableSelection()
})

// 监听 counterTreeData 变化时同步展开状态
watch(counterTreeData, () => {
  nextTick(() => {
    const validKeys = new Set()
    counterTreeData.value.forEach(node => collectValidKeys(node, validKeys))
    expandedCounterRegionRowKeys.value = expandedCounterRegionRowKeys.value.filter(key => validKeys.has(key))
  })
}, { deep: true })

function collectValidKeys(node, keySet) {
  if (node.rowKey) keySet.add(node.rowKey)
  if (node.children) node.children.forEach(child => collectValidKeys(child, keySet))
}
</script>

<style scoped>
.counter-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* 优化 el-tree-select 样式 */
:deep(.region-select) {
  .el-select__wrapper {
    min-height: 32px;
    line-height: 1.5;
  }

  .el-select__wrapper.is-focused,
  .el-select__wrapper:hover {
    border-color: var(--border-input) !important;
    outline: none !important;
    box-shadow: none !important;
  }
}

:deep(.region-select-popper),
:deep(.el-select-dropdown.region-select-popper) {
  padding: 4px 0;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  background: var(--bg-card);
}

:deep(.region-select-popper .el-tree-node__content) {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  border-radius: 4px;
  margin: 0 4px;
  gap: 4px;
  transition: background 0.15s ease;
}

:deep(.region-select-popper .el-tree-node__content:hover) {
  background: var(--primary-light);
}

:deep(.region-select-popper .el-tree-node__label) {
  order: 1;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.region-select-popper .el-tree-node__expand-icon) {
  order: 2;
  width: 16px;
  min-width: 16px;
  flex-shrink: 0;
  height: 16px;
  margin-left: 4px;
  margin-right: 0;
  color: var(--text-secondary);
  transition: transform 0.2s ease;
}

:deep(.region-select-popper .el-tree-node__expand-icon.is-leaf) {
  display: none;
}

:deep(.region-select-popper .el-select-dropdown__item) {
  padding: 0;
  margin: 0;
}

:deep(.region-select-popper .el-tree) {
  padding: 4px 0;
}

:deep(.region-select-popper .el-tree-node__children) {
  padding-left: 16px;
}

.toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  background: #f7f8fa;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  margin-bottom: var(--sp-4);
}

.toolbar-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.toolbar-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.expand-collapse-btns {
  flex-shrink: 0;
}

.view-summary {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  margin-bottom: var(--sp-4);
  font-size: var(--text-sm);
}

.view-summary__mode {
  font-weight: 600;
  color: var(--text-primary);
}

.view-summary__text {
  color: var(--text-secondary);
}

.view-summary__stats {
  display: flex;
  gap: var(--sp-4);
  margin-left: auto;
}

.view-stat {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

.view-stat__label {
  color: var(--text-muted);
}

.view-stat__value {
  font-weight: 600;
  color: var(--text-primary);
}

.view-stat__value.is-success {
  color: var(--success);
}

.view-stat__value.is-warning {
  color: var(--warning);
}

.view-stat__value.is-muted {
  color: var(--text-muted);
}

.inline-upload {
  display: inline-flex;
}

:deep(.inline-upload .el-upload) {
  display: inline-flex;
}

.status-tabs :deep(.el-radio-button__inner) {
  padding: 8px 12px;
}

.kpi-row {
  display: flex;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.kpi-card {
  width: 140px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--sp-3);
  text-align: left;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}

.kpi-card:hover {
  border-color: var(--border-hi);
  background: var(--bg-raised);
}

.kpi-value {
  font-size: var(--text-2xl);
  font-weight: 800;
  font-family: var(--mono);
  color: var(--text-primary);
}

.kpi-ok {
  color: var(--success);
}

.kpi-warn {
  color: var(--warn);
}

.kpi-muted {
  color: var(--text-muted);
}

.kpi-label {
  margin-top: 6px;
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.table-wrap {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.table-container {
  flex: 1;
  min-height: 0;
}

.table-footer {
  margin-top: var(--sp-3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-2) var(--sp-3);
  background: #f7f8fa;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.pagination-row {
  margin-top: var(--sp-3);
  display: flex;
  justify-content: flex-end;
}

.selection-info {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.cell-title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.cell-name {
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-tag {
  flex-shrink: 0;
}

.cell-sub {
  margin-top: 4px;
}

.cell-muted {
  color: var(--text-secondary);
  font-size: var(--text-xs);
}

.cell-dash {
  color: var(--text-muted);
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: flex-start;
}

.tag-list :deep(.el-tag) {
  font-size: 12px;
}

/* 区域节点样式 */
.region-node {
  padding: 0;
}

.region-node__title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-height: 28px;
}

.region-node__icon {
  color: var(--primary);
  font-size: 14px;
  flex-shrink: 0;
  line-height: 1;
}

.region-node__icon.is-virtual {
  color: var(--danger);
}

.region-node__name {
  font-weight: 600;
  font-size: var(--text-base);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.region-node__toggle {
  background: none;
  border: none;
  padding: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast);
}

.region-node__toggle:hover {
  background: var(--bg-raised);
  color: var(--text-primary);
}

.region-node__toggle-icon {
  width: 16px;
  height: 16px;
  transition: transform var(--duration-fast) var(--ease-out);
}

.region-node__toggle-icon.is-expanded {
  transform: rotate(90deg);
}

.region-summary {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

/* 区域行样式 */
.counter-region-row {
  background: var(--bg-raised);
}

.counter-region-row td {
  background: var(--bg-raised) !important;
}

.counter-region-row:hover > td {
  background: var(--bg-raised) !important;
}

:deep(.counter-region-row .counter-tree-cell .cell) {
  display: flex;
  align-items: center;
}

:deep(.counter-region-row .counter-tree-cell .cell > .el-table__indent),
:deep(.counter-region-row .counter-tree-cell .cell > .el-table__placeholder),
:deep(.counter-region-row .counter-tree-cell .cell > .el-table__expand-icon) {
  display: none !important;
}

.drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: var(--sp-4);
  border-bottom: 1px solid var(--border);
}

.drawer-title {
  min-width: 0;
}

.drawer-name {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.1;
}

.drawer-sub {
  margin-top: 8px;
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  flex-wrap: wrap;
}

.drawer-meta {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.drawer-muted {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.drawer-actions {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  flex-wrap: wrap;
}

.drawer-body {
  padding: var(--sp-4);
}

.mini-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.mono {
  font-family: var(--mono);
  color: var(--primary);
}

.drawer-footer {
  margin-top: var(--sp-4);
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
}

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
  align-items: stretch;
}

.hero-left,
.hero-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.hero-stat {
  flex-direction: row;
  justify-content: space-between;
  padding: var(--sp-4);
}

.hero-value {
  font-size: var(--text-2xl);
  font-weight: 800;
  font-family: var(--mono);
  color: var(--primary);
}

.hero-muted {
  font-size: var(--text-sm);
  color: var(--text-primary);
  font-weight: 600;
  text-align: center;
}

.hero-label {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.stat-value {
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--primary);
  font-family: var(--mono);
}

.stat-label {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  text-align: center;
}

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

.empty-mini {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
  color: var(--text-muted);
  font-size: var(--text-sm);
  text-align: center;
}

:deep(.row-paused) {
  background: rgba(0, 0, 0, 0.02);
}

:deep(.row-busy) {
  background: rgba(250, 173, 20, 0.06);
}

@media (max-width: 768px) {
  .toolbar-row {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-actions {
    margin-left: 0;
    display: flex;
    justify-content: flex-end;
    flex-wrap: wrap;
  }

  .kpi-card {
    width: 100%;
  }

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .hero-stat {
    flex-direction: column;
    gap: var(--sp-3);
  }
}

.card-value {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
</style>
