<template>
  <div class="icon-picker">
    <el-input
      :model-value="modelValue"
      placeholder="选择或输入图标"
      clearable
      @input="$emit('update:modelValue', $event)"
    >
      <template #prepend>
        <el-icon v-if="modelValue" :size="18">
          <component :is="modelValue" />
        </el-icon>
        <span v-else class="preview-emoji">{{ modelValue || '无' }}</span>
      </template>
      <template #append>
        <el-button @click="visible = true">
          <el-icon><Grid /></el-icon> 选择
        </el-button>
      </template>
    </el-input>

    <el-dialog v-model="visible" width="680px" :show-close="false" title="选择图标">
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">选择图标</span>
          <el-button circle size="small" class="icon-close-btn" @click="visible = false" title="关闭">
            <svg class="close-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <path d="M15 9l-6 6M9 9l6 6"/>
            </svg>
          </el-button>
        </div>
      </template>

      <!-- 搜索 + 类型切换 -->
      <div class="picker-toolbar">
        <el-radio-group v-model="iconType" size="small">
          <el-radio-button value="element">Element Plus 图标</el-radio-button>
          <el-radio-button value="emoji">Emoji 图标</el-radio-button>
        </el-radio-group>
        <el-input v-model="searchKeyword" placeholder="搜索图标名称..." clearable size="small" style="width:200px" />
      </div>

      <!-- Element Plus 图标网格 -->
      <div v-if="iconType === 'element'" class="icon-grid">
        <div
          v-for="icon in filteredElementIcons"
          :key="icon"
          class="icon-cell"
          :class="{ selected: modelValue === icon }"
          @click="selectIcon(icon)"
          :title="icon"
        >
          <el-icon :size="22">
            <component :is="icon" />
          </el-icon>
          <span class="icon-name">{{ icon }}</span>
        </div>
      </div>

      <!-- Emoji 图标网格 -->
      <div v-else class="icon-grid">
        <div
          v-for="emoji in filteredEmojis"
          :key="emoji"
          class="icon-cell"
          :class="{ selected: modelValue === emoji }"
          @click="selectIcon(emoji)"
          :title="emoji"
        >
          <span class="emoji-cell">{{ emoji }}</span>
          <span class="icon-name">{{ emoji }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import {
  Grid,
  House, HomeFilled, Star, StarFilled, Bell, Monitor, Setting, Tools,
  Menu, Operation, Document, Folder, FolderOpened, Files, FolderChecked,
  User, UserFilled, Avatar, Service, Guide,
  Location, Place, MapLocation,
  Tickets, Calendar, Clock,
  OfficeBuilding, School, Shop,
  ChatDotRound, ChatLineRound, Message, Connection, Link,
  Picture, Camera, VideoCamera,
  Edit, EditPen, Delete, Close,
  Plus, Minus, Search, Refresh, RefreshRight,
  Check, CircleCheck, SuccessFilled, Finished,
  Warning, WarningFilled, InfoFilled, QuestionFilled,
  Lock, Unlock, Key,
  DataAnalysis, DataLine, TrendCharts, Histogram, PieChart,
  List, Notebook, DocumentAdd,
  Sort, Top, Bottom, Back,
  Upload, Download,
  Share, More, Expand, Fold,
  View, Hide, ZoomIn, ZoomOut,
  Cpu, Van,
  Medal, Trophy,
  MagicStick, Sunny, Moon,
  Box, Goods, GoodsFilled,
  Suitcase, FirstAidKit, Reading,
  Coffee, CoffeeCup
} from '@element-plus/icons-vue'

defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const iconType = ref('element')
const searchKeyword = ref('')

function selectIcon(icon) {
  emit('update:modelValue', icon)
  visible.value = false
}

// 精选 Element Plus 图标
const elementIcons = [
  'House', 'HomeFilled', 'Star', 'StarFilled', 'Bell', 'Monitor', 'Setting', 'Tools',
  'Menu', 'Operation', 'Document', 'Folder', 'FolderOpened', 'FolderChecked',
  'User', 'UserFilled', 'Avatar', 'Service', 'Guide',
  'Location', 'Place', 'MapLocation',
  'Tickets', 'Calendar', 'Clock',
  'OfficeBuilding', 'School', 'Shop',
  'ChatDotRound', 'ChatLineRound', 'Message', 'Connection', 'Link',
  'Picture', 'Camera', 'VideoCamera',
  'Edit', 'EditPen', 'Delete', 'Close',
  'Plus', 'Minus', 'Search', 'Refresh', 'RefreshRight',
  'Check', 'CircleCheck', 'SuccessFilled', 'Finished',
  'Warning', 'WarningFilled', 'InfoFilled', 'QuestionFilled',
  'Lock', 'Unlock', 'Key',
  'DataAnalysis', 'DataLine', 'TrendCharts', 'Histogram', 'PieChart',
  'List', 'Notebook', 'DocumentAdd',
  'Sort', 'Top', 'Bottom', 'Back',
  'Upload', 'Download', 'Share', 'More', 'Expand', 'Fold',
  'View', 'Hide', 'ZoomIn', 'ZoomOut',
  'Cpu', 'Van',
  'Medal', 'Trophy',
  'MagicStick', 'Sunny', 'Moon',
  'Box', 'Goods', 'GoodsFilled',
  'Suitcase', 'FirstAidKit', 'Reading',
  'Coffee', 'Cup'
]

// 精选 Emoji 图标
const emojis = [
  '🏠', '📊', '🗺️', '📋', '🪟', '📱', '👤', '📝', '🎯', '📺',
  '⚙️', '🔧', '🔑', '🔒', '🔓', '🗂️', '📁', '📂', '📄', '📃',
  '🏢', '🏥', '🏫', '🏦', '🏪', '🏬', '🏨', '🏗️', '🏛️', '🏟️',
  '📞', '💬', '✉️', '📧', '🔗', '🌐', '💻', '🖥️', '📡', '📶',
  '🎨', '🖼️', '📷', '🎬', '🎥', '📹', '🎵', '🎶', '🔊', '🔇',
  '✏️', '🖊️', '✂️', '🗑️', '❌', '➕', '➖', '🔍', '🔎', '🔄',
  '✅', '⭐', '🌟', '💡', '🔔', '🔕', '🎪', '🎭', '🎯', '🏆',
  '🥇', '🎖️', '🏅', '🎗️', '🎀', '🎁', '📦', '📫', '📪', '📬',
  '🚀', '🚗', '🚙', '🚌', '🚎', '🏎️', '🚑', '🚒', '🚐', '🛻',
  '☀️', '🌙', '🌅', '🌄', '⛅', '🌈', '❄️', '🌊', '🍵', '☕',
  '💼', '🧳', '🎒', '👛', '👔', '🧑‍💼', '👨‍⚕️', '👩‍🏫', '👨‍🍳', '👨‍🔧',
  '🏠', '🏡', '🏘️', '🏚️', '🏗️', '🏢', '🏣', '🏤', '🏥', '🏦'
]

const filteredElementIcons = computed(() => {
  if (!searchKeyword.value) return elementIcons
  const kw = searchKeyword.value.toLowerCase()
  return elementIcons.filter(name => name.toLowerCase().includes(kw))
})

const filteredEmojis = computed(() => {
  if (!searchKeyword.value) return emojis
  // Emoji 搜索仅做简单匹配
  return emojis
})
</script>

<style scoped>
.icon-picker :deep(.el-input__prepend) {
  padding: 0 8px;
  min-width: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.preview-emoji {
  font-size: 16px;
}

.picker-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
  gap: 6px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px;
}

.icon-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 4px;
  border-radius: 8px;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;
  background: var(--bg-raised, #f5f7fa);
}
.icon-cell:hover {
  border-color: var(--accent, #409eff);
  background: rgba(0, 229, 255, 0.06);
  transform: translateY(-1px);
}
.icon-cell.selected {
  border-color: var(--accent, #409eff);
  background: rgba(0, 229, 255, 0.1);
  box-shadow: 0 0 0 1px var(--accent, #409eff);
}

.icon-name {
  font-size: 9px;
  color: var(--text-muted, #999);
  text-align: center;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.emoji-cell {
  font-size: 22px;
  line-height: 1;
}
</style>
