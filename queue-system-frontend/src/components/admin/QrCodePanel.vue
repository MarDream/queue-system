<template>
  <div class="qrcode-panel">
    <!-- Top section: Config + Live Preview -->
    <div class="editor-section">
      <div class="config-panel">
        <h3>二维码配置</h3>

        <!-- Template Presets -->
        <div class="preset-group">
          <label class="preset-label">模板预设</label>
          <div class="preset-cards">
            <div
              v-for="preset in QR_PRESETS"
              :key="preset.key"
              class="preset-card"
              :class="{ active: qrConfig.template === preset.key }"
              @click="applyPreset(preset)"
            >
              <el-icon class="preset-icon"><component :is="preset.icon" /></el-icon>
              <span class="preset-name">{{ preset.label }}</span>
              <span class="preset-desc">{{ preset.description }}</span>
            </div>
          </div>
        </div>

        <!-- Region Selector -->
        <div class="config-row">
          <label>选择区域</label>
          <el-tree-select
            v-model="selectedRegionId"
            :data="regionTree"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="选择区域"
            clearable
            check-strictly
            :render-after-expand="false"
            filterable
            :filter-method="filterRegionByName"
            style="width:100%"
          />
        </div>

        <!-- Colors -->
        <div class="config-row-inline">
          <div class="config-half">
            <label>前景色</label>
            <el-color-picker v-model="qrConfig.fgColor" />
          </div>
          <div class="config-half">
            <label>背景色</label>
            <el-color-picker v-model="qrConfig.bgColor" />
          </div>
        </div>

        <!-- Error Correction -->
        <div class="config-row">
          <label>容错级别</label>
          <el-select v-model="qrConfig.errorCorrection" style="width:100%">
            <el-option label="L (7%)" value="L" />
            <el-option label="M (15%)" value="M" />
            <el-option label="Q (25%)" value="Q" />
            <el-option label="H (30%)" value="H" />
          </el-select>
        </div>

        <!-- Preview Size -->
        <div class="config-row">
          <label>预览大小 ({{ previewSize }}px)</label>
          <el-slider v-model="previewSize" :min="86" :max="430" :step="43" />
        </div>

        <!-- Margin -->
        <div class="config-row">
          <label>边距 ({{ qrConfig.margin }})</label>
          <el-slider v-model="qrConfig.margin" :min="0" :max="6" :marks="{ 0: '0', 3: '3', 6: '6' }" />
        </div>


        <!-- Bottom Text -->
        <div class="config-row">
          <label>底部文字</label>
          <el-input v-model="qrConfig.bottomText" placeholder="默认：扫码取号" maxlength="20" show-word-limit />
        </div>

        <!-- Logo Upload -->
        <div class="config-row">
          <label>Logo 图片</label>
          <div class="logo-uploader">
            <div v-if="qrConfig.logoDataUrl" class="logo-preview-box">
              <img :src="qrConfig.logoDataUrl" class="logo-thumb" alt="Logo" />
              <el-button size="small" type="danger" link @click="removeLogo">移除</el-button>
            </div>
            <el-upload
              v-else
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :on-change="handleLogoUpload"
            >
              <el-button size="small">
                <el-icon class="btn-i"><Picture /></el-icon> 选择Logo
              </el-button>
            </el-upload>
            <span v-if="qrConfig.logoDataUrl" class="logo-hint">已保存至本地</span>
          </div>
        </div>

        <!-- Generate Button -->
        <div class="config-row">
          <el-button
            type="primary"
            :disabled="!selectedRegionId"
            :loading="generating"
            @click="generateQr"
            style="width:100%"
          >
            <el-icon class="btn-i"><Picture /></el-icon> 生成二维码
          </el-button>
        </div>
      </div>

      <!-- Live Preview -->
      <div class="preview-panel">
        <h3>实时预览</h3>
        <div class="preview-container">
          <QrPreviewCard
            v-if="previewUrl"
            :url="previewUrl"
            :region-name="previewRegionName"
            :config="previewConfig"
            :bottom-text="qrConfig.bottomText"
          />
          <div v-else class="preview-placeholder">
            <el-icon :size="48"><Picture /></el-icon>
            <p>选择区域后预览</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Batch Generation Section -->
    <div class="batch-section">
      <h3>批量生成</h3>
      <div class="batch-controls">
        <el-checkbox v-model="selectAllBatch" :indeterminate="isPartialSelect" @change="toggleSelectAllBatch">
          全选可用区域
        </el-checkbox>
        <span class="batch-count">已选 {{ selectedRegionIds.length }} 个区域</span>
      </div>
      <el-checkbox-group v-if="availableRegionsForBatch.length" v-model="selectedRegionIds" class="batch-region-grid">
        <el-checkbox
          v-for="region in availableRegionsForBatch"
          :key="region.id"
          :value="region.id"
          class="batch-region-item"
        >
          {{ region.name }} ({{ region.code }})
        </el-checkbox>
      </el-checkbox-group>
      <div v-else class="batch-empty">所有区域已生成</div>
      <div v-if="batchProgress.running" class="batch-progress">
        <el-progress
          :percentage="batchProgress.total > 0 ? Math.round(batchProgress.current / batchProgress.total * 100) : 0"
          :status="batchProgress.failures > 0 ? 'exception' : undefined"
        />
        <span class="progress-text">
          正在生成 {{ batchProgress.current }}/{{ batchProgress.total }}
          (成功 {{ batchProgress.successes }}, 失败 {{ batchProgress.failures }})
        </span>
      </div>
      <el-button
        type="primary"
        :disabled="selectedRegionIds.length === 0 || batchProgress.running"
        :loading="batchProgress.running"
        @click="batchGenerate"
      >
        批量生成 ({{ selectedRegionIds.length }})
      </el-button>
    </div>

    <!-- QR List Section -->
    <div v-if="!qrList.length" class="empty-tip">
      <el-empty description="暂无二维码数据">
        <el-button type="primary" @click="selectedRegionId = regions[0]?.id ?? null">
          选择区域生成
        </el-button>
      </el-empty>
    </div>
    <div v-else class="qr-list-section">
      <div class="list-header">
        <h3>已生成的二维码 ({{ qrList.length }})</h3>
        <div class="list-controls">
          <el-input v-model="searchText" placeholder="搜索区域名称" clearable style="width:200px" />
          <el-button v-if="printSelected.length > 0" type="primary" size="small" @click="openPrintDialog">
            打印选中 ({{ printSelected.length }})
          </el-button>
        </div>
      </div>
      <el-checkbox-group v-model="printSelected" class="qr-grid">
        <div v-for="item in filteredQrList" :key="item.id" class="qr-card">
          <div class="qr-card-header">
            <el-checkbox :value="item.id" class="qr-card-check" />
            <h4>{{ item.regionName }}</h4>
          </div>
          <canvas :ref="el => item.canvasRef = el" class="qr-canvas" :style="{width: qrConfig.size+'px', height: qrConfig.size+'px'}" @click="previewQr(item)"></canvas>
          <el-tooltip :content="item.url" placement="bottom" :show-after="500">
            <p class="qr-link">{{ truncateUrl(item.url) }}</p>
          </el-tooltip>
          <p v-if="item.createdAt" class="qr-created">创建于 {{ formatDate(item.createdAt) }}</p>
          <div class="qr-actions">
            <el-tooltip content="下载" placement="top">
              <el-button size="small" link type="primary" @click="downloadQr(item)">
                <el-icon><Download /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="复制链接" placement="top">
              <el-button size="small" link @click="copyUrl(item.url)">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button size="small" link type="danger" @click="removeQr(item.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </el-checkbox-group>
    </div>

    <!-- Preview Dialog -->
    <el-dialog v-model="previewVisible" width="500px" draggable resizable :show-close="false">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ previewItem?.regionName }}</span>
          <el-button circle size="small" class="icon-close-btn" @click="previewVisible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>
      <div class="preview-content">
        <QrPreviewCard
          :url="previewItem?.url"
          :region-name="previewItem?.regionName"
          :config="{ ...previewConfig, size: 400 }"
          :bottom-text="qrConfig.bottomText"
        />
      </div>
    </el-dialog>

    <!-- Print Dialog -->
    <QrPrintDialog
      v-if="printDialogVisible"
      v-model="printDialogVisible"
      :items="printSelectedItems"
      :config="qrConfig"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, CopyDocument, Delete, Picture, Grid, Notebook } from '@element-plus/icons-vue'
