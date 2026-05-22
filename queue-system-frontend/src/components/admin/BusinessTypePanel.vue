<template>
  <div class="biz-panel">
    <section class="group-section">
      <div class="group-section__header">
        <div>
          <div class="section-title">业务分组</div>
          <div class="section-subtitle">一个业务类型只能归属一个分组，可按分组管理和筛选。</div>
        </div>
        <el-button type="primary" @click="openGroupDialog()">
          <el-icon class="btn-i"><FolderAdd /></el-icon> 新增分组
        </el-button>
      </div>

      <div class="group-card-list">
        <button
          type="button"
          class="group-card group-card--all"
          :class="{ 'is-active': selectedGroupId === ALL_GROUP_KEY }"
          @click="selectedGroupId = ALL_GROUP_KEY"
        >
          <div class="group-card__title">全部业务</div>
          <div class="group-card__meta">{{ list.length }} 个业务</div>
        </button>

        <div
          v-for="group in groups"
          :key="group.id"
          class="group-card"
          :class="{ 'is-active': selectedGroupId === group.id }"
          @click="selectedGroupId = group.id"
        >
          <div class="group-card__title">{{ group.name }}</div>
          <div class="group-card__meta">排序 {{ group.sortOrder ?? 0 }} / {{ getGroupBusinessCount(group.id) }} 个业务</div>
          <div class="group-card__actions">
            <el-button
              size="small"
              link
              type="primary"
              @click.stop="openGroupDialog(group)"
            >
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button
              size="small"
              link
              type="danger"
              @click.stop="handleDeleteGroup(group)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <div class="panel-actions">
      <div class="panel-actions__left">
        <el-button type="primary" @click="openCreate">
          <el-icon class="btn-i"><Plus /></el-icon> 新增业务类型
        </el-button>
      </div>
      <div class="panel-actions__right">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索业务名称、前缀或分组名称"
          style="width: 260px"
        />
      </div>
    </div>

    <el-table
      ref="tableRef"
      :data="displayList"
      v-loading="loading"
      row-key="id"
      empty-text="暂无数据！"
    >
      <el-table-column label="拖动" width="60">
        <template #default>
          <span class="drag-handle">☰</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="所属分组" min-width="140">
        <template #default="{ row }">
          <el-tag type="info" effect="plain">{{ row.groupName || '未分组' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="prefix" label="前缀" width="90">
        <template #default="{ row }">
          <span class="mono text-accent">{{ row.prefix }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="dailyAppointmentLimit" label="每日预约限额" width="120" align="center" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <span :class="row.isEnabled ? 'text-success' : 'text-danger'">
            {{ row.isEnabled ? '启用' : '停用' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-tooltip content="编辑" placement="top">
            <el-button size="small" link type="primary" @click="openEdit(row)">
              <el-icon><Edit /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button size="small" link type="danger" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </el-tooltip>
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
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>

      <div class="biz-form">
        <div class="form-field">
          <label class="field-label">所属分组</label>
          <el-select v-model="form.groupId" placeholder="请选择业务分组" style="width: 100%">
            <el-option v-for="group in groups" :key="group.id" :label="group.name" :value="group.id" />
          </el-select>
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
      width="460px"
      draggable
      top="12vh"
      :show-close="false"
      :close-on-click-modal="false"
      custom-class="biz-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ editingGroup?.id ? '编辑业务分组' : '新增业务分组' }}</span>
          <el-button circle size="large" class="icon-close-btn" @click="groupDialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
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
          <label class="field-label">排序</label>
          <el-input-number v-model="groupForm.sortOrder" :min="0" :max="999" controls-position="right" />
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="groupDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="groupSaving" @click="handleSaveGroup">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, FolderAdd, Plus } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import { businessTypeApi, businessTypeGroupApi } from '../../api/admin'

const ALL_GROUP_KEY = '__all__'

const list = ref([])
const groups = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const groupDialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const groupSaving = ref(false)
const keyword = ref('')
const selectedGroupId = ref(ALL_GROUP_KEY)
const tableRef = ref(null)
const editingGroup = ref(null)

const form = ref(createEmptyBusinessForm())
const groupForm = ref(createEmptyGroupForm())

const displayList = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  return list.value.filter(item => {
    const groupMatched = selectedGroupId.value === ALL_GROUP_KEY || item.groupId === selectedGroupId.value
    if (!groupMatched) return false
    if (!normalizedKeyword) return true
    return [item.name, item.prefix, item.groupName, item.description]
      .filter(Boolean)
      .some(value => String(value).toLowerCase().includes(normalizedKeyword))
  })
})

const PINYIN_MAP = {
  '个':'G','人':'R','业':'Y','务':'W','对':'D','公':'G','企':'Q','社':'S',
  '医':'Y','疗':'L','保':'B','险':'X','住':'Z','房':'F','积':'J','金':'J',
  '老':'L','养':'Y','退':'T','休':'X','户':'H','口':'K','登':'D','记':'J',
  '婚':'H','姻':'Y','生':'S','育':'Y','就':'J','失':'S','工':'G','商':'S',
  '税':'S','水':'S','电':'D','燃':'R','气':'Q','暖':'N','物':'W','财':'C',
  '政':'Z','法':'F','律':'L','诉':'S','讼':'S','仲':'Z','裁':'C','证':'Z',
  '档':'D','案':'A','签':'Q','照':'Z','批':'P','文':'W','许':'X','可':'K',
  '认':'R','审':'S','核':'H','备':'B','投':'T','建':'J','议':'Y','咨':'Z',
  '询':'X','导':'D','办':'B','理':'L','缴':'J','费':'F','查':'C','打':'D',
  '印':'Y','复':'F','制':'Z','传':'C','真':'Z','扫':'S','描':'M','快':'K',
  '递':'D','邮':'Y','寄':'J','存':'C','取':'Q','贷':'D','款':'K','结':'J',
  '算':'S','汇':'H','转':'Z','账':'Z','现':'X','支':'Z','票':'P','信':'X',
  '用':'Y','卡':'K','基':'J','础':'C','设':'S','施':'S','环':'H','境':'J',
  '卫':'W','计':'J','划':'H','量':'L','质':'Z','检':'J','安':'A','全':'Q',
  '消':'X','防':'F','交':'J','通':'T','运':'Y','输':'S','化':'H','体':'T',
  '旅':'L','游':'Y','景':'J','点':'D','门':'M','挂':'G','号':'H','专':'Z',
  '家':'J','诊':'Z','手':'S','术':'S','验':'Y','注':'Z','射':'S','抽':'C',
  '血':'X','拍':'P','片':'P','心':'X','图':'T','脑':'N','超':'C','声':'S',
  '彩':'C','磁':'C','共':'G','振':'Z','行':'X','执':'Z','城':'G','管':'G',
  '林':'L','农':'N','牧':'M','渔':'Y','利':'L','国':'G','土':'T','规':'G',
  '地':'D','出':'C','让':'R','卖':'M','招':'Z','标':'B','采':'C','购':'G',
  '资':'Z','产':'C','易':'Y','商':'S','引':'Y','项':'X','目':'M','准':'Z',
  '评':'P','能':'N','职':'Z','收':'S','特':'T','困':'K','助':'Z','低':'D',
  '五':'W','救':'J','残':'C','疾':'J','联':'L','系':'X','役':'Y','军':'J',
  '优':'U','抚':'F','烈':'L','属':'S','伤':'S','复':'F','员':'Y','伍':'W',
  '士':'S','兵':'B','培':'P','训':'X','指':'Z','介':'J','绍':'S','推':'T',
  '荐':'J','创':'C','额':'E','担':'D','贴':'T','息':'X','奖':'J','学':'X',
  '源':'Y','毕':'B','到':'D','迁':'Q','移':'Y','落':'L','集':'J','才':'C',
  '进':'J','事':'S','代':'D','托':'T','管':'G','党':'D','织':'Z','关':'G',
  '团':'T','少':'S','先':'X','队':'D','光':'G','荣':'R','离':'L','绿':'L',
  '色':'S','道':'D','爱':'A','弱':'R','病':'B','孕':'Y','依':'Y','窗':'C',
  '站':'Z','式':'S','综':'Z','合':'H','一':'Y'
}

function createEmptyBusinessForm() {
  return {
    id: undefined,
    groupId: null,
    name: '',
    prefix: '',
    description: '',
    dailyAppointmentLimit: 50,
    sortOrder: 0,
    isEnabled: true
  }
}

function createEmptyGroupForm() {
  return {
    name: '',
    sortOrder: 0
  }
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

function getGroupBusinessCount(groupId) {
  return list.value.filter(item => item.groupId === groupId).length
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
  if (selectedGroupId.value !== ALL_GROUP_KEY && !groups.value.some(group => group.id === selectedGroupId.value)) {
    selectedGroupId.value = ALL_GROUP_KEY
  }
  initDragSort()
}

function initDragSort() {
  nextTick(() => {
    const tbody = tableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!tbody) return
    if (tbody._sortable) {
      tbody._sortable.destroy()
    }
    tbody._sortable = Sortable.create(tbody, {
      handle: '.drag-handle',
      animation: 150,
      onEnd: async ({ oldIndex, newIndex }) => {
        if (oldIndex === newIndex) return
        if (selectedGroupId.value === ALL_GROUP_KEY) {
          ElMessage.warning('请先切换到具体分组后再拖拽排序')
          await fetchList()
          return
        }
        if (keyword.value.trim()) {
          ElMessage.warning('搜索筛选状态下不支持拖拽排序，请先清空搜索词')
          await fetchList()
          return
        }
        const orderedItems = [...displayList.value]
        const movedItem = orderedItems.splice(oldIndex, 1)[0]
        orderedItems.splice(newIndex, 0, movedItem)
        await updateSortOrder(orderedItems)
      }
    })
  })
}

async function updateSortOrder(groupItems) {
  try {
    for (let i = 0; i < groupItems.length; i++) {
      const item = groupItems[i]
      item.sortOrder = i
      await businessTypeApi.update(item.id, item)
    }
    ElMessage.success('排序已更新')
    await fetchList()
  } catch (err) {
    ElMessage.error('排序更新失败')
    await fetchList()
  }
}

function openCreate() {
  isEdit.value = false
  form.value = createEmptyBusinessForm()
  if (selectedGroupId.value !== ALL_GROUP_KEY) {
    form.value.groupId = selectedGroupId.value
  } else if (groups.value.length > 0) {
    form.value.groupId = groups.value[0].id
  }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = {
    id: row.id,
    groupId: row.groupId,
    name: row.name,
    prefix: row.prefix,
    description: row.description || '',
    dailyAppointmentLimit: row.dailyAppointmentLimit ?? 50,
    sortOrder: row.sortOrder ?? 0,
    isEnabled: row.isEnabled !== false
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!groups.value.length) {
    ElMessage.warning('请先新增业务分组')
    return
  }
  if (!form.value.groupId) {
    ElMessage.warning('请选择业务分组')
    return
  }
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
      ...form.value,
      name: normalizedName,
      prefix: normalizedPrefix
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

function openGroupDialog(group = null) {
  editingGroup.value = group
  groupForm.value = group
    ? { name: group.name, sortOrder: group.sortOrder ?? 0 }
    : { name: '', sortOrder: groups.value.length }
  groupDialogVisible.value = true
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
      sortOrder: groupForm.value.sortOrder ?? 0
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

async function handleDeleteGroup(group) {
  try {
    await ElMessageBox.confirm(`确认删除分组「${group.name}」吗？`, '删除分组', { type: 'warning' })
    await businessTypeGroupApi.delete(group.id)
    ElMessage.success('分组删除成功')
    await reloadAll()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message || '分组删除失败')
  }
}

onMounted(() => {
  reloadAll()
})
</script>

<style scoped>
.biz-panel {
  width: 100%;
  max-width: none;
}

.group-section {
  margin-bottom: 20px;
  padding: 16px 18px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 12px;
}

.group-section__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.section-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.group-card-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}

.group-card {
  position: relative;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--bg-panel);
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.group-card:hover {
  border-color: var(--primary);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.08);
}

.group-card.is-active {
  border-color: var(--primary);
  background: var(--primary-light);
}

.group-card--all {
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.12), rgba(64, 158, 255, 0.04));
}

.group-card__title {
  padding-right: 48px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.group-card__meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}

.group-card__actions {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  gap: 2px;
}

.panel-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.panel-actions__left,
.panel-actions__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mono { font-family: 'Courier New', monospace; font-weight: 600; }
.text-accent { color: #409eff; }
.text-success { color: #67c23a; font-weight: 500; }
.text-danger { color: #f56c6c; font-weight: 500; }
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
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 8px;
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
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
  background: var(--bg-card);
}
</style>
