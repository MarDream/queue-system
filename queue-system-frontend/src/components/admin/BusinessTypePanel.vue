<template>
  <div class="biz-panel">
    <section class="group-toolbar">
      <div class="group-toolbar__intro">
        <div class="section-title">业务分组</div>
        <div class="section-subtitle">
          业务类型可先独立新增，后续再从分组中选择未分组业务统一纳管。列表按分组层级展示，默认折叠。
        </div>
      </div>

      <div class="group-toolbar__meta">
        <div class="group-summary">
          <span>分组 {{ groups.length }} 个</span>
          <span>未分组业务 {{ ungroupedCount }} 个</span>
        </div>
        <el-button type="primary" @click="openGroupDialog()">
          <el-icon class="btn-i"><FolderAdd /></el-icon> 新增分组
        </el-button>
      </div>
    </section>

    <div class="panel-actions">
      <div class="panel-actions__left">
        <el-button type="primary" @click="openCreate">
          <el-icon class="btn-i"><Plus /></el-icon> 新增业务类型
        </el-button>
      </div>

      <div class="panel-actions__right">
        <el-select v-model="selectedGroupFilter" style="width: 220px">
          <el-option
            v-for="option in groupFilterOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>

        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索业务名称、前缀、描述"
          style="width: 280px"
        />
      </div>
    </div>

    <el-table
      :key="tableKey"
      ref="tableRef"
      :data="treeData"
      v-loading="loading"
      row-key="rowKey"
      :tree-props="{ children: 'children' }"
      :expand-row-keys="expandedGroupRowKeys"
      :default-expand-all="false"
      :row-class-name="rowClassName"
      empty-text="暂无数据！"
    >
      <el-table-column label="名称" min-width="200">
        <template #default="{ row }">
          <div v-if="row.rowType === GROUP_ROW_TYPE" class="group-node">
            <span class="group-node__name">{{ row.name }}</span>
            <button
              type="button"
              class="group-node__toggle"
              :aria-label="isGroupExpanded(row) ? '折叠分组' : '展开分组'"
              @click.stop="toggleGroupRow(row)"
            >
              <el-icon :class="['group-node__toggle-icon', { 'is-expanded': isGroupExpanded(row) }]">
                <ArrowRight />
              </el-icon>
            </button>
            <el-tag size="small" effect="plain" type="info" round>{{ row.childCount }} 个业务</el-tag>
          </div>
          <div v-else class="biz-name-cell">
            <span class="drag-handle" title="拖拽排序">☰</span>
            <span class="biz-name">{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="prefix" label="前缀" width="90">
        <template #default="{ row }">
          <span v-if="row.rowType === BIZ_ROW_TYPE" class="mono text-accent">{{ row.prefix }}</span>
          <span v-else class="cell-muted">—</span>
        </template>
      </el-table-column>

      <el-table-column prop="dailyAppointmentLimit" label="每日预约限额" width="120" align="center">
        <template #default="{ row }">
          <span v-if="row.rowType === BIZ_ROW_TYPE">{{ row.dailyAppointmentLimit }}</span>
          <span v-else class="cell-muted">—</span>
        </template>
      </el-table-column>

      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.rowType === BIZ_ROW_TYPE">{{ row.description || '' }}</span>
          <span v-else class="cell-muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="排序" width="80">
        <template #default="{ row }">
          <span v-if="row.rowType === BIZ_ROW_TYPE">{{ row.sortOrder }}</span>
          <span v-else class="cell-muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <template v-if="row.rowType === BIZ_ROW_TYPE">
            <span :class="row.isEnabled ? 'text-success' : 'text-danger'">
              {{ row.isEnabled ? '启用' : '停用' }}
            </span>
          </template>
          <span v-else class="cell-muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <template v-if="row.rowType === GROUP_ROW_TYPE">
            <el-tooltip content="编辑分组" placement="top">
              <el-button size="small" link type="primary" @click="openGroupDialogById(row.groupId)">
                <el-icon><Edit /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="删除分组" placement="top">
              <el-button size="small" link type="danger" @click="handleDeleteGroup(row)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-tooltip>
          </template>
          <template v-else>
            <el-tooltip content="编辑业务" placement="top">
              <el-button size="small" link type="primary" @click="openEdit(row)">
                <el-icon><Edit /></el-icon>
              </el-button>
            </el-tooltip>

            <el-tooltip content="编辑分组" placement="top" :disabled="!row.groupId">
              <el-button size="small" link type="primary" :disabled="!row.groupId" @click="openGroupDialogById(row.groupId)">
                分组
              </el-button>
            </el-tooltip>

            <el-tooltip content="删除业务" placement="top">
              <el-button size="small" link type="danger" @click="handleDelete(row)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-tooltip>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      width="560px"
      draggable
      top="8vh"
      :show-close="false"
      :close-on-click-modal="false"
      custom-class="biz-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ isEdit ? '编辑业务类型' : '新增业务类型' }}</span>
          <el-button circle size="large" class="icon-close-btn" @click="dialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <path d="M15 9l-6 6M9 9l6 6" />
            </svg>
          </el-button>
        </div>
      </template>

      <div class="biz-form">
        <div class="form-field">
          <label class="field-label">当前类别</label>
          <div class="group-hint">
            <el-tag type="info" effect="plain">{{ form.groupName || '未分组' }}</el-tag>
            <span>分组归属在“业务分组”中统一维护，新增业务类型时可先不分组。</span>
          </div>
        </div>

        <div class="form-field">
          <label class="field-label">业务名称</label>
          <div class="input-with-prefix">
            <div class="input-with-prefix__main">
              <el-input v-model="form.name" @input="onNameInput" placeholder="如：个人业务" />
              <div class="field-meta">
                <span v-if="form.name.length > 0" class="char-count">{{ form.name.length }}/50</span>
              </div>
            </div>

            <div class="prefix-badge">
              <span class="prefix-badge-label">前缀</span>
              <el-input v-model="form.prefix" @input="onPrefixInput" placeholder="A" />
            </div>
          </div>
        </div>

        <div class="form-field">
          <label class="field-label">描述</label>
          <el-input
            v-model="form.description"
            type="textarea"
            @input="onDescriptionInput"
            placeholder="简要说明此业务类型的用途"
            :rows="3"
            resize="none"
          />
          <div class="field-meta">
            <span v-if="form.description.length > 0" class="char-count">{{ form.description.length }}/200</span>
          </div>
        </div>

        <div class="settings-strip">
          <div class="setting-item">
            <span class="setting-label">每日预约限额</span>
            <el-input-number v-model="form.dailyAppointmentLimit" :min="1" :max="500" controls-position="right" size="small" />
          </div>
          <div class="setting-divider"></div>
          <div class="setting-item">
            <span class="setting-label">排序</span>
            <el-input-number v-model="form.sortOrder" :min="0" :max="999" controls-position="right" size="small" />
          </div>
          <div class="setting-divider"></div>
          <div class="setting-item setting-item-switch">
            <span class="setting-label">状态</span>
            <el-switch v-model="form.isEnabled" size="small" />
            <span class="setting-value">{{ form.isEnabled ? '启用' : '停用' }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="groupDialogVisible"
      width="520px"
      draggable
      top="10vh"
      :show-close="false"
      :close-on-click-modal="false"
      custom-class="biz-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ editingGroup?.id ? '编辑业务分组' : '新增业务分组' }}</span>
          <el-button circle size="large" class="icon-close-btn" @click="groupDialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10" />
              <path d="M15 9l-6 6M9 9l6 6" />
            </svg>
          </el-button>
        </div>
      </template>

      <div class="biz-form">
        <div class="form-field">
          <label class="field-label">分组名称</label>
          <el-input v-model="groupForm.name" maxlength="50" show-word-limit placeholder="请输入分组名称" />
        </div>

        <div class="form-field">
          <label class="field-label">分组排序</label>
          <el-input-number v-model="groupForm.sortOrder" :min="0" :max="999" controls-position="right" />
        </div>

        <div class="form-field">
          <label class="field-label">纳入业务类型</label>
          <el-select
            v-model="groupForm.businessTypeIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可选择未分组业务，编辑时也可维护当前分组成员"
            style="width: 100%"
          >
            <el-option
              v-for="item in availableGroupBusinessOptions"
              :key="item.id"
              :label="`${item.name}（${item.prefix}）`"
              :value="item.id"
            >
              <div class="group-option">
                <span>{{ item.name }}</span>
                <span class="group-option__meta">{{ item.groupName || '未分组' }}</span>
              </div>
            </el-option>
          </el-select>
          <div class="field-meta">
            <span class="helper-text">仅允许纳入未分组业务；编辑分组时可同步移出当前成员。</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button v-if="editingGroup?.id" type="danger" plain @click="handleDeleteGroup(editingGroup)">删除分组</el-button>
          <span class="dialog-footer__spacer"></span>
          <el-button @click="groupDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="groupSaving" @click="handleSaveGroup">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Delete, Edit, FolderAdd, Plus } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { businessTypeApi, businessTypeGroupApi } from '../../api/admin'

const ALL_GROUP_KEY = '__all__'
const UNGROUPED_GROUP_KEY = '__ungrouped__'
const GROUP_ROW_TYPE = 'group'
const BIZ_ROW_TYPE = 'biz'

const list = ref([])
const groups = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const groupDialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const groupSaving = ref(false)
const keyword = ref('')
const selectedGroupFilter = ref(ALL_GROUP_KEY)
const tableRef = ref(null)
const tableKey = ref(0)
const editingGroup = ref(null)
const expandedGroupRowKeys = ref([])

const form = ref(createEmptyBusinessForm())
const groupForm = ref(createEmptyGroupForm())

const PINYIN_MAP = {
  '个': 'G', '人': 'R', '业': 'Y', '务': 'W', '对': 'D', '公': 'G', '企': 'Q', '社': 'S',
  '医': 'Y', '疗': 'L', '保': 'B', '险': 'X', '住': 'Z', '房': 'F', '积': 'J', '金': 'J',
  '老': 'L', '养': 'Y', '退': 'T', '休': 'X', '户': 'H', '口': 'K', '登': 'D', '记': 'J',
  '婚': 'H', '姻': 'Y', '生': 'S', '育': 'Y', '就': 'J', '失': 'S', '工': 'G', '商': 'S',
  '税': 'S', '水': 'S', '电': 'D', '燃': 'R', '气': 'Q', '暖': 'N', '物': 'W', '财': 'C',
  '政': 'Z', '法': 'F', '律': 'L', '诉': 'S', '讼': 'S', '仲': 'Z', '裁': 'C', '证': 'Z',
  '档': 'D', '案': 'A', '签': 'Q', '照': 'Z', '批': 'P', '文': 'W', '许': 'X', '可': 'K',
  '认': 'R', '审': 'S', '核': 'H', '备': 'B', '投': 'T', '建': 'J', '议': 'Y', '咨': 'Z',
  '询': 'X', '导': 'D', '办': 'B', '理': 'L', '缴': 'J', '费': 'F', '查': 'C', '打': 'D',
  '印': 'Y', '复': 'F', '制': 'Z', '传': 'C', '真': 'Z', '扫': 'S', '描': 'M', '快': 'K',
  '递': 'D', '邮': 'Y', '寄': 'J', '存': 'C', '取': 'Q', '贷': 'D', '款': 'K', '结': 'J',
  '算': 'S', '汇': 'H', '转': 'Z', '账': 'Z', '现': 'X', '支': 'Z', '票': 'P', '信': 'X',
  '用': 'Y', '卡': 'K', '基': 'J', '础': 'C', '设': 'S', '施': 'S', '环': 'H', '境': 'J',
  '卫': 'W', '计': 'J', '划': 'H', '量': 'L', '质': 'Z', '检': 'J', '安': 'A', '全': 'Q',
  '消': 'X', '防': 'F', '交': 'J', '通': 'T', '运': 'Y', '输': 'S', '化': 'H', '体': 'T',
  '旅': 'L', '游': 'Y', '景': 'J', '点': 'D', '门': 'M', '挂': 'G', '号': 'H', '专': 'Z',
  '家': 'J', '诊': 'Z', '手': 'S', '术': 'S', '验': 'Y', '注': 'Z', '射': 'S', '抽': 'C',
  '血': 'X', '拍': 'P', '片': 'P', '心': 'X', '图': 'T', '脑': 'N', '超': 'C', '声': 'S',
  '彩': 'C', '磁': 'C', '共': 'G', '振': 'Z', '行': 'X', '执': 'Z', '城': 'G', '管': 'G',
  '林': 'L', '农': 'N', '牧': 'M', '渔': 'Y', '利': 'L', '国': 'G', '土': 'T', '规': 'G',
  '地': 'D', '出': 'C', '让': 'R', '卖': 'M', '招': 'Z', '标': 'B', '采': 'C', '购': 'G',
  '资': 'Z', '产': 'C', '易': 'Y', '商': 'S', '引': 'Y', '项': 'X', '目': 'M', '准': 'Z',
  '评': 'P', '能': 'N', '职': 'Z', '收': 'S', '特': 'T', '困': 'K', '助': 'Z', '低': 'D',
  '五': 'W', '救': 'J', '残': 'C', '疾': 'J', '联': 'L', '系': 'X', '役': 'Y', '军': 'J',
  '优': 'U', '抚': 'F', '烈': 'L', '属': 'S', '伤': 'S', '复': 'F', '员': 'Y', '伍': 'W',
  '士': 'S', '兵': 'B', '培': 'P', '训': 'X', '指': 'Z', '介': 'J', '绍': 'S', '推': 'T',
  '荐': 'J', '创': 'C', '额': 'E', '担': 'D', '贴': 'T', '息': 'X', '奖': 'J', '学': 'X',
  '源': 'Y', '毕': 'B', '到': 'D', '迁': 'Q', '移': 'Y', '落': 'L', '集': 'J', '才': 'C',
  '进': 'J', '事': 'S', '代': 'D', '托': 'T', '管': 'G', '党': 'D', '织': 'Z', '关': 'G',
  '团': 'T', '少': 'S', '先': 'X', '队': 'D', '光': 'G', '荣': 'R', '离': 'L', '绿': 'L',
  '色': 'S', '道': 'D', '爱': 'A', '弱': 'R', '病': 'B', '孕': 'Y', '依': 'Y', '窗': 'C',
  '站': 'Z', '式': 'S', '综': 'Z', '合': 'H', '一': 'Y'
}

const sortedGroups = computed(() => {
  return [...groups.value].sort((left, right) => {
    const leftSort = left.sortOrder ?? 0
    const rightSort = right.sortOrder ?? 0
    if (leftSort !== rightSort) return leftSort - rightSort
    return (left.id ?? 0) - (right.id ?? 0)
  })
})

const groupFilterOptions = computed(() => {
  const options = [
    { value: ALL_GROUP_KEY, label: `全部类别（${list.value.length}）` }
  ]

  if (ungroupedCount.value > 0) {
    options.push({ value: UNGROUPED_GROUP_KEY, label: `未分组（${ungroupedCount.value}）` })
  }

  sortedGroups.value.forEach(group => {
    options.push({
      value: group.id,
      label: `${group.name}（${getGroupBusinessCount(group.id)}）`
    })
  })

  return options
})

const ungroupedCount = computed(() => list.value.filter(item => !item.groupId).length)

const filteredList = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()

  return list.value.filter(item => {
    const itemGroupKey = resolveGroupKey(item)
    const groupMatched = selectedGroupFilter.value === ALL_GROUP_KEY || itemGroupKey === selectedGroupFilter.value
    if (!groupMatched) return false

    if (!normalizedKeyword) return true

    return [item.name, item.prefix, item.groupName, item.description]
      .filter(Boolean)
      .some(value => String(value).toLowerCase().includes(normalizedKeyword))
  })
})

