<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <span class="live-dot"></span>
        <h1 class="title"><img src="/favicon.png" class="brand-icon" alt="QMS" /> 排队系统</h1>
        <p class="subtitle">Queue Management System</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
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

        // 根据角色跳转
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
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-void);
  position: relative;
  overflow: hidden;
  padding: var(--sp-4);
}

/* Background gradient accent */
.login-page::before {
  content: '';
  position: absolute;
  top: -20%; right: -10%;
  width: 60vw;
  height: 60vw;
  max-width: 800px;
  max-height: 800px;
  background: radial-gradient(circle, var(--accent-glow) 0%, transparent 70%);
  opacity: 0.4;
  pointer-events: none;
}

.login-page::after {
  content: '';
  position: absolute;
  bottom: -20%; left: -10%;
  width: 40vw;
  height: 40vw;
  max-width: 500px;
  max-height: 500px;
  background: radial-gradient(circle, rgba(138,43,226,0.1) 0%, transparent 70%);
  opacity: 0.5;
  pointer-events: none;
}

.login-container {
  width: 100%;
  max-width: 420px;
  padding: var(--sp-10);
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg), 0 0 60px rgba(0,229,255,0.05);
  position: relative;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: var(--sp-8);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
}

.login-header .live-dot {
  margin-bottom: var(--sp-2);
}

.title {
  font-size: var(--text-2xl);
  color: var(--text-primary);
  font-weight: 700;
  letter-spacing: 0.04em;
  line-height: 1.3;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
}
.title .brand-icon {
  height: 1.2em;
  width: auto;
}

.subtitle {
  font-size: var(--text-xs);
  color: var(--text-muted);
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.login-form {
  margin-top: var(--sp-6);
}

.login-btn {
  width: 100%;
  min-height: var(--touch-lg);
  font-size: var(--text-base);
  border-radius: var(--radius-md);
}

.login-footer {
  text-align: center;
  margin-top: var(--sp-8);
  padding-top: var(--sp-6);
  border-top: 1px solid var(--border);
}

.version {
  font-size: var(--text-xs);
  color: var(--text-muted);
  font-family: var(--mono);
}

/* Tablet */
@media (max-width: 768px) {
  .login-container {
    padding: var(--sp-8);
    max-width: 100%;
  }

  .title {
    font-size: var(--text-xl);
  }
}

/* Mobile */
@media (max-width: 480px) {
  .login-page {
    padding: var(--sp-3);
    align-items: flex-start;
    padding-top: 15vh;
  }

  .login-container {
    padding: var(--sp-6);
    border-radius: var(--radius-md);
    box-shadow: var(--shadow-md);
  }

  .login-header {
    margin-bottom: var(--sp-6);
  }

  .title {
    font-size: var(--text-lg);
  }
}
</style>