import request from '../../api/index'
import { useUserStore } from '../../stores/user'
import QRCode from 'qrcode'
import QrPreviewCard from './QrPreviewCard.vue'
import QrPrintDialog from './QrPrintDialog.vue'

const userStore = useUserStore()

// === Data ===
const regions = ref([])
const selectedRegionId = ref(null)
const qrList = ref([])
const previewVisible = ref(false)
const previewItem = ref(null)
const searchText = ref('')
const printSelected = ref([])
const printDialogVisible = ref(false)

// === QR Config ===
const qrConfig = reactive({
  size: 258,
  fgColor: '#000000',
  bgColor: '#ffffff',
  margin: 2,
  errorCorrection: 'M',
  logoDataUrl: '',
  template: 'simple',
  bottomText: '扫码取号',
})

// === Preview (independent size) ===
const previewSize = ref(258)

// === Generating state ===
const generating = ref(false)

// === Batch state ===
const selectedRegionIds = ref([])
const selectAllBatch = ref(false)
const batchProgress = reactive({ total: 0, current: 0, successes: 0, failures: 0, running: false })

// === Presets ===
const QR_PRESETS = Object.freeze([
  {
    key: 'simple',
    label: '简洁',
    icon: 'Grid',
    description: '纯二维码，无装饰',
    config: { fgColor: '#000000', bgColor: '#ffffff', margin: 2, errorCorrection: 'M' }
  },
  {
    key: 'branded',
    label: '品牌',
    icon: 'Picture',
    description: '带Logo和配色的品牌二维码',
    config: { fgColor: '#0097a7', bgColor: '#ffffff', margin: 2, errorCorrection: 'H' }
  },
  {
    key: 'poster',
    label: '海报',
    icon: 'Notebook',
    description: '二维码+区域信息卡片，适合打印张贴',
    config: { fgColor: '#000000', bgColor: '#ffffff', margin: 3, errorCorrection: 'H' }
  }
])