const treeData = computed(() => {
  const result = []

  const buildBizNode = (item) => ({
    ...item,
    rowKey: `biz-${item.id}`,
    rowType: BIZ_ROW_TYPE,
    childCount: 0
  })

  const filtered = filteredList.value
  const selectedKey = selectedGroupFilter.value

  // 已筛选到具体分组时，平铺展示该分组下的业务
  if (selectedKey !== ALL_GROUP_KEY) {
    return filtered.map(buildBizNode)
  }

  // 全部类别：构建分组树
  const grouped = new Map()
  const ungrouped = []

  filtered.forEach(item => {
    const gid = item.groupId
    if (gid) {
      if (!grouped.has(gid)) grouped.set(gid, [])
      grouped.get(gid).push(item)
    } else {
      ungrouped.push(item)
    }
  })

  sortedGroups.value.forEach(group => {
    const children = (grouped.get(group.id) || []).map(buildBizNode)
    result.push({
      rowKey: `group-${group.id}`,
      rowType: GROUP_ROW_TYPE,
      id: `group-${group.id}`,
      groupId: group.id,
      name: group.name,
      childCount: children.length,
      children
    })
  })

  if (ungrouped.length > 0) {
    result.push({
      rowKey: 'group-ungrouped',
      rowType: GROUP_ROW_TYPE,
      id: 'group-ungrouped',
      groupId: null,
      name: '未分组',
      childCount: ungrouped.length,
      children: ungrouped.map(buildBizNode)
    })
  }

  return result
})

