<template>
  <div class="admin-layout">
    <!-- Desktop/Tablet sidebar -->
    <aside :class="['sidebar', { 'sidebar-collapsed': isNarrow || sidebarCollapsed }]">
      <!-- Brand logo area -->
      <div class="sidebar-brand">
        <img src="/favicon.png" class="brand-logo" alt="QMS" />
        <span v-if="!isNarrow && !sidebarCollapsed" class="brand-text">QMS 排队系统</span>
      </div>

      <!-- Navigation menu -->
      <nav v-if="!sidebarCollapsed" ref="menuNavRef" class="menu" @contextmenu.prevent="handleSidebarContext">
        <div ref="rootMenuListRef" class="menu-root-list">
          <template v-for="item in rootItems" :key="item.id">
            <!-- 分组容器 -->
            <template v-if="item.type === 'group'">
              <div :class="['menu-group', { expanded: expandedIds.has(item.id) }]" :data-group-id="item.id">
                <!-- 分组头部（可点击展开/收起，拖拽手柄用于排序） -->
                <div class="menu-group-header" :data-menu-id="item.id" @click="toggleGroup(item.id)" @contextmenu.prevent="(e) => showContextMenu(e, item)">
                  <span class="drag-handle menu-group-drag" data-sortable-handle title="拖拽分组">≡</span>
                  <el-icon v-if="item.icon && !isEmoji(item.icon)" class="menu-icon-comp" :size="18"><component :is="item.icon" /></el-icon>
                  <span v-else class="menu-icon">{{ isEmoji(item.icon) ? item.icon : '📁' }}</span>
                  <span v-if="!isNarrow" class="menu-label">{{ item.name }}</span>
                  <span v-if="!isNarrow" class="menu-arrow">{{ expandedIds.has(item.id) ? '▾' : '▸' }}</span>
                </div>
                <!-- 分组子菜单 -->
                <transition name="submenu">
                  <div v-if="expandedIds.has(item.id)" class="menu-group-children" :data-group-id="item.id">
                    <div
                      v-for="child in item.children"
                      :key="child.id"
                      :class="['menu-item', 'menu-child', { active: activeKey === child.key }]"
                      :data-menu-id="child.id"
                      data-sortable-handle
                      @click="handleMenuClick(child)"
                      @contextmenu.prevent="(e) => showContextMenu(e, child)"
                      :title="child.name"
                    >
                      <el-icon v-if="child.icon && !isEmoji(child.icon)" class="menu-child-icon-comp" :size="16"><component :is="child.icon" /></el-icon>
                      <span v-else class="menu-child-icon">{{ isEmoji(child.icon) ? child.icon : '📁' }}</span>
                      <span v-if="!isNarrow" class="menu-label">{{ child.name }}</span>
                    </div>
                    <div v-if="!item.children?.length && !isNarrow" class="menu-group-empty">暂无数据！</div>
                  </div>
                </transition>
              </div>
            </template>
            <!-- 普通菜单项（无子菜单） -->
            <template v-else-if="!item.children?.length">
              <div
                :class="['menu-item', 'menu-root', { active: activeKey === item.key }]"
                :data-menu-id="item.id"
                data-sortable-handle
                @click="handleMenuClick(item)"
                @contextmenu.prevent="(e) => showContextMenu(e, item)"
                :title="item.name"
              >
                <el-icon v-if="item.icon && !isEmoji(item.icon)" class="menu-icon-comp" :size="18"><component :is="item.icon" /></el-icon>
                <span v-else class="menu-icon">{{ isEmoji(item.icon) ? item.icon : '📁' }}</span>
                <span v-if="!isNarrow" class="menu-label">{{ item.name }}</span>
              </div>
            </template>
            <!-- 普通菜单项（有子菜单，非分组类型） -->
            <template v-else>
              <div
                :class="['menu-item', 'menu-root', 'has-children', { active: isItemOrChildActive(item) }]"
                :data-menu-id="item.id"
                data-sortable-handle
                @click="handleMenuClick(item)"
                @contextmenu.prevent="(e) => showContextMenu(e, item)"
                :title="item.name"
              >
                <el-icon v-if="item.icon && !isEmoji(item.icon)" class="menu-icon-comp" :size="18"><component :is="item.icon" /></el-icon>
                <span v-else class="menu-icon">{{ isEmoji(item.icon) ? item.icon : '📁' }}</span>
                <span v-if="!isNarrow" class="menu-label">{{ item.name }}</span>
                <span v-if="!isNarrow" class="menu-arrow">{{ expandedIds.has(item.id) ? '▾' : '▸' }}</span>
              </div>
              <!-- 子菜单 -->
              <transition name="submenu">
                <div v-if="expandedIds.has(item.id)" class="menu-child-list" :data-parent-id="item.id">
                  <div
                    v-for="child in item.children"
                    :key="child.id"
                    :class="['menu-item', 'menu-child', { active: activeKey === child.key }]"
                    :data-menu-id="child.id"
                    data-sortable-handle
                    @click="handleMenuClick(child)"
                    @contextmenu.prevent="(e) => showContextMenu(e, child)"
                    :title="child.name"
                  >
                    <el-icon v-if="child.icon && !isEmoji(child.icon)" class="menu-child-icon-comp" :size="16"><component :is="child.icon" /></el-icon>
                    <span v-else class="menu-child-icon">{{ isEmoji(child.icon) ? child.icon : '📁' }}</span>
                    <span v-if="!isNarrow" class="menu-label">{{ child.name }}</span>
                  </div>
                </div>
              </transition>
            </template>
          </template>
        </div>
      </nav>
    </aside>

    <main class="content">
      <div class="top-nav">
        <div class="top-nav-left">
          <div class="topbar-brand">
            <img src="/favicon.png" class="topbar-logo" alt="QMS" />
            <span class="topbar-title">排队叫号管理系统</span>
          </div>
        </div>
        <div class="header-right">
          <div class="user-info">
            <span class="user-item">
              <el-icon class="info-icon"><Location /></el-icon>
              <span class="user-region">{{ userStore.regionName || '—' }}</span>
            </span>
            <el-divider direction="vertical" class="info-divider" />
            <span class="user-item">
              <el-icon class="info-icon"><UserFilled /></el-icon>
              <span class="user-role">{{ getRoleName(userStore.role) }}</span>
            </span>
            <el-divider direction="vertical" class="info-divider" />
            <span class="user-item">
              <el-icon class="info-icon"><Avatar /></el-icon>
              <span class="user-name">{{ userStore.name || userStore.username }}</span>
            </span>
          </div>
          <el-button plain size="small" @click="handleLogout" class="logout-btn">
            <el-icon class="btn-i"><SwitchButton /></el-icon>退出
          </el-button>
        </div>
      </div>
      <div class="content-body">
        <div class="content-header-card">
          <div class="content-header-left">
            <button class="nav-toggle" @click="sidebarCollapsed = !sidebarCollapsed" :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'">☰</button>
            <div class="content-title-group">
              <nav class="breadcrumb">
                <span class="breadcrumb-parent">{{ currentParentLabel }}</span>
                <span class="breadcrumb-sep">/</span>
                <span class="breadcrumb-current">{{ currentMenuLabel }}</span>
              </nav>
              <h2 class="content-title">{{ currentMenuLabel || '管理后台' }}</h2>
            </div>
          </div>
        </div>

        <div class="panel-host">
          <DashboardPanel v-if="activeKey === 'dashboard'" />
          <RegionPanel v-if="activeKey === 'region'" />
          <BusinessTypePanel v-if="activeKey === 'biz'" />
          <CounterPanel v-if="activeKey === 'counters'" />
          <QrCodePanel v-if="activeKey === 'qrcode'" />
          <UserPanel v-if="activeKey === 'users'" :initial-tab="userSubTab" />
          <MenuPanel v-if="activeKey === 'menu'" @reload="loadMenus" />
          <StatisticsPanel v-if="activeKey === 'statistics'" />
          <AiQueryPanel v-if="activeKey === 'ai'" />
        </div>

        <el-dialog v-model="regionPickerVisible" width="520px" draggable :show-close="false">
          <template #header>
            <div class="dialog-header">
              <span class="dialog-title">选择区域</span>
              <el-button circle size="small" class="icon-close-btn" @click="regionPickerVisible = false" title="关闭">
                <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M15 9l-6 6M9 9l6 6"/>
                </svg>
              </el-button>
            </div>
          </template>

          <div class="region-picker-body">
            <div class="region-picker-tip">超级管理员不绑定固定区域，请选择本次要进入的区域。</div>
            <el-tree-select
              v-model="regionPickerCode"
              :data="regionTreeForPicker"
              :props="{ label: 'label', value: 'value', children: 'children' }"
              placeholder="请选择区域"
              clearable
              check-strictly
              :render-after-expand="false"
              filterable
              style="width:100%"
            />
          </div>

          <template #footer>
            <el-button @click="regionPickerVisible = false">取消</el-button>
            <el-button type="primary" :disabled="!regionPickerCode" @click="confirmRegionPicker">确定</el-button>
          </template>
        </el-dialog>
      </div>
    </main>

    <!-- Mobile bottom tab bar -->
    <nav v-if="isMobile" class="mobile-tab-bar">
      <button
        v-for="item in flatMenus"
        :key="item.id"
        :class="['mobile-tab', { active: activeKey === item.key }]"
        @click="handleMenuClick(item)"
      >
        <el-icon v-if="item.icon && !isEmoji(item.icon)" :size="18"><component :is="item.icon" /></el-icon>
        <span v-else class="mobile-tab-icon">{{ isEmoji(item.icon) ? item.icon : '📁' }}</span>
        <span class="mobile-tab-label">{{ item.name }}</span>
      </button>
    </nav>

    <!-- 右键菜单 -->
    <ContextMenu
      :visible="ctxVisible"
      :x="ctxX"
      :y="ctxY"
      :items="ctxItems"
      @close="ctxVisible = false"
      @select="handleCtxSelect"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { SwitchButton, Location, UserFilled, Avatar } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { menuApi } from '../api/admin'
