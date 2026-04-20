<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <div class="logo-mark">
          <img src="/favicon.png" alt="QMS" />
        </div>
        <h1 class="login-title">排队叫号系统</h1>
        <p class="login-subtitle">Queue Management System</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
        <div class="field-group">
          <label class="field-label">用户名</label>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">密码</label>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" @keyup.enter="handleLogin" />
          </el-form-item>
        </div>

        <el-form-item class="submit-item">
          <el-button type="primary" size="large" :loading="loading" class="login-btn" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span class="version">v1.0.0</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await axios.post('/api/v1/auth/login', form)
      if (res.data.code === 200) {
        const userData = res.data.data
        userStore.setUser(userData)
        ElMessage.success('登录成功')

        if (userData.role === 'WINDOW_OPERATOR') {
          router.push('/counter')
        } else {
          router.push('/admin')
        }
      } else {
        ElMessage.error(res.data.message || '登录失败')
      }
    } catch (err) {
      const msg = err.response?.data?.message || '登录失败，请检查用户名和密码'
      ElMessage.error(msg)
    } finally {
      loading.value = false
    }
  })
}

// 监听用户活动事件
function recordActivity() {
  userStore.recordActivity()
}

// 添加活动监听器
const events = ['mousedown', 'mousemove', 'keydown', 'scroll', 'touchstart', 'click']
events.forEach(event => {
  document.addEventListener(event, recordActivity, { passive: true })
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(0, 82, 217, 0.08) 0%, rgba(0, 82, 217, 0.02) 42%, transparent 72%),
    var(--bg-body);
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-mark {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
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

.login-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.login-form {
  margin-top: 0;
}

.field-group {
  margin-bottom: 20px;
}

.field-label {
  display: block;
  margin-bottom: 8px;
  font-size: var(--text-sm);
  color: var(--text-primary);
  font-weight: 500;
}

.login-page :deep(.el-form-item) {
  margin-bottom: 0;
}

.login-page :deep(.el-input__wrapper) {
  min-height: 40px;
}

.login-page :deep(.el-form-item__error) {
  font-size: 12px;
  padding-top: 4px;
}

.submit-item {
  margin-top: 8px;
}

.login-btn {
  width: 100%;
  min-height: 40px;
  font-size: var(--text-base);
}

.login-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
  text-align: center;
}

.version {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

@media (max-width: 480px) {
  .login-page {
    padding: 16px;
    align-items: flex-start;
    padding-top: 12vh;
  }

  .login-card {
    padding: 28px 20px;
  }

  .login-title {
    font-size: 24px;
  }
}
</style>