const availableGroupBusinessOptions = computed(() => {
  const currentGroupId = editingGroup.value?.id ?? null
  return [...list.value]
    .filter(item => !item.groupId || item.groupId === currentGroupId)
    .sort((left, right) => {
      const leftGroupOrder = resolveGroupOrder(left.groupId)
      const rightGroupOrder = resolveGroupOrder(right.groupId)
      if (leftGroupOrder !== rightGroupOrder) return leftGroupOrder - rightGroupOrder
      const leftSort = left.sortOrder ?? 0
      const rightSort = right.sortOrder ?? 0
      if (leftSort !== rightSort) return leftSort - rightSort
      return (left.id ?? 0) - (right.id ?? 0)
    })
})

function createEmptyBusinessForm() {
  return {
    id: undefined,
    name: '',
    prefix: '',
    description: '',
    dailyAppointmentLimit: 50,
    sortOrder: 0,
    isEnabled: true,
    groupName: null
  }
}

function createEmptyGroupForm() {
  return {
    name: '',
    sortOrder: 0,
    businessTypeIds: []
  }
}

function resolveGroupKey(item) {
  return item.groupId ?? UNGROUPED_GROUP_KEY
}

function resolveGroupLabel(item) {
  return item.groupName || '未分组'
}

function resolveGroupOrder(groupId) {
  if (!groupId) {
    return Number.MAX_SAFE_INTEGER
  }
  const group = groups.value.find(item => item.id === groupId)
  if (!group) {
    return Number.MAX_SAFE_INTEGER
  }
  return group.sortOrder ?? Number.MAX_SAFE_INTEGER
}

