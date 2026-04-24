<template>
  <div class="role-panel">
    <div class="filter-bar">
      <div class="filter-group">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索角色名称/编码"
          prefix-icon="Search"
          clearable
          style="width:220px"
          @input="handleSearch"
        />
      </div>
      <div class="filter-actions">
        <el-button type="primary" @click="openCreate">
          <el-icon class="btn-i"><Plus /></el-icon> 新增角色
        </el-button>
      </div>
    </div>
    <div class="role-tip">
      内置角色支持编辑基础信息和默认权限配置，但不支持删除。
    </div>

    <div class="table-container">
      <el-table :data="filteredList" v-loading="loading" stripe empty-text="暂无数据！">
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="角色编码" width="180">
          <template #default="{ row }">
            <code class="role-code">{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="角色名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 'SYSTEM' ? 'danger' : 'success'" size="small" effect="light">
              {{ row.type === 'SYSTEM' ? '内置' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openPermission(row)">
              <el-icon><Key /></el-icon> 权限配置
            </el-button>
            <el-button size="small" link type="primary" @click="openEdit(row)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-button size="small" link type="danger" @click="handleDelete(row)" :disabled="row.type === 'SYSTEM'" class="delete-icon-btn">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" width="460px" draggable destroy-on-close :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ isEdit ? '编辑角色' : '新增角色' }}</span>
          <el-button circle size="small" class="icon-close-btn" @click="dialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" placeholder="如：WINDOW_OPERATOR" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="如：窗口操作员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简要描述角色职能" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 权限配置弹窗 -->
    <el-dialog v-model="permDialogVisible" title="菜单权限配置" width="800px" destroy-on-close :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ permTarget?.name }} — 权限配置</span>
          <el-button circle size="small" class="icon-close-btn" @click="permDialogVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <div v-if="permTarget" class="perm-content">
        <div class="debug-info">
          <span>总菜单: {{ flatMenus.length }} 个 | 已选: {{ selectedMenuIds.length }} 个</span>
        </div>
        <div class="perm-section">
          <div class="perm-tip">勾选该角色可访问的菜单：</div>
          <div v-if="flatMenus.length === 0" class="empty-tip">暂无菜单数据</div>
          <div v-else class="menu-check-group">
            <div v-for="menu in flatMenus" :key="menu.id" class="menu-check-item">
              <el-checkbox
                :model-value="selectedMenuIds.includes(menu.id)"
                @update:model-value="toggleMenuId(menu.id, $event)"
              >
                <span class="menu-name">{{ menu.name }}</span>
                <span class="menu-path">{{ menu.path || '—' }}</span>
              </el-checkbox>
            </div>
          </div>
        </div>
        <div class="perm-section" v-if="buttonGroups.length > 0">
          <div class="perm-tip">勾选该角色可操作的按钮（基于已选菜单）：</div>
          <div class="button-groups">
            <div v-for="group in buttonGroups" :key="group.menuId" class="button-group">
              <div class="group-menu-name">{{ group.menuName }}</div>
              <div class="btn-check-list">
                <el-checkbox
                  v-for="btn in group.buttons"
                  :key="btn.id"
                  :model-value="selectedButtonIds.includes(btn.id)"
                  @update:model-value="toggleButtonId(btn.id, $event)"
                >
                  {{ btn.name }}
                </el-checkbox>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="handlePermSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Plus, Edit, Delete, Key } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { rolePermissionApi } from '../../api/admin'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const searchKeyword = ref('')

const formRef = ref(null)
const form = ref({
  code: '',
  name: '',
  description: '',
  sortOrder: 0
})

