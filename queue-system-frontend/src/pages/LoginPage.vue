<template>
  <div class="login-shell">
    <div class="login-layout">
      <section class="brand-panel" aria-label="系统介绍">
        <div class="brand-topline">
          <span class="brand-topline__badge">智慧政务大厅</span>
          <span class="brand-topline__text">统一排队 · 实时叫号 · 全流程可视</span>
        </div>

        <div class="brand-head">
          <div class="brand-mark">
            <div class="brand-mark__core">Q</div>
          </div>
          <div>
            <h1 class="brand-title">排队叫号系统</h1>
            <p class="brand-subtitle">Queue Management System</p>
          </div>
        </div>

        <p class="brand-description">
          面向政务服务大厅的一体化排队叫号平台，覆盖自助取号、窗口联动、等候引导和后台配置，
          让群众办事路径更清晰、窗口协同更高效。
        </p>

        <div class="service-tags">
          <span class="service-tag">现场取号</span>
          <span class="service-tag">扫码预约</span>
          <span class="service-tag">窗口叫号</span>
          <span class="service-tag">大屏联动</span>
        </div>

        <div class="hall-visual">
          <div class="hall-stage">
            <div class="queue-screen">
              <div class="queue-screen__bar"></div>
              <div class="queue-screen__content">
                <span class="queue-screen__label">大厅总览</span>
                <div class="queue-screen__numbers">
                  <div>
                    <strong>06</strong>
                    <span>等待中</span>
                  </div>
                  <div>
                    <strong>03</strong>
                    <span>办理中</span>
                  </div>
                  <div>
                    <strong>04</strong>
                    <span>窗口在线</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="kiosk-card kiosk-card--secondary">
              <div class="spotlight-card">
                <div class="spotlight-card__top">
                  <span class="spotlight-card__label">当前叫号</span>
                  <span class="spotlight-card__badge">窗口 03</span>
                </div>
                <div class="spotlight-card__ticket">A-018</div>
                <div class="spotlight-card__meta">
                  <span class="meta-pill">预计等待 6 分钟</span>
                  <span class="meta-pill">状态：办理中</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <ul class="feature-list">
          <li class="feature-item feature-item--blue">扫码取号，减少排队拥堵</li>
          <li class="feature-item feature-item--green">窗口叫号，实时联动大屏</li>
          <li class="feature-item feature-item--orange">区域、窗口、业务统一管理</li>
        </ul>

        <div class="brand-foot">
          <span class="foot-pill">安全登录</span>
          <span class="foot-pill">多角色</span>
          <span class="foot-pill">v{{ displayVersion }}</span>
        </div>
      </section>

      <section class="login-panel" aria-label="登录">
        <div class="login-card">
          <div class="login-card__header">
            <h2>欢迎回来</h2>
            <p>请使用你的账号密码登录</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="handleLogin">
            <el-form-item prop="username" class="form-item">
              <template #label>用户名</template>
              <el-input
                v-model="form.username"
                :disabled="loading"
                placeholder="请输入用户名"
                size="large"
                autocomplete="username"
                clearable
              />
            </el-form-item>

            <el-form-item prop="password" class="form-item">
              <template #label>密码</template>
              <el-input
                v-model="form.password"
                :disabled="loading"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
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

          <div class="quick-links">
            <button class="quick-link" type="button" :disabled="loading" @click="router.push('/register')">
              <span class="quick-link__icon">
                <el-icon><User /></el-icon>
              </span>
              <span class="quick-link__text">注册账号</span>
              <span class="quick-link__arrow">›</span>
            </button>
            <button class="quick-link" type="button" :disabled="loading" @click="router.push('/forgot-password')">
              <span class="quick-link__icon">
                <el-icon><Lock /></el-icon>
              </span>
              <span class="quick-link__text">忘记密码</span>
              <span class="quick-link__arrow">›</span>
            </button>
          </div>

          <p v-if="versionMismatch" class="version-warning">
            前端 {{ frontendVersion }} / 后端 {{ backendVersion }}
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { View, Hide, User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'

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
  min-height: 100vh;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at top left, rgba(76, 134, 255, 0.18), transparent 26%),
    radial-gradient(circle at top right, rgba(94, 211, 186, 0.14), transparent 24%),
    radial-gradient(circle at bottom left, rgba(108, 152, 232, 0.1), transparent 20%),
    linear-gradient(180deg, #f2f7ff 0%, #f8fbff 100%);
  position: relative;
  overflow: hidden;
}