function getGroupBusinessCount(groupId) {
  if (!groupId) {
    return ungroupedCount.value
  }
  return list.value.filter(item => item.groupId === groupId).length
}

function rowClassName({ row }) {
  const base = row.rowType === GROUP_ROW_TYPE ? 'biz-group-row' : ''
  return base
}

function isGroupExpanded(row) {
  return expandedGroupRowKeys.value.includes(row.rowKey)
}

function toggleGroupRow(row) {
  if (row.rowType !== GROUP_ROW_TYPE) return

  const nextExpanded = !isGroupExpanded(row)
  const keySet = new Set(expandedGroupRowKeys.value)

  if (nextExpanded) {
    keySet.add(row.rowKey)
  } else {
    keySet.delete(row.rowKey)
  }

  expandedGroupRowKeys.value = Array.from(keySet)
  tableRef.value?.toggleRowExpansion?.(row, nextExpanded)
}

function getFirstChar(text) {
  if (!text) return ''
  const ch = text.trim().charAt(0)
  if (!ch) return ''
  if (ch >= 'a' && ch <= 'z') return ch.toUpperCase()
  if (ch >= 'A' && ch <= 'Z') return ch.toUpperCase()
  const pinyin = PINYIN_MAP[ch] || ''
  return pinyin ? pinyin.toUpperCase() : ''
}