// === Computed ===
const regionTree = computed(() => buildRegionTree(regions.value))

const availableRegionsForBatch = computed(() => {
  const existingIds = new Set(qrList.value.map(q => q.regionId))
  return regions.value.filter(r => !existingIds.has(r.id))
})

const isPartialSelect = computed(() => {
  const available = availableRegionsForBatch.value
  if (available.length === 0) return false
  const selected = selectedRegionIds.value.filter(id => available.some(a => a.id === id))
  return selected.length > 0 && selected.length < available.length
})

const filteredQrList = computed(() => {
  if (!searchText.value) return qrList.value
  return qrList.value.filter(item =>
    item.regionName.toLowerCase().includes(searchText.value.toLowerCase())
  )
})

const printSelectedItems = computed(() => {
  return qrList.value.filter(item => printSelected.value.includes(item.id))
})

// === Preview tracking ===
const previewUrl = computed(() => {
  if (!selectedRegionId.value) return ''
  const region = findRegionById(regions.value, selectedRegionId.value)
  if (!region) return ''
  return `${window.location.origin}/appointment?region=${region.code}`
})

const previewRegionName = computed(() => {
  if (!selectedRegionId.value) return ''
  const region = findRegionById(regions.value, selectedRegionId.value)
  return region?.name || ''
})