.login-shell::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(159, 185, 219, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(159, 185, 219, 0.08) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
}

.login-layout {
  position: relative;
  z-index: 1;
  width: min(1280px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(360px, 0.82fr);
  gap: 24px;
  align-items: stretch;
}

.brand-panel {
  min-height: 640px;
  padding: 26px 28px 28px;
  border-radius: 22px;
  border: 1px solid rgba(105, 149, 222, 0.2);
  background:
    linear-gradient(180deg, rgba(234, 242, 255, 0.96), rgba(244, 248, 255, 0.94)),
    linear-gradient(135deg, rgba(255, 255, 255, 0.22), transparent 50%);
  box-shadow: 0 28px 60px rgba(44, 88, 154, 0.1);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.brand-panel::before {
  content: '';
  position: absolute;
  inset: auto -8% -18% auto;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(103, 180, 255, 0.18), transparent 68%);
  pointer-events: none;
}

.brand-topline {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.brand-topline__badge {
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(36, 108, 229, 0.1);
  color: #2c6fe6;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
}

.brand-topline__text {
  color: #74849a;
  font-size: 12px;
  font-weight: 600;
}

.brand-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.brand-mark {
  width: 68px;
  height: 68px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  display: grid;
  place-items: center;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.brand-mark__core {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2f8fff, #285ddb);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.brand-title {
  margin: 0;
  font-size: clamp(26px, 2.2vw, 40px);
  line-height: 1.12;
  color: #2f3137;
  font-weight: 800;
}

.brand-subtitle {
  margin: 6px 0 0;
  font-size: 15px;
  color: #67788e;
}

.brand-description {
  margin: 18px 0 0;
  max-width: 620px;
  font-size: 14px;
  line-height: 1.7;
  color: #68788d;
}

.service-tags {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.service-tag {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(188, 206, 232, 0.9);
  color: #53647a;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
}

.hall-visual {
  margin-top: 16px;
  position: relative;
}

.hall-stage {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 12px;
  align-items: stretch;
}

.queue-screen,
.kiosk-card {
  border-radius: 18px;
  border: 1px solid rgba(190, 209, 235, 0.88);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 18px 32px rgba(64, 98, 154, 0.08);
}

.queue-screen {
  min-height: 156px;
  padding: 12px;
  position: relative;
  overflow: hidden;
}

.queue-screen__bar {
  height: 8px;
  width: 92px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(56, 127, 239, 0.8), rgba(56, 127, 239, 0.08));
}

.queue-screen__content {
  margin-top: 12px;
}

.queue-screen__label {
  color: #617186;
  font-size: 13px;
  font-weight: 700;
}

.queue-screen__numbers {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.queue-screen__numbers div {
  min-height: 78px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(245, 249, 255, 0.96), rgba(255, 255, 255, 0.96));
  border: 1px solid rgba(215, 225, 239, 0.94);
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.queue-screen__numbers strong {
  font-size: 26px;
  line-height: 1;
  color: #2e3137;
}

.queue-screen__numbers span {
  color: #77879d;
  font-size: 12px;
  font-weight: 600;
}

.kiosk-card {
  padding: 10px;
  display: flex;
  flex-direction: column;
}

.kiosk-card--secondary {
  justify-content: center;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 16px 0 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feature-item {
  position: relative;
  padding-left: 22px;
  font-size: 15px;
  line-height: 1.45;
  color: #6c7686;
}

.feature-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.55em;
  width: 12px;
  height: 12px;
  border-radius: 50%;
}

.feature-item--blue::before {
  background: #1e63df;
}

.feature-item--green::before {
  background: #31c78f;
}

.feature-item--orange::before {
  background: #ff9f0f;
}

.spotlight-card {
  padding: 16px 16px 14px;
  border-radius: 16px;
  background:
    linear-gradient(120deg, rgba(221, 235, 255, 0.85) 0%, rgba(255, 255, 255, 0.98) 30%, rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(120, 161, 234, 0.24);
  box-shadow: none;
}

.spotlight-card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.spotlight-card__label {
  color: #5a6679;
  font-size: 14px;
  font-weight: 600;
}

.spotlight-card__badge {
  padding: 6px 12px;
  border-radius: 999px;
  color: #2c73e8;
  font-size: 12px;
  font-weight: 700;
  background: rgba(68, 132, 245, 0.12);
  border: 1px solid rgba(68, 132, 245, 0.24);
}

.spotlight-card__ticket {
  margin-top: 18px;
  font-size: clamp(34px, 3.4vw, 56px);
  font-weight: 800;
  line-height: 1;
  color: #2f3137;
}

.spotlight-card__meta {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.meta-pill,
.foot-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #616b79;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(202, 214, 230, 0.9);
}

.brand-foot {
  margin-top: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.login-panel {
  min-height: 640px;
  display: flex;
}

.login-card {
  width: 100%;
  padding: 40px 34px 32px;
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 255, 255, 0.96)),
    linear-gradient(180deg, rgba(72, 134, 239, 0.04), transparent 22%);
  box-shadow: 0 28px 60px rgba(44, 88, 154, 0.12);
  border: 1px solid rgba(214, 223, 235, 0.88);
}

.login-card__header h2 {
  margin: 0;
  font-size: 28px;
  line-height: 1.12;
  color: #2f3137;
}

.login-card__header p {
  margin: 8px 0 0;
  font-size: 14px;
  color: #717d8d;
}

.login-form {
  margin-top: 22px;
}

.form-item {
  margin-bottom: 18px;
}

.login-card :deep(.el-form-item) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  margin-bottom: 0;
}

.login-card :deep(.el-form-item__label) {
  width: auto !important;
  justify-content: flex-start;
  padding: 0 0 8px;
  height: auto;
  line-height: 1.4;
  font-size: 14px;
  font-weight: 700;
  color: #4a5565;
}

.login-card :deep(.el-form-item__content) {
  line-height: normal;
}

.login-card :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px #d8e0ec !important;
  background: #fff !important;
  padding: 0 16px;
}

.login-card :deep(.el-input__wrapper:hover) {
  box-shadow: inset 0 0 0 1px #9eb9df !important;
}

.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: inset 0 0 0 1px #4f8dee !important;
}

.login-card :deep(.el-input__inner) {
  font-size: 14px;
}

.login-card :deep(.el-input__inner::placeholder) {
  color: #b7c0ce;
}

.login-card :deep(.el-form-item__error) {
  padding-top: 8px;
}

.pwd-toggle {
  cursor: pointer;
  color: #9aa7b8;
}

.pwd-toggle.is-disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.submit-item {
  margin-top: 4px;
}

.login-btn {
  width: 100%;
  min-height: 50px;
  border-radius: 8px;
  font-size: 18px;
  font-weight: 700;
  box-shadow: 0 18px 34px rgba(29, 92, 207, 0.18);
}

.quick-links {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quick-link {
  min-height: 66px;
  border-radius: 14px;
  border: 1px solid #dbe3ee;
  background: #fff;
  padding: 0 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.quick-link:hover {
  border-color: #a8c3eb;
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(52, 93, 161, 0.08);
}

.quick-link:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.quick-link__icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #2f72e7;
  background: rgba(58, 123, 240, 0.1);
  font-size: 16px;
  flex-shrink: 0;
}

.quick-link__text {
  flex: 1;
  text-align: left;
  font-size: 14px;
  font-weight: 700;
  color: #3f4855;
}

.quick-link__arrow {
  font-size: 22px;
  line-height: 1;
  color: #a0acba;
}

.version-warning {
  margin: 18px 0 0;
  text-align: center;
  color: #c68c33;
  font-size: 12px;
}

@media (max-width: 1180px) {
  .login-layout {
    grid-template-columns: 1fr;
  }

  .brand-panel,
  .login-panel {
    min-height: auto;
  }

  .brand-panel {
    padding: 24px;
  }

  .hall-stage {
    grid-template-columns: 1fr;
  }

  .feature-list {
    margin-top: 26px;
  }
}

@media (max-width: 760px) {
  .login-shell {
    padding: 18px;
  }

  .login-layout {
    gap: 18px;
  }

  .brand-panel,
  .login-card {
    padding: 22px 18px;
    border-radius: 18px;
  }

  .brand-head {
    gap: 16px;
  }

  .brand-mark {
    width: 72px;
    height: 72px;
    border-radius: 20px;
  }

  .brand-mark__core {
    width: 42px;
    height: 42px;
    font-size: 24px;
  }

  .brand-description {
    font-size: 13px;
  }

  .hall-stage {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .queue-screen__numbers {
    grid-template-columns: 1fr;
  }

  .feature-item {
    font-size: 14px;
  }

  .spotlight-card {
    padding: 18px 16px;
  }

  .spotlight-card__ticket {
    font-size: 50px;
  }

  .quick-links {
    grid-template-columns: 1fr;
  }

  .login-card__header h2 {
    font-size: 24px;
  }

  .login-btn {
    font-size: 16px;
  }
}
</style>