import request from '../api/index'
import Sortable from 'sortablejs'
import { ElMessage, ElMessageBox } from 'element-plus'
import DashboardPanel from '../components/admin/DashboardPanel.vue'
import RegionPanel from '../components/admin/RegionPanel.vue'
import BusinessTypePanel from '../components/admin/BusinessTypePanel.vue'
import CounterPanel from '../components/admin/CounterPanel.vue'
import QrCodePanel from '../components/admin/QrCodePanel.vue'
import UserPanel from '../components/admin/UserPanel.vue'
import MenuPanel from '../components/admin/MenuPanel.vue'
import StatisticsPanel from '../components/admin/StatisticsPanel.vue'
import AiQueryPanel from '../components/admin/AiQueryPanel.vue'
import ContextMenu from '../components/ContextMenu.vue'

const router = useRouter()
const userStore = useUserStore()

const activeKey = ref('dashboard')
const userSubTab = ref('users')
const isNarrow = ref(false)
const isMobile = ref(false)
const sidebarCollapsed = ref(false)
const menuNavRef = ref(null)
const rootMenuListRef = ref(null)

// 菜单数据（从后端加载）
const menuList = ref([])
const expandedIds = ref(new Set())

// 右键菜单
const ctxVisible = ref(false)
const ctxX = ref(0)
const ctxY = ref(0)
const ctxTarget = ref(null)
const ctxTargetType = ref('') // 'sidebar' | 'menu'

