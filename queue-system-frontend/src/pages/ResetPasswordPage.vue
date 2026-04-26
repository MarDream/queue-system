<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <div class="logo-mark">
          <img src="/favicon.png" alt="QMS" />
        </div>
        <h1 class="auth-title">重置密码</h1>
        <p class="auth-subtitle">{{ subtitle }}</p>
      </div>

      <el-form v-if="!isOldMode" ref="formRef" :model="form" :rules="rules" class="auth-form">
        <div class="field-group">
          <label class="field-label">邮箱</label>
          <el-form-item prop="email">
            <el-input v-model="form.email" placeholder="请输入注册邮箱" size="large" />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">验证码</label>
          <el-form-item prop="code">
            <el-input v-model="form.code" placeholder="请输入验证码" size="large" />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">新密码</label>
          <el-form-item prop="newPassword">
            <el-input v-model="form.newPassword" type="password" show-password placeholder="至少6位" size="large" />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">确认新密码</label>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" size="large" />
          </el-form-item>
        </div>

        <el-form-item class="submit-item">
          <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="handleReset">
            确认重置
          </el-button>
        </el-form-item>
      </el-form>

      <div v-else class="auth-form">
        <div v-if="!userStore.isLoggedIn" class="login-tip">
          <el-button type="primary" size="large" class="submit-btn" @click="router.push('/login')">先登录再修改密码</el-button>
        </div>
        <el-form v-else ref="oldFormRef" :model="oldForm" :rules="oldRules" class="auth-form">
          <div class="field-group">
            <label class="field-label">原密码</label>
            <el-form-item prop="oldPassword">
              <el-input v-model="oldForm.oldPassword" type="password" show-password placeholder="请输入原密码" size="large" />
            </el-form-item>
          </div>

          <div class="field-group">
            <label class="field-label">新密码</label>
            <el-form-item prop="newPassword">
              <el-input v-model="oldForm.newPassword" type="password" show-password placeholder="至少6位" size="large" />
            </el-form-item>
          </div>

          <div class="field-group">
            <label class="field-label">确认新密码</label>
            <el-form-item prop="confirmPassword">
              <el-input v-model="oldForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" size="large" />
            </el-form-item>
          </div>

          <el-form-item class="submit-item">
            <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="handleChangeByOldPassword">
              确认修改
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="auth-footer">
        <template v-if="!isOldMode">
          <el-button link type="primary" @click="router.push('/forgot-password')">重新发送验证码</el-button>
          <el-divider direction="vertical" />
          <el-button link type="primary" @click="router.push('/reset-password?mode=old')">使用原密码重置</el-button>
          <el-divider direction="vertical" />
          <el-button link type="primary" @click="router.push('/login')">返回登录</el-button>
        </template>
        <template v-else>
          <el-button link type="primary" @click="router.push('/forgot-password')">通过邮箱重置</el-button>
          <el-divider direction="vertical" />
          <el-button link type="primary" @click="router.push('/login')">返回登录</el-button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import request from '../api'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const oldFormRef = ref(null)
const loading = ref(false)

const isOldMode = computed(() => String(route.query.mode || '') === 'old')
const subtitle = computed(() => {
  if (isOldMode.value) return '输入原密码并设置新密码'
  return '输入邮箱验证码并设置新密码'
})

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const oldForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const rules = {
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_, value, cb) => {
        if (value !== form.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const oldRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_, value, cb) => {
        if (value !== oldForm.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function handleReset() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (String(form.newPassword || '').length < 6) {
      ElMessage.warning('密码至少6位')
      return
    }
    loading.value = true
    try {
      const res = await axios.post('/api/v1/auth/password-reset/confirm', {
        email: form.email,
        code: form.code,
        newPassword: form.newPassword
      })
      if (res.data?.code === 200) {
        ElMessage.success('密码已重置，请使用新密码登录')
        router.push('/login')
      } else {
        ElMessage.error(res.data?.message || '重置失败')
      }
    } catch (err) {
      const msg = err.response?.data?.message || '重置失败'
      ElMessage.error(msg)
    } finally {
      loading.value = false
    }
  })
}

async function handleChangeByOldPassword() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  if (!oldFormRef.value) return
  await oldFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (String(oldForm.newPassword || '').length < 6) {
      ElMessage.warning('密码至少6位')
      return
    }
    loading.value = true
    try {
      await request.post('/auth/password/change', {
        oldPassword: oldForm.oldPassword,
        newPassword: oldForm.newPassword
      })
      ElMessage.success('密码已修改，请使用新密码重新登录')
      userStore.logout(true)
      router.push('/login')
    } catch (e) {
      ElMessage.error(e.message || '修改失败')
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  const email = route.query.email ? String(route.query.email) : ''
  const code = route.query.code ? String(route.query.code) : ''
  if (email) form.email = email
  if (code) form.code = code
})
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(0, 82, 217, 0.08) 0%, rgba(0, 82, 217, 0.02) 42%, transparent 72%),
    var(--bg-body);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}

.auth-header {
  text-align: center;
  margin-bottom: 28px;
}

.logo-mark {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 14px;
  background: var(--primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-mark img {
  width: 34px;
  height: 34px;
}

.auth-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.auth-subtitle {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.field-group {
  margin-bottom: 16px;
}

.field-label {
  display: block;
  margin-bottom: 8px;
  font-size: var(--text-sm);
  color: var(--text-primary);
  font-weight: 500;
}

.submit-item {
  margin-top: 8px;
}

.submit-btn {
  width: 100%;
  min-height: 40px;
}

.auth-footer {
  margin-top: 8px;
  text-align: center;
}

.login-tip {
  margin-top: 8px;
}
</style>