function onNameInput(val) {
  if (val.length > 50) {
    form.value.name = val.slice(0, 50)
    return
  }
  if (!isEdit.value) {
    form.value.prefix = getFirstChar(val)
  }
}

function onPrefixInput(val) {
  const upper = val.toUpperCase().replace(/[^A-Z]/g, '')
  form.value.prefix = upper.length > 5 ? upper.slice(0, 5) : upper
}

function onDescriptionInput(val) {
  if (val.length > 200) {
    form.value.description = val.slice(0, 200)
  }
}

async function fetchGroups() {
  const data = await businessTypeGroupApi.list()
  groups.value = Array.isArray(data) ? data : []
}

async function fetchList() {
  loading.value = true
  try {
    const data = await businessTypeApi.list()
    list.value = Array.isArray(data) ? data : []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function reloadAll() {
  await Promise.all([fetchGroups(), fetchList()])
  const filterExists = selectedGroupFilter.value === ALL_GROUP_KEY ||
    selectedGroupFilter.value === UNGROUPED_GROUP_KEY ||
    groups.value.some(group => group.id === selectedGroupFilter.value)

  if (!filterExists) {
    selectedGroupFilter.value = ALL_GROUP_KEY
  }

  initDragSort()
}

const dragState = ref({ sourceRowKey: null })

function initDragSort() {
  nextTick(() => {
    const tableEl = tableRef.value?.$el
    const tbody = tableEl?.querySelector('.el-table__body-wrapper tbody')
    if (!tbody) return
    if (tbody._sortable) {
      tbody._sortable.destroy()
    }

    // 构建 rowKey -> tr 元素映射
    const flatData = flattenTreeData()
    const rowKeyByTr = new Map()
    const trs = tbody.querySelectorAll('tr')
    trs.forEach(tr => {
      const text = (tr.innerText || '').trim()
      for (const row of flatData) {
        if (row.rowType === BIZ_ROW_TYPE && text.includes(row.name)) {
          rowKeyByTr.set(tr, row.rowKey)
          tr.setAttribute('data-row-key', row.rowKey)
          break
        }
        if (row.rowType === GROUP_ROW_TYPE && text.startsWith(row.name)) {
          rowKeyByTr.set(tr, row.rowKey)
          tr.setAttribute('data-row-key', row.rowKey)
          break
        }
      }
    })

    const getTrRowKey = (tr) => rowKeyByTr.get(tr) || tr?.getAttribute('data-row-key') || null

    tbody._sortable = Sortable.create(tbody, {
      handle: '.drag-handle',
      animation: 150,
      draggable: 'tr',
      filter: '.biz-group-row',
      preventOnFilter: false,
      onStart: (evt) => {
        lastHighlightedGroupId = undefined
        dragState.value.sourceRowKey = getTrRowKey(evt.item)
      },
      onMove: (evt) => {
        clearAllDropHighlights(tbody)

        if (selectedGroupFilter.value === ALL_GROUP_KEY) {
          const targetTr = findClosestTr(evt.related)
          if (targetTr) {
            const targetRowKey = getTrRowKey(targetTr)
            if (targetTr.classList.contains('biz-group-row')) {
              targetTr.classList.add('drop-target-highlight')
              const groupNode = treeData.value.find(row => row.rowKey === targetRowKey && row.rowType === GROUP_ROW_TYPE)
              lastHighlightedGroupId = groupNode?.groupId ?? null
            } else {
              const bizRow = flattenTreeData().find(row => row.rowKey === targetRowKey && row.rowType === BIZ_ROW_TYPE)
              if (bizRow) {
                lastHighlightedGroupId = bizRow.groupId ?? null
              }
            }
          }
        }
        return true
      },
      onEnd: async (evt) => {
        clearAllDropHighlights(tbody)

        const { oldIndex, newIndex } = evt

        if (oldIndex === newIndex) {
          dragState.value.sourceRowKey = null
          return
        }

        const sourceRowKey = dragState.value.sourceRowKey
        dragState.value.sourceRowKey = null

        if (!sourceRowKey) {
          tableKey.value++
          nextTick(initDragSort)
          return
        }

        // 全部类别视图下：判断是否跨分组移动
        if (selectedGroupFilter.value === ALL_GROUP_KEY) {
          const targetGroupId = lastHighlightedGroupId
          if (targetGroupId === undefined) {
            tableKey.value++
            nextTick(initDragSort)
            return
          }

          const sourceRow = flattenTreeData().find(row => row.rowKey === sourceRowKey)
          if (!sourceRow || sourceRow.rowType !== BIZ_ROW_TYPE) {
            tableKey.value++
            nextTick(initDragSort)
            return
          }

          // 跨分组移动
          if (sourceRow.groupId !== targetGroupId) {
            tableKey.value++
            await nextTick()
            initDragSort()
            await moveBizToGroup(sourceRow, targetGroupId)
            return
          }
          // 同组内排序：继续执行下面的通用排序逻辑
        }

        if (keyword.value.trim()) {
          ElMessage.warning('搜索状态下不支持拖拽排序，请先清空搜索词')
          tableKey.value++
          nextTick(initDragSort)
          return
        }

        const flatDataInner = flattenTreeData()
        const sourceIdx = flatDataInner.findIndex(row => row.rowKey === sourceRowKey)
        if (sourceIdx === -1) {
          tableKey.value++
          nextTick(initDragSort)
          return
        }

        // 同组排序：用数据驱动而非 DOM 索引
        const sourceRow = flatDataInner[sourceIdx]
        // 找到同组的所有业务节点
        const sameGroupBizItems = flatDataInner.filter(
          row => row.rowType === BIZ_ROW_TYPE && row.groupId === sourceRow.groupId
        )
        // 计算 source 在同组列表中的索引
        const srcGroupIdx = sameGroupBizItems.findIndex(item => item.rowKey === sourceRowKey)
        if (srcGroupIdx === -1) {
          tableKey.value++
          nextTick(initDragSort)
          return
        }

        // 用 DOM 中非分组行的位置差来推算目标在同组中的位置
        const domBizRows = Array.from(tbody.querySelectorAll('tr:not(.biz-group-row)'))
        const srcDomIdx = domBizRows.findIndex(tr => getTrRowKey(tr) === sourceRowKey)
        const domDelta = newIndex - oldIndex

        let targetGroupIdx = srcGroupIdx + domDelta
        targetGroupIdx = Math.max(0, Math.min(targetGroupIdx, sameGroupBizItems.length - 1))
        if (srcGroupIdx === targetGroupIdx) {
          tableKey.value++
          nextTick(initDragSort)
          return
        }

        // 执行数组移动
        const [moved] = sameGroupBizItems.splice(srcGroupIdx, 1)
        sameGroupBizItems.splice(targetGroupIdx, 0, moved)
        await updateSortOrder(sameGroupBizItems)
        tableKey.value++
        nextTick(initDragSort)
      }
    })
  })
}

let lastHighlightedGroupId = undefined

function clearAllDropHighlights(tbody) {
  if (!tbody) return
  tbody.querySelectorAll('.drop-target-highlight').forEach(el => {
    el.classList.remove('drop-target-highlight')
  })
}

function findGroupRowEl(el) {
  let current = el
  while (current && current.tagName !== 'TBODY') {
    if (current.tagName === 'TR' && current.classList.contains('biz-group-row')) {
      return current
    }
    current = current.parentElement
  }
  return null
}

function getRowKeyFromTr(trEl) {
  return trEl?.getAttribute('data-row-key') || trEl?.dataset?.rowKey || null
}

function findClosestTr(el) {
  let current = el
  while (current && current.tagName !== 'TR') {
    current = current.parentElement
    if (!current || current.tagName === 'TBODY') return null
  }
  return current
}

async function moveBizToGroup(sourceRow, targetGroupId) {
  const targetGroupName = targetGroupId
    ? (groups.value.find(g => g.id === targetGroupId)?.name || '未知分组')
    : '未分组'

  try {
    await businessTypeApi.update(sourceRow.id, {
      name: sourceRow.name,
      prefix: sourceRow.prefix,
      description: sourceRow.description || '',
      dailyAppointmentLimit: sourceRow.dailyAppointmentLimit ?? 50,
      sortOrder: sourceRow.sortOrder ?? 0,
      isEnabled: sourceRow.isEnabled !== false,
      groupId: targetGroupId
    })
    ElMessage.success(`已将「${sourceRow.name}」移至${targetGroupName}`)
    await reloadAll()
  } catch (err) {
    ElMessage.error(err.message || '移动失败')
  }
}

function flattenTreeData() {
  const flat = []
  for (const row of treeData.value) {
    flat.push(row)
    if (row.children) {
      for (const child of row.children) {
        flat.push(child)
      }
    }
  }
  return flat
}

async function updateSortOrder(bizItems) {
  try {
    const updates = bizItems.map((item, i) =>
      businessTypeApi.update(item.id, {
        name: item.name,
        prefix: item.prefix,
        description: item.description || '',
        dailyAppointmentLimit: item.dailyAppointmentLimit ?? 50,
        sortOrder: i,
        isEnabled: item.isEnabled !== false
      })
    )
    await Promise.all(updates)
    ElMessage.success('排序已更新')
    await fetchList()
  } catch (err) {
    ElMessage.error(err.message || '排序更新失败')
    await fetchList()
  }
}

function openCreate() {
  isEdit.value = false
  form.value = createEmptyBusinessForm()
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = {
    id: row.id,
    name: row.name,
    prefix: row.prefix,
    description: row.description || '',
    dailyAppointmentLimit: row.dailyAppointmentLimit ?? 50,
    sortOrder: row.sortOrder ?? 0,
    isEnabled: row.isEnabled !== false,
    groupName: row.groupName || null
  }
  dialogVisible.value = true
}

function openGroupDialog(group = null) {
  editingGroup.value = group
  groupForm.value = group
    ? {
        name: group.name,
        sortOrder: group.sortOrder ?? 0,
        businessTypeIds: list.value
          .filter(item => item.groupId === group.id)
          .map(item => item.id)
      }
    : {
        name: '',
        sortOrder: groups.value.length,
        businessTypeIds: []
      }
  groupDialogVisible.value = true
}

function openGroupDialogById(groupId) {
  const target = groups.value.find(item => item.id === groupId)
  if (!target) {
    ElMessage.warning('当前业务暂无所属分组')
    return
  }
  openGroupDialog(target)
}

async function handleSave() {
  if (!form.value.name || !form.value.name.trim()) {
    ElMessage.warning('请输入业务名称')
    return
  }
  if (!form.value.prefix || !form.value.prefix.trim()) {
    ElMessage.warning('请输入前缀')
    return
  }

  const normalizedPrefix = form.value.prefix.trim().toUpperCase()
  const normalizedName = form.value.name.trim()
  if (!/^[A-Z]{1,5}$/.test(normalizedPrefix)) {
    ElMessage.warning('前缀仅允许1-5位大写字母')
    return
  }

  const duplicateName = list.value.find(item => item.name?.trim() === normalizedName && item.id !== form.value.id)
  if (duplicateName) {
    ElMessage.warning('业务名称已存在')
    return
  }

  const duplicatePrefix = list.value.find(item => item.prefix?.trim().toUpperCase() === normalizedPrefix && item.id !== form.value.id)
  if (duplicatePrefix) {
    ElMessage.warning('前缀已存在')
    return
  }

  saving.value = true
  try {
    const payload = {
      name: normalizedName,
      prefix: normalizedPrefix,
      description: form.value.description || '',
      dailyAppointmentLimit: form.value.dailyAppointmentLimit ?? 50,
      sortOrder: form.value.sortOrder ?? 0,
      isEnabled: form.value.isEnabled !== false
    }

    if (isEdit.value) {
      await businessTypeApi.update(form.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await businessTypeApi.create(payload)
      ElMessage.success('创建成功')
    }

    dialogVisible.value = false
    await reloadAll()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除 "${row.name}" 吗？`, '确认删除')
    await businessTypeApi.delete(row.id)
    ElMessage.success('删除成功')
    await reloadAll()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message || '删除失败')
  }
}

async function handleSaveGroup() {
  if (!groupForm.value.name || !groupForm.value.name.trim()) {
    ElMessage.warning('请输入分组名称')
    return
  }

  groupSaving.value = true
  try {
    const payload = {
      name: groupForm.value.name.trim(),
      sortOrder: groupForm.value.sortOrder ?? 0,
      businessTypeIds: [...new Set((groupForm.value.businessTypeIds || []).filter(Boolean))]
    }

    if (editingGroup.value?.id) {
      await businessTypeGroupApi.update(editingGroup.value.id, payload)
      ElMessage.success('分组更新成功')
    } else {
      await businessTypeGroupApi.create(payload)
      ElMessage.success('分组创建成功')
    }

    groupDialogVisible.value = false
    await reloadAll()
  } catch (err) {
    ElMessage.error(err.message || '分组保存失败')
  } finally {
    groupSaving.value = false
  }
}

async function handleDeleteGroup(row) {
  const groupId = row.groupId
  const groupName = row.name
  try {
    await ElMessageBox.confirm(`确认删除分组「${groupName}」吗？删除后其下业务将变为未分组。`, '删除分组', { type: 'warning' })
    await businessTypeGroupApi.delete(groupId)
    ElMessage.success('分组删除成功')
    await reloadAll()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message || '分组删除失败')
  }
}

watch([selectedGroupFilter, keyword], () => {
  initDragSort()
})

watch(treeData, (rows) => {
  const validKeys = new Set(rows.filter(row => row.rowType === GROUP_ROW_TYPE).map(row => row.rowKey))
  expandedGroupRowKeys.value = expandedGroupRowKeys.value.filter(key => validKeys.has(key))
})

onMounted(() => {
  reloadAll()
})
</script>

<style scoped>
.biz-panel {
  width: 100%;
  max-width: none;
}

.group-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 20px;
  padding: 18px 20px;
  border: 1px solid var(--border);
  border-radius: 16px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.16), transparent 38%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(245, 249, 255, 0.98));
}

.group-toolbar__intro {
  flex: 1;
  min-width: 0;
}

.group-toolbar__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.group-summary {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
  font-size: 12px;
  color: var(--text-secondary);
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
}

.section-subtitle {
  margin-top: 6px;
  line-height: 1.6;
  font-size: 12px;
  color: var(--text-muted);
}

.panel-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.panel-actions__left,
.panel-actions__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.group-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-node__name {
  font-weight: 600;
  color: var(--text-primary);
}

.group-node__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  background: transparent;
  color: #909399;
  cursor: pointer;
}

.group-node__toggle:hover {
  color: #409eff;
}

.group-node__toggle-icon {
  font-size: 14px;
  transition: transform 0.2s ease, color 0.2s ease;
}

.group-node__toggle-icon.is-expanded {
  transform: rotate(90deg);
}

.biz-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.biz-name {
  color: var(--text-primary);
}

.group-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-height: 40px;
  padding: 10px 12px;
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-panel);
  font-size: 12px;
  color: var(--text-secondary);
}

.group-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.group-option__meta {
  font-size: 12px;
  color: var(--text-muted);
}

.helper-text {
  font-size: 11px;
  color: var(--text-muted);
}

.mono {
  font-family: 'Courier New', monospace;
  font-weight: 600;
}

.text-accent {
  color: #409eff;
}

.text-success {
  color: #67c23a;
  font-weight: 500;
}

.text-danger {
  color: #f56c6c;
  font-weight: 500;
}

.cell-muted {
  color: var(--text-muted);
}

:deep(.biz-group-row > td.el-table__cell) {
  background: rgba(64, 158, 255, 0.04);
}

:deep(.biz-group-row:hover > td.el-table__cell) {
  background: rgba(64, 158, 255, 0.07) !important;
}

:deep(.biz-group-row .el-table__expand-icon) {
  display: none;
}

:deep(.biz-group-row.drop-target-highlight > td.el-table__cell) {
  background: rgba(103, 194, 58, 0.15) !important;
  outline: 2px dashed #67c23a;
  outline-offset: -2px;
}

.drag-handle {
  cursor: move;
  font-size: 18px;
  color: #909399;
  user-select: none;
}

.drag-handle:hover {
  color: #409eff;
}

:global(.biz-dialog) {
  border-radius: 12px !important;
  overflow: hidden;
}

:global(.biz-dialog .el-dialog__header) {
  margin: 0 !important;
  padding: 20px 24px !important;
  border-bottom: 1px solid var(--border) !important;
  background: var(--bg-card) !important;
}

:global(.biz-dialog .el-dialog__body) {
  padding: 0 !important;
}

:global(.biz-dialog .el-dialog__footer) {
  padding: 0 !important;
}

.biz-form {
  padding: 24px;
}

.form-field {
  margin-bottom: 20px;
}

.field-label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.input-with-prefix {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.input-with-prefix__main {
  flex: 1;
  min-width: 0;
}

.prefix-badge {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-self: flex-start;
  width: 90px;
  flex-shrink: 0;
  padding: 8px 10px;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.prefix-badge-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.prefix-badge :deep(.el-input) {
  width: 100%;
}

.prefix-badge :deep(.el-input__wrapper) {
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  padding: 0 !important;
  min-height: auto !important;
}

.prefix-badge :deep(.el-input__inner) {
  font-family: var(--mono) !important;
  font-weight: 600;
  font-size: 14px !important;
  color: var(--primary) !important;
  letter-spacing: 0.04em;
  text-align: center;
}

.field-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  min-height: 18px;
}

.char-count {
  font-size: 11px;
  color: var(--text-muted);
  font-family: var(--mono);
}

.form-field :deep(.el-textarea__inner) {
  width: 100% !important;
  border-radius: var(--radius-md) !important;
}

.settings-strip {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  padding: 14px 16px;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.setting-item-switch {
  margin-left: auto;
}

.setting-label {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.setting-value {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
}

.setting-divider {
  width: 1px;
  height: 20px;
  background: var(--border);
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
  background: var(--bg-card);
}

.dialog-footer__spacer {
  flex: 1;
}

@media (max-width: 960px) {
  .group-toolbar,
  .panel-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .group-toolbar__meta {
    align-items: stretch;
  }

  .group-summary,
  .panel-actions__right,
  .panel-actions__left {
    justify-content: flex-start;
  }

  .panel-actions__right {
    flex-wrap: wrap;
  }

  .input-with-prefix,
  .settings-strip {
    flex-direction: column;
    align-items: stretch;
  }

  .prefix-badge {
    width: 100%;
  }

  .setting-item-switch {
    margin-left: 0;
  }

  .setting-divider {
    width: 100%;
    height: 1px;
  }
}
</style>
