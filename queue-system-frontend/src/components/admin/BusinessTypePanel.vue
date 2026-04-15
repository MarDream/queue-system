<template>
  <div class="biz-panel">
    <div class="panel-actions">
      <el-button type="primary" @click="openCreate">
        <el-icon class="btn-i"><Plus /></el-icon> 新增业务类型
      </el-button>
    </div>

    <el-table ref="tableRef" :data="list" v-loading="loading" style="max-width:800px" row-key="id" empty-text="暂无数据！">
      <el-table-column label="拖动" width="60">
        <template #default>
          <span class="drag-handle">☰</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="prefix" label="前缀" width="80">
        <template #default="{ row }">
          <span class="mono text-accent">{{ row.prefix }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" />
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

    <el-dialog v-model="dialogVisible" width="500px" draggable resizable :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ isEdit ? '编辑业务类型' : '新增业务类型' }}</span>
          <el-button circle size="small" class="icon-close-btn" @click="dialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <el-form :model="form" label-width="100px" ref="formRef">
        <el-form-item label="名称" prop="name" required>
          <el-input v-model="form.name" @input="onNameChange" placeholder="请输入业务名称" />
        </el-form-item>
        <el-form-item label="前缀" prop="prefix">
          <el-input v-model="form.prefix" maxlength="3" style="width:120px" />
          <span class="prefix-hint">默认为名称首字大写，可手动修改</span>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isEnabled" />
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
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import request from '../../api/index'
import Sortable from 'sortablejs'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const form = ref({ name: '', prefix: '', description: '', sortOrder: 0, isEnabled: true })
const tableRef = ref(null)

// 简易拼音首字母映射（只用于名称第一个字）
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
  '用':'Y','地':'D','出':'C','让':'R','卖':'M','招':'Z','标':'B','采':'C',
  '购':'G','资':'Z','产':'C','易':'Y','商':'S','引':'Y','项':'X','目':'M',
  '准':'Z','评':'P','能':'N','职':'Z','收':'S','特':'T','困':'K','助':'Z',
  '低':'D','五':'W','救':'J','残':'C','疾':'J','联':'L','系':'X','役':'Y',
  '军':'J','优':'U','抚':'F','烈':'L','属':'S','伤':'S','复':'F','员':'Y',
  '伍':'W','士':'S','兵':'B','培':'P','训':'X','指':'Z','介':'J','绍':'S',
  '推':'T','荐':'J','创':'C','额':'E','担':'D','贴':'T','息':'X','奖':'J',
  '学':'X','源':'Y','毕':'B','到':'D','迁':'Q','移':'Y','落':'L','集':'J',
  '才':'C','进':'J','事':'S','代':'D','托':'T','管':'G','党':'D','织':'Z',
  '关':'G','团':'T','少':'S','先':'X','队':'D','光':'G','荣':'R','离':'L',
  '医':'Y','绿':'L','色':'S','道':'D','爱':'A','弱':'R','病':'B','孕':'Y',
  '依':'Y','窗':'C','站':'Z','式':'S','综':'Z','合':'H','一':'Y'
}

// 获取名称第一个字的拼音大写首字母
function getFirstChar(text) {
  if (!text) return ''
  const ch = text.trim().charAt(0)
  if (!ch) return ''
  if (ch >= 'a' && ch <= 'z') return ch.toUpperCase()
  if (ch >= 'A' && ch <= 'Z') return ch
  return PINYIN_MAP[ch] || ''
}

function onNameChange(val) {
  if (isEdit.value) return // 编辑时不自动改
  form.value.prefix = getFirstChar(val)
}

async function fetchList() {
  loading.value = true
  try {
    const data = await request.get('/admin/business-types')
    list.value = data.sort((a, b) => a.sortOrder - b.sortOrder)
  }
  catch { list.value = [] }
  finally { loading.value = false }
}

function initDragSort() {
  nextTick(() => {
    const tbody = tableRef.value?.$el.querySelector('.el-table__body-wrapper tbody')
    if (!tbody) return
    Sortable.create(tbody, {
      handle: '.drag-handle',
      animation: 150,
      onEnd: async ({ oldIndex, newIndex }) => {
        if (oldIndex === newIndex) return
        const movedItem = list.value.splice(oldIndex, 1)[0]
        list.value.splice(newIndex, 0, movedItem)
        await updateSortOrder()
      }
    })
  })
}

async function updateSortOrder() {
  try {
    for (let i = 0; i < list.value.length; i++) {
      list.value[i].sortOrder = i
      await request.put(`/admin/business-types/${list.value[i].id}`, list.value[i])
    }
    ElMessage.success('排序已更新')
  } catch (err) {
    ElMessage.error('排序更新失败')
    await fetchList()
  }
}

function openCreate() {
  isEdit.value = false
  form.value = { name: '', prefix: '', description: '', sortOrder: 0, isEnabled: true }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name || !form.value.name.trim()) {
    ElMessage.warning('请输入业务名称')
    return
  }
  // 前端预校验：全局 name 和 prefix 不能重复（排除自身）
  const normalizedName = form.value.name?.trim() ?? ''
  const normalizedPrefix = form.value.prefix?.trim() ?? ''
  const dupByName = list.value.find(
    item => (item.name ?? '').trim() === normalizedName && item.id !== form.value.id
  )
  if (dupByName) {
    ElMessage.warning('业务名称已存在')
    return
  }
  const dupByPrefix = list.value.find(
    item => (item.prefix ?? '').trim() === normalizedPrefix && item.id !== form.value.id
  )
  if (dupByPrefix) {
    ElMessage.warning('前缀已存在')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await request.put(`/admin/business-types/${form.value.id}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/admin/business-types', form.value)
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
  try {
    await ElMessageBox.confirm(`确认删除 "${row.name}" 吗？`, '确认删除')
    await request.delete(`/admin/business-types/${row.id}`)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(err.message)
  }
}

onMounted(() => {
  fetchList().then(() => initDragSort())
})
</script>

<style scoped>
.biz-panel { max-width: 1200px; }
.panel-actions { margin-bottom: 20px; }
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
.prefix-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
