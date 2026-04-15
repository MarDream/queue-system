<template>
  <el-dialog
    :model-value="modelValue"
    title="打印二维码"
    width="720px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">打印二维码</span>
        <el-button circle size="small" class="icon-close-btn" @click="$emit('update:modelValue', false)" title="关闭">
          <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" />
            <path d="M15 9l-6 6M9 9l6 6" />
          </svg>
        </el-button>
      </div>
    </template>

    <!-- Settings -->
    <div class="print-settings">
      <div class="setting-row">
        <label class="setting-label">纸张大小</label>
        <el-radio-group v-model="pageSize">
          <el-radio-button value="a4">A4</el-radio-button>
          <el-radio-button value="a5">A5</el-radio-button>
        </el-radio-group>
      </div>
      <div class="setting-row">
        <label class="setting-label">每行列数</label>
        <el-select v-model="columns" style="width: 100px">
          <el-option :value="1" label="1 列" />
          <el-option :value="2" label="2 列" />
          <el-option :value="3" label="3 列" />
          <el-option :value="4" label="4 列" />
        </el-select>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="rendering" class="print-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>正在渲染二维码 ({{ renderedCount }}/{{ totalCount }})</span>
    </div>

    <!-- Preview -->
    <template v-if="!rendering && renderedImages.length > 0">
      <div class="print-preview-label">打印预览</div>
      <div
        class="print-preview"
        :class="'paper-' + pageSize"
        :style="{ gridTemplateColumns: `repeat(${columns}, 1fr)` }"
      >
        <div
          v-for="(img, idx) in renderedImages"
          :key="idx"
          class="qr-cell"
        >
          <img :src="img.dataUrl" :alt="img.regionName" class="qr-img" />
          <span class="qr-label">{{ img.regionName }}</span>
        </div>
      </div>
    </template>

    <!-- Footer -->
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="$emit('update:modelValue', false)">取消</el-button>
        <el-button
          type="primary"
          :disabled="rendering || renderedImages.length === 0"
          :loading="rendering"
          @click="handlePrint"
        >
          <el-icon class="btn-i"><Printer /></el-icon> 打印
        </el-button>
      </div>
    </template>

    <!-- Hidden canvases for rendering -->
    <div class="hidden-canvases">
      <canvas
        v-for="(item, idx) in items"
        :key="idx"
        :ref="el => hiddenCanvasRefs[idx] = el"
        class="qr-hidden-canvas"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import { Download, Printer, Loading } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
  config: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update:modelValue'])

// Internal state
const pageSize = ref('a4')
const columns = ref(2)
const renderedImages = ref([])
const rendering = ref(false)
const renderedCount = ref(0)
const totalCount = ref(0)
const hiddenCanvasRefs = ref([])

// Watch dialog open
watch(
  () => props.modelValue,
  async (visible) => {
    if (visible && props.items.length > 0) {
      await renderAllQrCodes()
    }
  }
)

async function renderAllQrCodes() {
  rendering.value = true
  renderedImages.value = []
  renderedCount.value = 0
  totalCount.value = props.items.length
  hiddenCanvasRefs.value = []

  await nextTick()

  const cfg = props.config
  const qrOptions = {
    width: cfg.size || 258,
    margin: cfg.margin ?? 2,
    color: {
      dark: cfg.fgColor || '#000000',
      light: cfg.bgColor || '#ffffff',
    },
    errorCorrectionLevel: cfg.errorCorrection || 'M',
  }

  const results = []

  for (let i = 0; i < props.items.length; i++) {
    const item = props.items[i]
    const canvas = hiddenCanvasRefs.value[i]

    if (!canvas || !item.url) {
      renderedCount.value++
      continue
    }

    try {
      await QRCode.toCanvas(canvas, item.url, qrOptions)

      // Draw logo if present
      if (cfg.logoDataUrl) {
        const ctx = canvas.getContext('2d')
        const logoImg = new Image()
        await new Promise((resolve) => {
          logoImg.onload = resolve
          logoImg.onerror = resolve
          logoImg.src = cfg.logoDataUrl
        })
        if (logoImg.complete && logoImg.naturalWidth > 0) {
          const logoSize = (cfg.size || 258) * 0.2
          const x = (canvas.width - logoSize) / 2
          const y = (canvas.height - logoSize) / 2
          ctx.fillStyle = cfg.bgColor || '#ffffff'
          ctx.beginPath()
          if (ctx.roundRect) {
            ctx.roundRect(x - 3, y - 3, logoSize + 6, logoSize + 6, 4)
          } else {
            ctx.rect(x - 3, y - 3, logoSize + 6, logoSize + 6)
          }
          ctx.fill()
          ctx.drawImage(logoImg, x, y, logoSize, logoSize)
        }
      }

      const dataUrl = canvas.toDataURL('image/png')
      results.push({
        regionName: item.regionName,
        url: item.url,
        dataUrl,
      })
    } catch {
      // Skip failed renders
    }

    renderedCount.value++
  }

  renderedImages.value = results
  rendering.value = false

  if (results.length === 0 && props.items.length > 0) {
    ElMessage.error('二维码渲染失败')
  }
}