// 按 sortOrder 排序
const sortedMenus = computed(() => {
  return [...menuList.value].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
})

// 构建树形结构（按 parentId 分组）
const treeMenus = computed(() => {
  const childrenMap = {}
  for (const m of sortedMenus.value) {
    const pid = m.parentId ?? null
    if (pid !== null) {
      if (!childrenMap[pid]) childrenMap[pid] = []
      childrenMap[pid].push(m)
    }
  }

  const roots = sortedMenus.value.filter(m => !m.parentId)
  for (const r of roots) {
    // 分组：查找其子菜单（parent_id = group_id 且 type 不是 group 的菜单）
    r.children = (childrenMap[r.id] || []).filter(c => c.type !== 'group')
    r.key = menuKeyFromPath(r.path)
    for (const c of r.children) {
      c.key = menuKeyFromPath(c.path)
    }
  }
  return roots
})

// 根级别显示项（所有根级菜单：分组 + 普通菜单）
const rootItems = computed(() => {
  return treeMenus.value
})

// 所有分组列表（用于右键"移入分组"子菜单）
const groupsList = computed(() => {
  return treeMenus.value.filter(m => m.type === 'group')
})

// 右键菜单项
const ctxItems = computed(() => {
  if (!userStore.isSuperAdmin) return []
  const items = []

  if (ctxTargetType.value === 'sidebar') {
    items.push({ label: '新建分组', action: 'createGroup' })
  } else if (ctxTargetType.value === 'menu' && ctxTarget.value) {
    const item = ctxTarget.value
    // 分组的右键菜单：重命名 + 删除（空分组）
    if (item.type === 'group') {
      items.push({ label: '重命名', action: 'rename' })
      // 只有空分组才能删除
      if (!item.children || item.children.length === 0) {
        items.push({ label: '删除分组', action: 'deleteGroup' })
      }
    } else {
      // 非分组菜单：移入分组 + 重命名
      // 构建移入分组的子菜单
      const groupChildren = groupsList.value.map(g => ({
        label: g.name,
        action: `moveToGroup:${g.id}`,
        active: item.parentId === g.id
      }))
      if (groupChildren.length > 0 || item.parentId) {
        groupChildren.unshift({ label: '取消分组', action: 'ungroup', disabled: !item.parentId })
      }
      if (groupChildren.length > 1 || (groupChildren.length === 1 && item.parentId)) {
        items.push({ label: '移入分组', action: 'moveToGroup', children: groupChildren })
      } else if (groupChildren.length === 1 && !item.parentId) {
        // 只有一个分组且不在任何分组中，直接显示分组选项
        const singleGroup = groupsList.value[0]
        items.push({ label: `移入「${singleGroup.name}」`, action: `moveToGroup:${singleGroup.id}` })
      }
      items.push({ label: '重命名', action: 'rename' })
    }
  }
  return items
})

// 扁平列表（移动端用）
const flatMenus = computed(() => {
  const result = []
  for (const r of treeMenus.value) {
    if (r.type !== 'group') {
      result.push(r)
      if (r.children) result.push(...r.children)
    } else {
      // 分组的子菜单也加入扁平列表
      if (r.children) result.push(...r.children)
    }
  }
  return result
})

const currentMenuLabel = computed(() => {
  const all = flatMenus.value
  const found = all.find(m => m.key === activeKey.value)
  return found ? found.name : ''
})

// 面包屑父级标签（当前菜单所属的分组名，或当前菜单名）
const currentParentLabel = computed(() => {
  const all = flatMenus.value
  const current = all.find(m => m.key === activeKey.value)
  if (!current) return '管理后台'
  // 查找父级分组
  for (const r of treeMenus.value) {
    if (r.children?.some(c => c.key === activeKey.value)) {
      return r.name
    }
  }
  // 没有父级，返回自身名
  return current.name
})

// 判断菜单项或其子项是否激活
function isItemOrChildActive(item) {
  if (activeKey.value === item.key) return true
  return item.children?.some(c => c.key === activeKey.value) || false
}

function menuKeyFromPath(path) {
  const map = {
    '/home': 'dashboard',
    '/appointment': 'appointment',
    '/admin': 'dashboard',
    '/admin?tab=region': 'region',
    '/admin?tab=biz': 'biz',
    '/admin?tab=counters': 'counters',
    '/admin?tab=qrcode': 'qrcode',
    '/admin?tab=users': 'users',
    '/admin?tab=menu': 'menu',
    '/admin?tab=roles': 'users',
    '/admin?tab=statistics': 'statistics',
    '/admin?tab=ai': 'ai',
    '/counter': 'counter',
    '/display': 'display'
  }
  return map[path] || path
}

