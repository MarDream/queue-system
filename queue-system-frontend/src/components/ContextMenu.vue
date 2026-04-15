<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="context-menu-overlay"
      @click="close"
      @contextmenu.prevent="close"
    >
      <div
        ref="menuRef"
        class="context-menu"
        :style="{ left: x + 'px', top: y + 'px' }"
        @click.stop
      >
        <div
          v-for="(item, idx) in items"
          :key="idx"
          class="context-menu-item-wrapper"
          @mouseenter="onHover(idx)"
          @mouseleave="onLeave(idx)"
        >
          <div
            :class="['context-menu-item', { disabled: item.disabled, 'has-children': item.children?.length }]"
            @click="item.disabled || item.children?.length ? null : select(item)"
          >
            <span class="item-label">{{ item.label }}</span>
            <span v-if="item.children?.length" class="item-arrow">▶</span>
          </div>
          <!-- 子菜单 -->
          <div
            v-if="item.children?.length && activeSubIdx === idx"
            class="context-menu context-menu-sub"
          >
            <div
              v-for="(child, cIdx) in item.children"
              :key="cIdx"
              :class="['context-menu-item', { disabled: child.disabled, active: child.active }]"
              @click="child.disabled ? null : select(child)"
            >
              <span class="item-label">{{ child.label }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  visible: Boolean,
  x: Number,
  y: Number,
  items: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'select'])
const menuRef = ref(null)
const activeSubIdx = ref(-1)

function onHover(idx) {
  activeSubIdx.value = idx
}

function onLeave(idx) {
  // 延迟关闭，给子菜单留出 hover 时间
  setTimeout(() => {
    if (activeSubIdx.value === idx) {
      activeSubIdx.value = -1
    }
  }, 150)
}

function close() {
  activeSubIdx.value = -1
  emit('close')
}

function select(item) {
  emit('select', item)
  close()
}
</script>

<style scoped>
.context-menu-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
}

.context-menu {
  position: fixed;
  min-width: 140px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
  padding: var(--sp-1) 0;
  z-index: 10000;
}

.context-menu-sub {
  position: absolute;
  left: 100%;
  top: -4px;
  min-width: 160px;
}

.context-menu-item-wrapper {
  position: relative;
}

.context-menu-item {
  padding: var(--sp-2) var(--sp-4);
  font-size: var(--text-sm);
  color: var(--text-primary);
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
}

.context-menu-item:hover {
  background: var(--accent-glow);
  color: var(--accent);
}

.context-menu-item.disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}

.context-menu-item.disabled:hover {
  background: transparent;
}

.context-menu-item.active {
  background: var(--accent-glow);
  color: var(--accent);
  font-weight: 500;
}

.item-arrow {
  font-size: 8px;
  color: var(--text-muted);
}
</style>
