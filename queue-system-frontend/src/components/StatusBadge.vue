<template>
  <span :class="['badge', badgeClass]">{{ displayText }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    required: true
  },
  type: {
    type: String,
    default: 'ticket'
  }
})

const statusMap = {
  waiting: { text: '等待中', class: 'badge-waiting' },
  called: { text: '已叫号', class: 'badge-called' },
  serving: { text: '办理中', class: 'badge-serving' },
  completed: { text: '已办结', class: 'badge-done' },
  done: { text: '已办结', class: 'badge-done' },
  skipped: { text: '已过号', class: 'badge-skipped' },
  cancelled: { text: '已取消', class: 'badge-skipped' },
  idle: { text: '空闲', class: 'badge-waiting' },
  busy: { text: '忙碌', class: 'badge-called' },
  paused: { text: '暂停', class: 'badge-skipped' }
}

const badgeClass = computed(() => {
  const key = props.status?.toLowerCase()
  return statusMap[key]?.class || 'badge-waiting'
})

const displayText = computed(() => {
  const key = props.status?.toLowerCase()
  return statusMap[key]?.text || props.status || ''
})
</script>