function normalizeMenuName(path, name) {
  return name || ''
}

function normalizeMenuIcon(icon) {
  return icon || '📁'
}

function isEmoji(str) {
  if (!str) return false
  return /[^\u0000-\u007F]/.test(str)
}

function handleMenuClick(item) {
  // 分组项点击仅展开/收起
  if (item.type === 'group') {
    toggleGroup(item.id)
    return
  }
  // 如果有子菜单，切换展开/收起
  if (item.children && item.children.length > 0) {
    const newSet = new Set(expandedIds.value)
    if (newSet.has(item.id)) {
      newSet.delete(item.id)
    } else {
      newSet.add(item.id)
    }
    expandedIds.value = newSet
  }
  // 独立页面菜单项 → 新标签页打开
  const routeKeys = { home: '/home', appointment: '/appointment', counter: '/counter', display: '/display' }
  if (item.key && routeKeys[item.key]) {
    let url = routeKeys[item.key]
    // 取号页面和大屏默认带上当前用户的 regionCode
    if (item.key === 'appointment' || item.key === 'display') {
      const savedRegionCode = localStorage.getItem('lastRegionCode') || ''
      const regionCode = userStore.regionCode || savedRegionCode
      if (!regionCode) {
        openRegionPicker(item.key)
        return
      }
      localStorage.setItem('lastRegionCode', regionCode)
      const targetUrl = item.key === 'display'
        ? `/display?region=${encodeURIComponent(regionCode)}`
        : `/appointment?region=${encodeURIComponent(regionCode)}`
      window.open(targetUrl, '_blank')
      return
    }
    window.open(url, '_blank')
    return
  }
  // 管理后台内部面板切换
  if (item.key) {
    if (item.key === 'users') {
      userSubTab.value = item.path === '/admin?tab=roles' ? 'roles' : 'users'
    }
    activeKey.value = item.key
  }
}

function toggleGroup(groupId) {
  const newSet = new Set(expandedIds.value)
  if (newSet.has(groupId)) {
    newSet.delete(groupId)
  } else {
    newSet.add(groupId)
  }
  expandedIds.value = newSet
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

const regionPickerVisible = ref(false)
const regionPickerCode = ref('')
const regionPickerTarget = ref('')
const allRegionsForPicker = ref([])

const regionTreeForPicker = computed(() => buildRegionTreeForPicker(allRegionsForPicker.value))

function buildRegionTreeForPicker(flatRegions) {
  if (!flatRegions || flatRegions.length === 0) return []
  const map = {}
  const roots = []
  for (const r of flatRegions) {
    const code = r.code || r.regionCode || ''
    const name = r.name || r.regionName || ''
    map[r.id] = { ...r, label: code ? `${name}（${code}）` : name, value: code, children: [] }
  }
  for (const r of flatRegions) {
    const node = map[r.id]
    if (!r.parentId || !map[r.parentId]) {
      roots.push(node)
    } else {
      map[r.parentId].children.push(node)
    }
  }
  return roots
}

async function ensureRegionsLoaded() {
  if (allRegionsForPicker.value.length > 0) return
  allRegionsForPicker.value = await request.get('/regions')
}

async function openRegionPicker(targetKey) {
  try {
    await ensureRegionsLoaded()
    regionPickerTarget.value = targetKey
    regionPickerCode.value = ''
    regionPickerVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '获取区域列表失败')
  }
}

function confirmRegionPicker() {
  const code = (regionPickerCode.value || '').trim()
  if (!code) return
  localStorage.setItem('lastRegionCode', code)
  regionPickerVisible.value = false
  const targetUrl = regionPickerTarget.value === 'display'
    ? `/display?region=${encodeURIComponent(code)}`
    : `/appointment?region=${encodeURIComponent(code)}`
  window.open(targetUrl, '_blank')
}

function getRoleName(role) {
  const map = {
    SUPER_ADMIN: '超级管理员',
    REGION_ADMIN: '区域管理员',
    WINDOW_OPERATOR: '窗口操作员'
  }
  return map[role] || role
}

function getRoleType(role) {
  const map = {
    SUPER_ADMIN: 'danger',
    REGION_ADMIN: 'warning',
    WINDOW_OPERATOR: 'success'
  }
  return map[role] || 'info'
}

function checkScreenSize() {
  const w = window.innerWidth
  isNarrow.value = w <= 1024
  isMobile.value = w <= 768
}

// ==================== 拖拽排序 ====================

let sortableInstance = null
let groupInstances = new Map()
let dragProcessed = false