const previewConfig = computed(() => ({
  ...qrConfig,
  size: previewSize.value,
}))

// === Watch for size changes to re-render list QR codes ===
// (triggered when presets change margin/errorCorrection, which also need re-render)
watch(() => qrConfig.margin, async () => {
  await nextTick()
  for (const item of qrList.value) {
    await drawQrCode(item)
  }
})

// === Region tree helpers ===
function buildRegionTree(flatRegions) {
  if (!flatRegions || flatRegions.length === 0) return []
  const map = {}
  const roots = []
  flatRegions.forEach(r => {
    map[r.id] = { ...r, children: [] }
  })
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

function findRegionById(flatRegions, id) {
  return flatRegions.find(r => r.id === id) || null
}

// === Preset ===
function applyPreset(preset) {
  Object.assign(qrConfig, preset.config)
  qrConfig.template = preset.key
}

// === Logo ===
function handleLogoUpload(file) {
  if (file.size > 500 * 1024) {
    ElMessage.warning('Logo图片不能超过500KB')
    return
  }
  if (!file.raw.type.startsWith('image/')) {
    ElMessage.warning('请上传图片文件')
    return
  }
  const reader = new FileReader()
  reader.onload = (e) => {
    qrConfig.logoDataUrl = e.target.result
    try {
      localStorage.setItem('qrcode_logo_data', e.target.result)
    } catch {
      ElMessage.error('存储空间不足，无法保存Logo')
      qrConfig.logoDataUrl = ''
    }
  }
  reader.readAsDataURL(file.raw)
}

function removeLogo() {
  qrConfig.logoDataUrl = ''
  localStorage.removeItem('qrcode_logo_data')
}

// === API ===
async function fetchRegions() {
  try {
    const params = { userId: Number(userStore.userId) || undefined }
    regions.value = await request.get('/regions', { params })
    if (!userStore.isSuperAdmin && regions.value.length > 0) {
      const userRegion = regions.value.find(r => r.code === userStore.regionCode)
      if (userRegion) {
        selectedRegionId.value = userRegion.id
      }
    }
  }
  catch { regions.value = [] }
}

async function generateQr() {
  if (!selectedRegionId.value) return
  generating.value = true
  try {
    const region = findRegionById(regions.value, selectedRegionId.value)
    if (!region || !region.code) {
      ElMessage.error('未找到区域代码')
      return
    }
    const res = await request.get('/qrcode/url', { params: { regionCode: region.code, userId: Number(userStore.userId) || undefined } })

    const qrItem = {
      id: res.id,
      regionId: res.regionId,
      regionName: res.regionName,
      url: res.url,
      createdAt: res.createdAt || new Date().toISOString(),
      canvasRef: null
    }

    qrList.value = qrList.value.filter(item => item.regionName !== res.regionName)
    qrList.value.unshift(qrItem)

    await nextTick()
    await drawQrCode(qrItem)
    ElMessage.success('二维码已生成')
  } catch (e) {
    ElMessage.error('生成失败: ' + (e.message || ''))
  } finally {
    generating.value = false
  }
}

async function loadQrList() {
  try {
    const list = await request.get('/qrcode/list', { params: { userId: Number(userStore.userId) || undefined } })
    qrList.value = list.map(item => ({
      id: item.id,
      regionId: item.regionId,
      regionName: item.regionName,
      url: item.url,
      createdAt: item.createdAt,
      canvasRef: null
    }))
    await nextTick()
    for (const item of qrList.value) {
      await drawQrCode(item)
    }
  } catch {
    qrList.value = []
  }
}

async function removeQr(id) {
  try {
    await request.delete(`/qrcode/${id}`)
    qrList.value = qrList.value.filter(item => item.id !== id)
    printSelected.value = printSelected.value.filter(pid => pid !== id)
    ElMessage.success('已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

async function drawQrCode(item) {
  const canvas = item.canvasRef
  if (!canvas) return

  try {
    await QRCode.toCanvas(canvas, item.url, {
      width: qrConfig.size,
      margin: qrConfig.margin,
      color: { dark: qrConfig.fgColor, light: qrConfig.bgColor },
      errorCorrectionLevel: qrConfig.errorCorrection,
    })

    if (qrConfig.logoDataUrl) {
      const ctx = canvas.getContext('2d')
      const logo = new Image()
      logo.onload = () => {
        const logoSize = qrConfig.size * 0.2
        const x = (canvas.width - logoSize) / 2
        const y = (canvas.height - logoSize) / 2
        ctx.fillStyle = qrConfig.bgColor
        ctx.beginPath()
        if (ctx.roundRect) {
          ctx.roundRect(x - 3, y - 3, logoSize + 6, logoSize + 6, 4)
        } else {
          ctx.rect(x - 3, y - 3, logoSize + 6, logoSize + 6)
        }
        ctx.fill()
        ctx.drawImage(logo, x, y, logoSize, logoSize)
      }
      logo.src = qrConfig.logoDataUrl
    }
  } catch {
    // silently ignore
  }
}

// === Batch ===
function toggleSelectAllBatch(val) {
  if (val) {
    selectedRegionIds.value = availableRegionsForBatch.value.map(r => r.id)
  } else {
    selectedRegionIds.value = []
  }
}

async function batchGenerate() {
  batchProgress.total = selectedRegionIds.value.length
  batchProgress.current = 0
  batchProgress.successes = 0
  batchProgress.failures = 0
  batchProgress.running = true

  for (const regionId of [...selectedRegionIds.value]) {
    const region = regions.value.find(r => r.id === regionId)
    if (!region?.code) {
      batchProgress.failures++
      batchProgress.current++
      continue
    }

    try {
      const res = await request.get('/qrcode/url', {
        params: { regionCode: region.code, userId: Number(userStore.userId) || undefined }
      })
      qrList.value.unshift({
        id: res.id,
        regionId: res.regionId,
        regionName: res.regionName,
        url: res.url,
        createdAt: res.createdAt || new Date().toISOString(),
        canvasRef: null
      })
      batchProgress.successes++
    } catch {
      batchProgress.failures++
    }

    batchProgress.current++
    await nextTick()
  }

  batchProgress.running = false
  selectedRegionIds.value = []
  selectAllBatch.value = false

  await nextTick()
  for (const item of qrList.value) {
    await drawQrCode(item)
  }

  if (batchProgress.failures === 0) {
    ElMessage.success(`批量生成完成，共 ${batchProgress.successes} 个`)
  } else {
    ElMessage.warning(`生成完成：成功 ${batchProgress.successes}，失败 ${batchProgress.failures}`)
  }
}

// === Utilities ===
function truncateUrl(url) {
  if (!url) return ''
  if (url.length <= 40) return url
  return url.slice(0, 20) + '...' + url.slice(-15)
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function previewQr(item) {
  previewItem.value = item
  previewVisible.value = true
}

function downloadQr(item) {
  const link = document.createElement('a')
  link.href = item.canvasRef.toDataURL('image/png')
  link.download = `qrcode-${item.regionName}.png`
  link.click()
}

function copyUrl(url) {
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('链接已复制')
  })
}

function openPrintDialog() {
  if (printSelected.value.length === 0) {
    ElMessage.warning('请先选择要打印的二维码')
    return
  }
  printDialogVisible.value = true
}

// === Init ===
onMounted(() => {
  // Restore logo from localStorage
  const savedLogo = localStorage.getItem('qrcode_logo_data')
  if (savedLogo) {
    qrConfig.logoDataUrl = savedLogo
  }

  fetchRegions()
  loadQrList()
})
</script>

<style scoped>
.qrcode-panel { max-width: 1400px; }

/* Editor Section: Config + Preview */
.editor-section {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: var(--sp-6);
  margin-bottom: var(--sp-6);
}

.config-panel, .preview-panel {
  background: var(--bg-card);
  padding: var(--sp-6);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
}

.config-panel h3, .preview-panel h3 {
  margin: 0 0 var(--sp-4) 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}

/* Preset Cards */
.preset-group { margin-bottom: var(--sp-5); }
.preset-label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: var(--sp-2);
}
.preset-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--sp-2);
}
.preset-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-1);
  padding: var(--sp-3) var(--sp-2);
  border: 1.5px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  background: var(--bg-raised);
}
.preset-card:hover {
  border-color: var(--border-hi);
  background: var(--bg-panel);
}
.preset-card.active {
  border-color: var(--accent);
  background: var(--accent-glow);
  box-shadow: var(--glow-accent);
}
.preset-icon { font-size: 20px; color: var(--text-secondary); }
.preset-card.active .preset-icon { color: var(--accent); }
.preset-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.preset-desc {
  font-size: var(--text-xs);
  color: var(--text-muted);
  text-align: center;
  line-height: 1.3;
}

