<template>
  <div class="login-shell" :style="{ '--login-bg-image': `url(${brandImage})` }">
    <section class="login-panel" aria-label="登录">
      <div class="brand-header">
        <img class="brand-image" :src="brandImage" alt="东莞城市品牌" />
      </div>
      <div class="brand-divider"></div>

      <div class="login-card">
        <div class="login-header">
          <h1 class="platform-title">东莞市一体化运营运维管理平台</h1>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="handleLogin">
          <el-form-item prop="username" class="input-item">
            <el-input
              v-model="form.username"
              :disabled="loading"
              :prefix-icon="User"
              placeholder="用户名/邮箱/手机号"
              size="large"
              autocomplete="username"
              clearable
            />
          </el-form-item>

          <el-form-item prop="password" class="input-item">
            <el-input
              v-model="form.password"
              :disabled="loading"
              :prefix-icon="Lock"
              :type="showPassword ? 'text' : 'password'"
              placeholder="密码"
              size="large"
              autocomplete="current-password"
            >
              <template #suffix>
                <el-icon class="pwd-toggle" :class="{ 'is-disabled': loading }" @click.stop="loading ? null : (showPassword = !showPassword)">
                  <component :is="showPassword ? View : Hide" />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item class="submit-item">
            <el-button type="primary" size="large" :loading="loading" class="login-btn" @click="handleLogin">
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="link-actions" role="navigation" aria-label="登录辅助入口">
          <button class="text-link" type="button" :disabled="loading" @click="router.push('/register')">注册账号</button>
          <span class="link-separator" aria-hidden="true"></span>
          <button class="text-link" type="button" :disabled="loading" @click="router.push('/forgot-password')">忘记密码</button>
        </div>

        <div class="version-meta">
          <span>版本 {{ displayVersion }}</span>
          <span v-if="versionMismatch" class="version-warning">前端 {{ frontendVersion }} / 后端 {{ backendVersion }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { View, Hide, User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import brandImage from '../assets/login/bk_login.png'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const showPassword = ref(false)
const frontendVersion = __APP_VERSION__
const backendVersion = ref('')

const displayVersion = computed(() => {
  return backendVersion.value || frontendVersion
})

const versionMismatch = computed(() => {
  return backendVersion.value && backendVersion.value !== frontendVersion
})

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(() => {
  loadVersion()
})

async function loadVersion() {
  try {
    const res = await axios.get('/api/v1/meta/version')
    const version = res?.data?.data?.version
    if (typeof version === 'string' && version.trim()) {
      backendVersion.value = version.trim()
    }
  } catch (err) {
    console.warn('Failed to load backend version', err)
  }
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
</script>

<style scoped>
.login-shell {
  --panel-border: rgba(144, 159, 179, 0.34);
  --panel-shadow: 0 18px 48px rgba(30, 72, 111, 0.14);

  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 32px 16px;
  background: linear-gradient(180deg, #eef5fb 0%, #f8fbff 100%);
}

.login-shell::before,
.login-shell::after {
  content: '';
  position: absolute;
  inset: 0;
}

.login-shell::before {
  background-image: var(--login-bg-image);
  background-repeat: no-repeat;
  background-position: center center;
  background-size: min(1120px, 88vw) auto;
  opacity: 0.18;
  transform: scale(1.04);
}

.login-shell::after {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.94)),
    radial-gradient(circle at top right, rgba(66, 170, 103, 0.08), transparent 30%);
}

.login-panel {
  width: min(100%, 460px);
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--panel-border);
  border-radius: 12px;
  box-shadow: var(--panel-shadow);
  backdrop-filter: blur(10px);
  overflow: hidden;
}

.brand-header {
  padding: 24px 24px 18px;
  display: flex;
  justify-content: center;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 251, 255, 0.92));
}

.brand-image {
  width: min(100%, 226px);
  display: block;
}

.brand-divider {
  height: 1px;
  background: rgba(155, 170, 189, 0.28);
}

.login-card {
  padding: 40px 42px 28px;
}

.login-header {
  margin-bottom: 26px;
}

.platform-title {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.45;
  color: #68788d;
  text-align: center;
  letter-spacing: 0.01em;
}

.login-form {
  margin-top: 12px;
}

.input-item {
  margin-bottom: 18px;
}

.login-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.login-card :deep(.el-input__wrapper) {
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid #d7dfea !important;
  border-radius: 0 !important;
  background: #ffffff !important;
  box-shadow: none !important;
  transition: border-color var(--duration-fast) var(--ease-out), background-color var(--duration-fast) var(--ease-out);
}

.login-card :deep(.el-input__wrapper:hover) {
  border-color: #bdd0e4 !important;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  border-color: #8eaac8 !important;
  background: #ffffff !important;
}

.login-card :deep(.el-input__inner) {
  font-size: 15px;
  color: #5f6f84;
}

.login-card :deep(.el-input__inner::placeholder) {
  color: #c1c7d2;
}

.login-card :deep(.el-input__prefix-inner) {
  color: #a4afbf;
  font-size: 16px;
}

.login-card :deep(.el-form-item__error) {
  font-size: 12px;
  padding-top: 6px;
}

.pwd-toggle {
  cursor: pointer;
  color: #9ba7b7;
  transition: color var(--duration-fast) var(--ease-out);
}

.pwd-toggle:hover {
  color: #6a8fb7;
}

.pwd-toggle.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.submit-item {
  margin-top: 24px;
}

.login-btn {
  width: 100%;
  min-height: 44px;
  border-radius: 2px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.08em;
  box-shadow: 0 10px 24px rgba(0, 82, 217, 0.16);
}

.link-actions {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.text-link {
  border: 0;
  background: transparent;
  padding: 0;
  color: #7c8fa5;
  font-size: 13px;
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out);
}

.text-link:hover {
  color: var(--primary);
}

.text-link:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.link-separator {
  width: 1px;
  height: 11px;
  background: #d5dde7;
}

.version-meta {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #a1acba;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}

.version-warning {
  color: #c68c33;
}

@media (max-width: 560px) {
  .login-shell {
    padding: 16px;
  }

  .login-shell::before {
    background-size: 120vw auto;
    opacity: 0.14;
  }

  .brand-header {
    padding: 20px 16px 16px;
  }

  .brand-image {
    width: min(100%, 200px);
  }

  .login-card {
    padding: 30px 20px 22px;
  }

  .platform-title {
    font-size: 18px;
  }
}
</style>