function initSortable() {
  if (sortableInstance) sortableInstance.destroy()
  groupInstances.forEach(inst => inst.destroy())
  groupInstances.clear()
  dragProcessed = false

  const el = rootMenuListRef.value
  if (!el || isNarrow.value) return

  // 根列表 Sortable：
  // - .menu-root 元素可拖拽（移动菜单项）
  // - .menu-group 容器可拖拽（移动整个分组）
  // - 拖到 .menu-group-children 区域 → 加入分组
  sortableInstance = Sortable.create(el, {
    animation: 200,
    ghostClass: 'menu-ghost',
    chosenClass: 'menu-chosen',
    dragClass: 'menu-drag',
    group: 'menu-items',
    // 只有带 data-sortable-handle 的元素才能拖拽
    handle: '[data-sortable-handle]',
    filter: '.menu-group-empty',
    onEnd: async (evt) => {
      if (dragProcessed) { dragProcessed = false; return }

      const { item, from, to } = evt
      const draggedId = parseInt(item.dataset.menuId || item.dataset.groupId)

      // 场景1：拖入分组/父菜单子列表
      if (to.classList?.contains('menu-group-children') || to.classList?.contains('menu-child-list')) {
        dragProcessed = true
        const targetGroupId = parseInt(to.dataset.groupId || to.dataset.parentId)
        try {
          await menuApi.updateParent(draggedId, targetGroupId)
          ElMessage.success('已移入分组')
          await loadMenus()
        } catch (e) {
          ElMessage.error('操作失败')
          await loadMenus()
        }
        return
      }

      // 场景2：从子列表拖回根列表（包括分组子列表和普通子列表）
      if ((from.classList?.contains('menu-group-children') || from.classList?.contains('menu-child-list')) && to === el) {
        dragProcessed = true

        // DOM样式转换：从子菜单样式变为根菜单样式
        item.classList.remove('menu-child')
        item.classList.add('menu-item', 'menu-root')
        // 恢复图标
        const childIcon = item.querySelector('.menu-child-icon')
        if (childIcon && !item.querySelector('.menu-icon')) {
          const iconSpan = document.createElement('span')
          iconSpan.className = 'menu-icon'
          iconSpan.textContent = childIcon.textContent
          childIcon.replaceWith(iconSpan)
        }
        // 恢复拖拽手柄
        if (!item.querySelector('[data-sortable-handle]')) {
          item.setAttribute('data-sortable-handle', '')
        }

        try {
          await menuApi.updateParent(draggedId, null)
          ElMessage.success('已取消分组')
          await loadMenus()
        } catch (e) {
          ElMessage.error('操作失败')
          await loadMenus()
        } finally {
          dragProcessed = false
        }
        return
      }

      // 场景3：根级别内排序（包括分组与菜单之间的排序）
      if (from === to) {
        dragProcessed = true
        try {
          const rootEls = el.querySelectorAll(':scope > .menu-group, :scope > .menu-item.menu-root')
          const menuIds = []
          for (const rootEl of rootEls) {
            if (rootEl.classList.contains('menu-item')) {
              menuIds.push(parseInt(rootEl.dataset.menuId))
            } else if (rootEl.classList.contains('menu-group')) {
              menuIds.push(parseInt(rootEl.dataset.groupId))
            }
          }
          await menuApi.updateSort(menuIds)
          ElMessage.success('排序已更新')
          await loadMenus()
        } catch (e) {
          ElMessage.error('排序保存失败')
          await loadMenus()
        }
        return
      }

      // 其他情况不处理
      dragProcessed = false
    }
  })

  // 每个分组子列表初始化 Sortable：处理分组内排序 + 拖回根列表 + 拖到其他分组
  const groupChildrenEls = el.querySelectorAll('.menu-group-children')
  groupChildrenEls.forEach(groupEl => {
    createChildSortable(groupEl, groupEl.dataset.groupId)
  })

  // 普通菜单项的子菜单列表（menu-child-list）也需要可拖拽
  const childListEls = el.querySelectorAll('.menu-child-list')
  childListEls.forEach(childListEl => {
    // 使用 parentId 作为分组标识
    createChildSortable(childListEl, childListEl.dataset.parentId)
  })
}