const formRules = {
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const filteredList = computed(() => {
  if (!searchKeyword.value.trim()) return list.value
  const kw = searchKeyword.value.trim().toLowerCase()
  return list.value.filter(r =>
    r.code.toLowerCase().includes(kw) ||
    r.name.toLowerCase().includes(kw)
  )
})

async function fetchList() {
  loading.value = true
  try {
    list.value = await rolePermissionApi.list()
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {}

function openCreate() {
  isEdit.value = false
  form.value = { code: '', name: '', description: '', sortOrder: 0 }
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.value = {
    id: row.id,
    code: row.code,
    name: row.name,
    description: row.description || '',
    sortOrder: row.sortOrder || 0
  }
  dialogVisible.value = true
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
      await rolePermissionApi.update(form.value.id, form.value)
      ElMessage.success('角色已更新')
    } else {
      await rolePermissionApi.create(form.value)
      ElMessage.success('角色已创建')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
    await rolePermissionApi.delete(row.id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

const permDialogVisible = ref(false)
const permSaving = ref(false)
const permTarget = ref(null)
const selectedMenuIds = ref([])
const selectedButtonIds = ref([])
const flatMenus = ref([])
const allButtons = ref([])

function toggleMenuId(menuId, checked) {
  if (checked) {
    if (!selectedMenuIds.value.includes(menuId)) selectedMenuIds.value.push(menuId)
  } else {
    selectedMenuIds.value = selectedMenuIds.value.filter(id => id !== menuId)
  }
}

function toggleButtonId(buttonId, checked) {
  if (checked) {
    if (!selectedButtonIds.value.includes(buttonId)) selectedButtonIds.value.push(buttonId)
  } else {
    selectedButtonIds.value = selectedButtonIds.value.filter(id => id !== buttonId)
  }
}

const buttonGroups = computed(() => {
  const map = {}
  for (const btn of allButtons.value) {
    if (!map[btn.menuId]) {
      const menu = flatMenus.value.find(m => m.id === btn.menuId)
      map[btn.menuId] = { menuId: btn.menuId, menuName: menu ? menu.name : '未知菜单', buttons: [] }
    }
    map[btn.menuId].buttons.push(btn)
  }
  return Object.values(map).sort((a, b) => a.menuId - b.menuId)
})

async function openPermission(row) {
  permTarget.value = row
  permDialogVisible.value = true
  selectedMenuIds.value = []
  selectedButtonIds.value = []
  try {
    const [menus, buttons, menuIds, buttonIds] = await Promise.all([
      rolePermissionApi.listMenus(),
      rolePermissionApi.listAllButtons(),
      rolePermissionApi.getRoleMenus(row.code),
      rolePermissionApi.getRoleButtons(row.code)
    ])
    flatMenus.value = menus || []
    allButtons.value = buttons || []
    selectedMenuIds.value = (menuIds || []).map(Number)
    selectedButtonIds.value = (buttonIds || []).map(Number)
  } catch {
    flatMenus.value = []
    allButtons.value = []
    ElMessage.error('获取权限数据失败')
  }
}

async function handlePermSave() {
  if (!permTarget.value) return
  permSaving.value = true
  try {
    await rolePermissionApi.updatePermissions(permTarget.value.code, {
      menuIds: selectedMenuIds.value,
      buttonIds: selectedButtonIds.value
    })
    ElMessage.success('权限已保存')
    permDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    permSaving.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.role-panel { max-width: 1100px; }

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-actions { margin-left: auto; }

.table-container {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}

.role-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px dashed var(--border);
  border-radius: 8px;
  font-size: 13px;
}

.role-code {
  font-size: 13px;
  color: var(--primary);
  background: var(--primary-light);
  padding: 2px 6px;
  border-radius: 4px;
}

.perm-content { display: flex; flex-direction: column; gap: 16px; }

.debug-info {
  font-size: 12px;
  color: var(--text-muted);
  padding: 4px 0;
}

.perm-section { display: flex; flex-direction: column; gap: 8px; }

.perm-tip {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.menu-check-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
  background: var(--bg-body);
  border-radius: 8px;
  border: 1px solid var(--border);
}

.menu-check-item { display: flex; align-items: center; }

.menu-name { font-weight: 500; color: var(--text-primary); }
.menu-path { font-size: 12px; color: var(--text-muted); margin-left: 4px; }

.button-groups { display: flex; flex-direction: column; gap: 12px; }

.button-group {
  padding: 10px 12px;
  background: var(--bg-body);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.group-menu-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.btn-check-list { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