function handlePrint() {
  if (renderedImages.value.length === 0) return

  const sizeLabel = pageSize.value.toUpperCase()
  const cols = columns.value
  const cellWidth = pageSize.value === 'a4' ? `calc((100% - ${(cols - 1) * 12}px) / ${cols})` : `calc((100% - ${(cols - 1) * 8}px) / ${cols})`

  const cellsHtml = renderedImages.value
    .map(
      (img) => `
    <div class="qr-cell">
      <img src="${img.dataUrl}" alt="${img.regionName}" />
      <div class="qr-label">${img.regionName}</div>
    </div>`
    )
    .join('\n')

  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>二维码打印 - ${sizeLabel}</title>
<style>
  @page {
    size: ${sizeLabel};
    margin: 10mm;
  }
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background: #fff;
    font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
    color: #000;
  }
  .print-grid {
    display: grid;
    grid-template-columns: repeat(${cols}, ${cellWidth});
    gap: ${pageSize.value === 'a4' ? '12px' : '8px'};
    padding: 0;
  }
  .qr-cell {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: ${pageSize.value === 'a4' ? '10px' : '6px'};
    page-break-inside: avoid;
    break-inside: avoid;
  }
  .qr-cell img {
    width: ${pageSize.value === 'a4' ? '90%' : '85%'};
    height: auto;
    display: block;
  }
  .qr-label {
    margin-top: ${pageSize.value === 'a4' ? '8px' : '4px'};
    font-size: ${pageSize.value === 'a4' ? '13px' : '11px'};
    font-weight: 600;
    text-align: center;
    word-break: break-all;
  }
  @media print {
    body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  }
</style>
</head>
<body>
<div class="print-grid">
${cellsHtml}
</div>
<script>
  window.onload = function() { window.print(); };
<\/script>
</body>
</html>`

  const printWindow = window.open('', '_blank', 'width=800,height=600')
  if (!printWindow) {
    ElMessage.error('浏览器阻止了弹窗，请允许弹出窗口后重试')
    return
  }
  printWindow.document.write(html)
  printWindow.document.close()
}
</script>

<style scoped>
.print-settings {
  display: flex;
  gap: var(--sp-6);
  margin-bottom: var(--sp-4);
}
.setting-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}
.setting-label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
}

.print-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  padding: var(--sp-12) 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.print-preview-label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: var(--sp-3);
}

.print-preview {
  display: grid;
  gap: var(--sp-3);
  max-height: 50vh;
  overflow-y: auto;
  padding: var(--sp-4);
  background: #fff;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
}
.paper-a4 .qr-cell { padding: var(--sp-2); }
.paper-a5 .qr-cell { padding: var(--sp-1); }

.qr-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  page-break-inside: avoid;
}
.qr-img {
  width: 100%;
  max-width: 180px;
  height: auto;
  display: block;
  border: 1px solid #e0e0e0;
  border-radius: var(--radius-sm);
}
.qr-label {
  font-size: var(--text-xs);
  font-weight: 600;
  color: #333;
  text-align: center;
  word-break: break-all;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-3);
}

.hidden-canvases {
  position: absolute;
  left: -9999px;
  top: -9999px;
  visibility: hidden;
}
.qr-hidden-canvas {
  width: 258px;
  height: 258px;
}
</style>