function createChildSortable(containerEl, groupId) {
  if (!groupId) return
  const inst = Sortable.create(containerEl, {
      animation: 200,
      ghostClass: 'menu-ghost',
      chosenClass: 'menu-chosen',
      dragClass: 'menu-drag',
      group: {
        name: 'menu-items',
        pull: true,
        put: true
      },
      filter: '.menu-group-empty',
      handle: '[data-sortable-handle]',
      onAdd: async (evt) => {
        const { item, to: toEl, from: fromEl } = evt
        const draggedId = parseInt(item.dataset.menuId)
        const targetGroupId = parseInt(toEl.dataset.groupId || toEl.dataset.parentId)

        dragProcessed = true

        // 立即转换DOM样式：从根菜单样式变为子菜单样式
        if (item.classList.contains('menu-root')) {
          item.classList.remove('menu-root', 'has-children')
          item.classList.add('menu-child')
          // 转换图标样式
          const menuIcon = item.querySelector('.menu-icon')
          if (menuIcon && !item.querySelector('.menu-child-icon')) {
            const iconSpan = document.createElement('span')
            iconSpan.className = 'menu-child-icon'
            iconSpan.textContent = menuIcon.textContent
            menuIcon.replaceWith(iconSpan)
          }
          // 移除箭头
          const arrow = item.querySelector('.menu-arrow')
          if (arrow) arrow.remove()
        }

        try {
          await menuApi.updateParent(draggedId, targetGroupId)
          ElMessage.success('已移入分组')
          await loadMenus()
        } catch (e) {
          ElMessage.error('操作失败: ' + (e.message || ''))
          await loadMenus()
        } finally {
          dragProcessed = false
        }
      },
      onEnd: async (evt) => {
        if (dragProcessed) { dragProcessed = false; return }

        const { item, from, to } = evt

        // 拖回根列表
        if (to.classList?.contains('menu-root-list')) {
          const draggedId = parseInt(item.dataset.menuId)
          dragProcessed = true

          // DOM样式转换
          item.classList.remove('menu-child')
          item.classList.add('menu-item', 'menu-root')
          const childIcon = item.querySelector('.menu-child-icon')
          if (childIcon && !item.querySelector('.menu-icon')) {
            const iconSpan = document.createElement('span')
            iconSpan.className = 'menu-icon'
            iconSpan.textContent = childIcon.textContent
            childIcon.replaceWith(iconSpan)
          }
          if (!item.querySelector('[data-sortable-handle]')) {
            item.setAttribute('data-sortable-handle', '')
          }

          try {
            await menuApi.updateParent(draggedId, null)
            ElMessage.success('已取消分组')
            await loadMenus()
          } catch (e) {
            ElMessage.error('操作失败')
            await loadMenus()
          } finally {
            dragProcessed = false
          }
          return
        }

        // 拖到另一个分组/父菜单的子列表
        const targetGroupId = to.dataset.groupId || to.dataset.parentId
        if (targetGroupId && from !== to) {
          dragProcessed = true
          const draggedId = parseInt(item.dataset.menuId)
          const newGroupId = parseInt(targetGroupId)

          // DOM样式转换
          if (item.classList.contains('menu-root')) {
            item.classList.remove('menu-root', 'has-children')
            item.classList.add('menu-child')
            const menuIcon = item.querySelector('.menu-icon')
            if (menuIcon && !item.querySelector('.menu-child-icon')) {
              const iconSpan = document.createElement('span')
              iconSpan.className = 'menu-child-icon'
              iconSpan.textContent = menuIcon.textContent
              menuIcon.replaceWith(iconSpan)
            }
            const arrow = item.querySelector('.menu-arrow')
            if (arrow) arrow.remove()
          }

          try {
            await menuApi.updateParent(draggedId, newGroupId)
            ElMessage.success('已移入分组')
            await loadMenus()
          } catch (e) {
            ElMessage.error('操作失败')
            await loadMenus()
          } finally {
            dragProcessed = false
          }
          return
        }
      }
    })
  groupInstances.set(groupId, inst)
}

// ==================== 右键菜单 ====================

function handleSidebarContext(e) {
  if (!userStore.isSuperAdmin) return
  ctxTargetType.value = 'sidebar'
  ctxTarget.value = null
  ctxX.value = e.clientX
  ctxY.value = e.clientY
  ctxVisible.value = true
}

function showContextMenu(e, item) {
  if (!userStore.isSuperAdmin) return
  ctxTargetType.value = 'menu'
  ctxTarget.value = item
  ctxX.value = e.clientX
  ctxY.value = e.clientY
  ctxVisible.value = true
}

async function handleCtxSelect(item) {
  const action = item.action
  if (!action) return

  if (action === 'createGroup') {
    try {
      const { value } = await ElMessageBox.prompt('输入分组名称', '新建分组', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /\S/,
        inputErrorMessage: '名称不能为空'
      })
      await menuApi.createGroup(value.trim())
      ElMessage.success('分组创建成功')
      await loadMenus()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || '创建失败')
    }
  } else if (action === 'rename' && ctxTarget.value) {
    const item = ctxTarget.value
    try {
      const { value } = await ElMessageBox.prompt('输入新名称', '重命名菜单', {
        inputValue: item.name,
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /\S/,
        inputErrorMessage: '名称不能为空'
      })
      await menuApi.rename(item.id, value.trim())
      ElMessage.success('重命名成功')
      await loadMenus()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || '重命名失败')
    }
  } else if (action === 'ungroup') {
    const target = ctxTarget.value
    try {
      await menuApi.updateParent(target.id, null)
      ElMessage.success('已取消分组')
      await loadMenus()
    } catch (e) {
      ElMessage.error('操作失败')
    }
  } else if (action?.startsWith('moveToGroup:')) {
    const groupId = parseInt(action.split(':')[1])
    const target = ctxTarget.value
    try {
      await menuApi.updateParent(target.id, groupId)
      ElMessage.success('已移入分组')
      await loadMenus()
    } catch (e) {
      ElMessage.error('操作失败')
    }
  } else if (action === 'deleteGroup' && ctxTarget.value) {
    const target = ctxTarget.value
    try {
      await ElMessageBox.confirm(`确定要删除分组「${target.name}」吗？`, '删除分组', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await menuApi.deleteGroup(target.id)
      ElMessage.success('分组已删除')
      await loadMenus()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
    }
  }
}

// ==================== 数据加载 ====================