/* Config Rows */
.config-row {
  margin-bottom: var(--sp-4);
}
.config-row label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: var(--sp-2);
}
.config-row-inline {
  display: flex;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}
.config-half {
  flex: 1;
}
.config-half label {
  display: block;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: var(--sp-2);
}

/* Logo */
.logo-uploader {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}
.logo-preview-box {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}
.logo-thumb {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}
.logo-hint {
  font-size: var(--text-xs);
  color: var(--success);
}

/* Preview */
.preview-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}
.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-3);
  color: var(--text-muted);
}
.preview-placeholder p {
  font-size: var(--text-sm);
}

/* Batch Section */
.batch-section {
  background: var(--bg-card);
  padding: var(--sp-6);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  margin-bottom: var(--sp-6);
}
.batch-section h3 {
  margin: 0 0 var(--sp-4) 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}
.batch-controls {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  margin-bottom: var(--sp-3);
}
.batch-count {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}
.batch-region-grid {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
  max-height: 200px;
  overflow-y: auto;
}
.batch-region-item {
  min-width: 160px;
}
.batch-empty {
  color: var(--text-muted);
  font-size: var(--text-sm);
  padding: var(--sp-4) 0;
  text-align: center;
}
.batch-progress {
  margin-bottom: var(--sp-3);
}
.progress-text {
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

/* QR List */
.empty-tip {
  padding: var(--sp-12) 0;
}
.qr-list-section h3 {
  margin: 0 0 var(--sp-4) 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
}
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--sp-4);
}
.list-header h3 { margin: 0; }
.list-controls {
  display: flex;
  gap: var(--sp-3);
  align-items: center;
}
.qr-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--sp-5);
}
.qr-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--sp-5);
  text-align: center;
}
.qr-card-header {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}
.qr-card-check { flex-shrink: 0; }
.qr-card h4 {
  margin: 0;
  font-size: var(--text-base);
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
  text-align: left;
}
.qr-canvas {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: transform 0.2s;
}
.qr-canvas:hover {
  transform: scale(1.05);
}
.qr-link {
  margin: var(--sp-2) 0 0 0;
  font-size: var(--text-xs);
  color: var(--text-muted);
  word-break: break-all;
  cursor: default;
  line-height: 1.5;
}
.qr-created {
  margin: var(--sp-1) 0 var(--sp-2) 0;
  font-size: var(--text-xs);
  color: var(--text-secondary);
  line-height: 1.5;
}
.qr-actions {
  display: flex;
  gap: var(--sp-2);
  justify-content: center;
}

/* Preview Dialog */
.preview-content {
  display: flex;
  justify-content: center;
}

/* Tablet */
@media (max-width: 768px) {
  .editor-section {
    grid-template-columns: 1fr;
  }
  .preset-cards {
    grid-template-columns: repeat(3, 1fr);
  }
  .config-row-inline {
    flex-direction: column;
  }
  .batch-region-grid {
    flex-direction: column;
  }
  .batch-region-item {
    min-width: unset;
  }
}
</style>
