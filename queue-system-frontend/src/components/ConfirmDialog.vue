<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="420px"
    draggable
    @update:model-value="$emit('update:visible', $event)"
    @close="handleCancel"
    :close-on-click-modal="false"
  >
    <slot>
      <p>{{ message }}</p>
    </slot>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
const props = defineProps({
  visible: {
    type: Boolean,
    required: true
  },
  title: {
    type: String,
    default: '确认操作'
  },
  message: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:visible', 'confirm', 'cancel'])

const handleConfirm = () => {
  emit('confirm')
  emit('update:visible', false)
}

const handleCancel = () => {
  emit('cancel')
  emit('update:visible', false)
}
</script>