async function loadMenus() {
  try {
    const params = {}
    if (!userStore.isSuperAdmin) {
      params.userId = Number(userStore.userId) || undefined
    }
    const data = await menuApi.list(params)
    menuList.value = (data || [])
      .filter(item => item.path !== '/admin?tab=roles')
      .map(item => ({
      ...item,
      name: normalizeMenuName(item.path, item.name),
      icon: normalizeMenuIcon(item.icon)
      }))
    // 默认展开所有分组
    const newExpanded = new Set(expandedIds.value)
    for (const m of menuList.value) {
      if (m.type === 'group') {
        newExpanded.add(m.id)
      }
    }
    expandedIds.value = newExpanded
  } catch {
    menuList.value = [
      { id: 1, name: '系统配置', path: '/admin', icon: '📊', sortOrder: 1, parentId: null, type: 'page' },
      { id: 2, name: '区域管理', path: '/admin?tab=region', icon: '🗺️', sortOrder: 2, parentId: null, type: 'page' },
      { id: 3, name: '业务类型', path: '/admin?tab=biz', icon: '📋', sortOrder: 3, parentId: null, type: 'page' },
      { id: 4, name: '窗口管理', path: '/admin?tab=counters', icon: '🪟', sortOrder: 4, parentId: null, type: 'page' },
      { id: 5, name: '二维码', path: '/admin?tab=qrcode', icon: '📱', sortOrder: 5, parentId: null, type: 'page' },
      { id: 6, name: '用户管理', path: '/admin?tab=users', icon: '👤', sortOrder: 6, parentId: null, type: 'page' }
    ]
  }
  await nextTick()
  initSortable()
}

onMounted(() => {
  const params = new URLSearchParams(window.location.search)
  const tab = params.get('tab')
  if (tab === 'roles') {
    activeKey.value = 'users'
    userSubTab.value = 'roles'
  } else if (tab) {
    activeKey.value = tab
    userSubTab.value = 'users'
  }

  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  loadMenus()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
  if (sortableInstance) sortableInstance.destroy()
  groupInstances.forEach(inst => inst.destroy())
})
</script>

<style scoped>
/* ═══════════════════════════════════════════════════════════════
   Layout — Claude warm light theme for content area
   ═══════════════════════════════════════════════════════════════ */
.admin-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-body);
}

.sidebar {
  width: 200px;
  background: var(--bg-sidebar);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  transition: width var(--duration-normal) var(--ease-out);
  flex-shrink: 0;
}

.sidebar-collapsed {
  width: 64px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 0 var(--sp-4);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  min-height: 48px;
}

.brand-logo {
  height: 24px;
  width: auto;
  flex-shrink: 0;
}

.brand-text {
  font-size: var(--text-sm);
  font-weight: 600;
  color: #ffffff;
  white-space: nowrap;
  letter-spacing: 0.04em;
}

/* ═══════════════════════════════════════════════════════════════
   Menu navigation
   ═══════════════════════════════════════════════════════════════ */
.menu {
  flex: 1;
  padding: var(--sp-3) 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.menu-root-list {
  display: flex;
  flex-direction: column;
  width: 100%;
}

/* ── Menu item base ── */
.menu-item {
  display: flex;
  align-items: center;
  padding: 0 var(--sp-4);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  color: var(--text-sidebar);
  gap: var(--sp-3);
  min-height: 40px;
  position: relative;
  user-select: none;
  font-size: var(--text-sm);
}

.menu-item:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.06);
}

.menu-item.active {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.1);
}

.menu-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--primary);
  border-radius: 0 2px 2px 0;
}

.menu-icon {
  font-size: 18px;
  flex-shrink: 0;
  line-height: 1;
  width: 20px;
  text-align: center;
}

.menu-icon-comp {
  flex-shrink: 0;
  line-height: 1;
  width: 20px;
  text-align: center;
  color: currentColor;
}

.menu-child-icon-comp {
  flex-shrink: 0;
  line-height: 1;
  width: 20px;
  text-align: center;
  color: currentColor;
  opacity: 0.82;
}

.menu-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
}

.menu-arrow {
  font-size: 10px;
  color: var(--text-sidebar-muted);
  flex-shrink: 0;
  transition: transform var(--duration-fast) var(--ease-out);
}

/* ── Group container ── */
.menu-group {
  margin: 0;
}

/* 分组拖拽手柄 */
.menu-group-drag {
  cursor: grab;
  opacity: 0.38;
  font-size: 14px;
  flex-shrink: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
  user-select: none;
}

.menu-group-header:hover .menu-group-drag {
  opacity: 0.76;
}

.menu-group-drag:active {
  cursor: grabbing;
}

.menu-group-header {
  display: flex;
  align-items: center;
  padding: 0 var(--sp-4);
  cursor: pointer;
  color: var(--text-sidebar);
  gap: var(--sp-3);
  min-height: 40px;
  user-select: none;
  transition: all var(--duration-fast) var(--ease-out);
  font-size: var(--text-sm);
}

.menu-group-header:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.06);
}

.menu-group-header .menu-icon {
  font-size: 18px;
}

.menu-group-header .menu-label {
  font-weight: 600;
}

.menu-group-header .menu-arrow {
  margin-left: auto;
}

.menu-child {
  padding-left: var(--sp-4);
  min-height: 38px;
  color: var(--text-sidebar-muted);
  font-size: var(--text-sm);
  margin-left: calc(20px + var(--sp-3));
}

.menu-child:hover {
  color: #ffffff;
}

.menu-child.active {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
}

.menu-child.active::before {
  height: 16px;
}

.menu-child-icon {
  font-size: 18px;
  flex-shrink: 0;
  line-height: 1;
  width: 20px;
  text-align: center;
  opacity: 0.6;
  visibility: visible;
}

.menu-child.active .menu-child-icon {
  opacity: 1;
}

.menu-child .menu-label {
  font-weight: 400;
  margin-left: var(--sp-3);
}

.menu-child-list {
  background: rgba(0, 0, 0, 0.12);
}

.menu-group-children {
  background: rgba(0, 0, 0, 0.12);
  min-height: 40px;
  padding: 4px 0;
}

