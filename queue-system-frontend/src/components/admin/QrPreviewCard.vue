<template>
  <div class="qr-preview-card" :class="'template-' + config.template">
    <div v-if="config.template === 'poster'" class="poster-header">
      <h4>{{ regionName }}</h4>
      <span class="poster-subtitle">扫码取号</span>
    </div>
    <canvas ref="canvasRef" class="qr-canvas" :style="{ width: config.size + 'px', height: config.size + 'px' }"></canvas>
    <div v-if="showBottomText" class="qr-bottom-text">
      {{ bottomText || '扫码取号' }}
    </div>
  </div>
</template>
<script setup>
import { ref, watch, onMounted, nextTick, computed } from 'vue'
import QRCode from 'qrcode'

const props = defineProps({
  url: { type: String, default: '' },
  regionName: { type: String, default: '' },
  bottomText: { type: String, default: '' },
  showBottomText: { type: Boolean, default: true },
  config: {
    type: Object,
    default: () => ({
      size: 258,
      fgColor: '#000000',
      bgColor: '#ffffff',
      margin: 2,
      errorCorrection: 'M',
      logoDataUrl: '',
      template: 'simple',
    }),
  },
})

const canvasRef = ref(null)

const displayUrl = computed(() => {
  if (!props.url) return ''
  try {
    const u = new URL(props.url)
    return u.hostname + u.pathname
  } catch {
    return props.url
  }
})

let renderTimer = null

watch(
  [() => props.url, () => props.config],
  async () => {
    clearTimeout(renderTimer)
    renderTimer = setTimeout(async () => {
      await nextTick()
      await renderQr()
    }, 50)
  },
  { deep: true, immediate: true }
)

async function renderQr() {
  const canvas = canvasRef.value
  if (!canvas || !props.url) return

  try {
    await QRCode.toCanvas(canvas, props.url, {
      width: props.config.size,
      margin: props.config.margin,
      color: { dark: props.config.fgColor, light: props.config.bgColor },
      errorCorrectionLevel: props.config.errorCorrection,
    })

    if (props.config.logoDataUrl) {
      const ctx = canvas.getContext('2d')
      const logo = new Image()
      logo.crossOrigin = 'anonymous'
      logo.onload = () => {
        const logoSize = props.config.size * 0.2
        const x = (canvas.width - logoSize) / 2
        const y = (canvas.height - logoSize) / 2
        ctx.fillStyle = props.config.bgColor
        ctx.beginPath()
        if (ctx.roundRect) {
          ctx.roundRect(x - 4, y - 4, logoSize + 8, logoSize + 8, 4)
        } else {
          ctx.rect(x - 4, y - 4, logoSize + 8, logoSize + 8)
        }
        ctx.fill()
        ctx.drawImage(logo, x, y, logoSize, logoSize)
      }
      logo.onerror = () => {
        // silently ignore broken logo
      }
      logo.src = props.config.logoDataUrl
    }
  } catch {
    // QRCode.toCanvas may fail on invalid URL — silently ignore
  }
}

onMounted(async () => {
  await nextTick()
  await renderQr()
})
</script>

<style scoped>
.qr-preview-card {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-5);
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  border: 1px solid var(--border);
}

.poster-header,
.poster-footer {
  text-align: center;
}

.poster-header h4 {
  margin: 0 0 var(--sp-1) 0;
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--text-primary);
}

.poster-subtitle {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.poster-footer .poster-url {
  font-size: var(--text-xs);
  color: var(--text-muted);
  font-family: var(--mono);
}

.qr-canvas {
  border-radius: var(--radius-md);
  image-render: pixelated;
}

.qr-bottom-text {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-primary);
  text-align: center;
  margin-top: var(--sp-2);
}

/* Poster template gets a light background for scannability */
.template-poster {
  background: #ffffff;
  border-color: var(--border);
}

.template-poster .poster-header h4 {
  color: #1a1a1a;
}

.template-poster .poster-subtitle {
  color: #666;
}

.template-poster .poster-url {
  color: #999 !important;
}

/* Tablet */
@media (max-width: 768px) {
  .qr-preview-card {
    padding: var(--sp-4);
  }
}
</style>