/* Submenu transition */
.submenu-enter-active, .submenu-leave-active {
  transition: all 0.2s var(--ease-out);
  overflow: hidden;
}

.submenu-enter-from, .submenu-leave-to {
  opacity: 0;
  max-height: 0;
}

.submenu-enter-to, .submenu-leave-from {
  opacity: 1;
  max-height: 500px;
}

/* Group empty */
.menu-group-empty {
  padding: var(--sp-3) var(--sp-5);
  font-size: var(--text-xs);
  color: rgba(255, 255, 255, 0.2);
  text-align: center;
  min-height: 38px;
}

/* ═══════════════════════════════════════════════════════════════
   Sortable.js drag states
   ═══════════════════════════════════════════════════════════════ */
.menu-ghost {
  opacity: 0.5;
  background: var(--primary-light);
  border-radius: var(--radius-sm);
}

.menu-chosen {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  background: rgba(255, 255, 255, 0.05);
}

.menu-drag {
  opacity: 0.8;
  transform: rotate(1deg);
}

/* ═══════════════════════════════════════════════════════════════
   Top navigation bar
   ═══════════════════════════════════════════════════════════════ */
.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
  background: var(--bg-body);
}

.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 var(--sp-6);
  min-height: 48px;
  background: var(--bg-topbar);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  gap: var(--sp-3);
}

.top-nav-left {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.topbar-logo {
  width: 24px;
  height: 24px;
}

.topbar-title {
  font-size: var(--text-sm);
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 0.04em;
}

.nav-toggle {
  background: #fff;
  border: 1px solid var(--border-input);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px 10px;
  font-size: 16px;
  line-height: 1;
  transition: all var(--duration-fast) var(--ease-out);
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 32px;
}

.nav-toggle:hover {
  color: var(--primary);
  border-color: var(--primary);
  background: var(--primary-light);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--text-sm);
}

.breadcrumb-parent,
.breadcrumb-sep {
  color: var(--text-secondary);
}

.breadcrumb-current {
  color: var(--text-primary);
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  color: rgba(255, 255, 255, 0.88);
}

.user-item {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

.info-icon,
.user-region,
.user-name {
  color: rgba(255, 255, 255, 0.78);
}

.info-divider {
  height: 16px;
  margin: 0;
  border-color: rgba(255, 255, 255, 0.14);
}

.user-role {
  font-size: var(--text-xs);
  color: #ffffff;
  background: rgba(0, 82, 217, 0.24);
  padding: 2px var(--sp-2);
  border-radius: var(--radius-sm);
}

.logout-btn {
  border-color: rgba(255, 255, 255, 0.18) !important;
  background: transparent !important;
  color: rgba(255, 255, 255, 0.88) !important;
}

.logout-btn:hover {
  border-color: #ffffff !important;
  background: rgba(255, 255, 255, 0.08) !important;
  color: #ffffff !important;
}

.content-body {
  flex: 1;
  padding: var(--sp-6);
  overflow-y: auto;
}

.content-header-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-6);
  padding: var(--sp-5) var(--sp-6);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.content-header-left {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
}

.content-title-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.content-title {
  font-size: var(--text-2xl);
  font-weight: 600;
  color: var(--text-primary);
}

.panel-host {
  min-height: calc(100% - 88px);
}

.region-picker-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.region-picker-tip {
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.mobile-tab-bar {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #ffffff;
  border-top: 1px solid var(--border);
  padding: var(--sp-1) var(--sp-2);
  z-index: 100;
  justify-content: space-around;
}

.mobile-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--sp-2) var(--sp-1);
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  min-height: var(--touch-lg);
  border-radius: var(--radius-sm);
}

.mobile-tab.active {
  color: var(--primary);
  background: var(--primary-light);
}

.mobile-tab-icon {
  font-size: 20px;
}

.mobile-tab-label {
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

/* ═══════════════════════════════════════════════════════════════
   RESPONSIVE: Tablet Landscape (≤1024px)
   ═══════════════════════════════════════════════════════════════ */
@media (max-width: 1024px) {
  .top-nav { padding: 0 var(--sp-4); }
  .content-body { padding: var(--sp-5); }
}

@media (max-width: 768px) {
  .top-nav {
    padding: var(--sp-3) var(--sp-4);
    flex-wrap: wrap;
    min-height: auto;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .content-body {
    padding: var(--sp-4);
    padding-bottom: calc(var(--sp-16) + var(--sp-4));
  }

  .content-header-card {
    padding: var(--sp-4);
  }

  .mobile-tab-bar {
    display: flex;
  }
}

@media (max-width: 480px) {
  .sidebar {
    width: 56px;
  }

  .sidebar-brand {
    padding: 0 var(--sp-3);
    justify-content: center;
  }

  .brand-logo {
    height: 22px;
  }

  .menu-item,
  .menu-group-header {
    padding: 0 var(--sp-3);
    justify-content: center;
  }

  .menu-item.active::before {
    left: 0;
  }

  .menu-label,
  .menu-arrow,
  .menu-child-icon,
  .brand-text,
  .topbar-title,
  .user-region,
  .user-name {
    display: none;
  }

  .content-header-left {
    width: 100%;
    align-items: flex-start;
  }

  .content-title {
    font-size: var(--text-xl);
  }

  .breadcrumb {
    font-size: var(--text-xs);
  }
}
</style>
