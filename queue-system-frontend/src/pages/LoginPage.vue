<template>
  <div class="login-shell">
    <div class="login-bg" aria-hidden="true">
      <div class="bg-orb orb-1"></div>
      <div class="bg-orb orb-2"></div>
      <div class="bg-orb orb-3"></div>
      <div class="bg-grid"></div>
    </div>

    <div class="login-wrap">
      <section class="brand-panel">
        <div class="brand-top">
          <div class="logo-mark">
            <svg class="brand-logo-svg" viewBox="0 0 64 64" role="img" aria-label="QMS Logo">
              <defs>
                <linearGradient id="qms-grad" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0" stop-color="#0052d9" />
                  <stop offset="1" stop-color="#2b6de5" />
                </linearGradient>
                <linearGradient id="qms-grad-soft" x1="0" y1="1" x2="1" y2="0">
                  <stop offset="0" stop-color="rgba(0, 82, 217, 0.18)" />
                  <stop offset="1" stop-color="rgba(43, 196, 138, 0.18)" />
                </linearGradient>
              </defs>
              <rect x="10" y="12" width="44" height="40" rx="12" fill="url(#qms-grad-soft)" />
              <path
                d="M22 20h20a6 6 0 0 1 6 6v12a6 6 0 0 1-6 6H32l-6 6v-6h-4a6 6 0 0 1-6-6V26a6 6 0 0 1 6-6Z"
                fill="url(#qms-grad)"
              />
              <path
                d="M28.8 31.8c0-4.1 3.2-7.3 7.5-7.3 2.1 0 3.9.7 5.2 2l-2 2c-.8-.9-1.9-1.3-3.2-1.3-2.7 0-4.7 1.9-4.7 4.7 0 2.8 2 4.7 4.7 4.7 1.4 0 2.6-.5 3.5-1.5l2 2c-1.4 1.6-3.4 2.4-5.7 2.4-4.3 0-7.3-3.1-7.3-7.7Z"
                fill="#fff"
                fill-opacity="0.92"
              />
              <path
                d="M43.7 41.2l4.2 4.2"
                stroke="#fff"
                stroke-opacity="0.92"
                stroke-width="3"
                stroke-linecap="round"
              />
            </svg>
          </div>
          <div class="brand-titles">
            <h1 class="brand-title">排队叫号系统</h1>
            <p class="brand-subtitle">Queue Management System</p>
          </div>
        </div>

        <div class="brand-features">
          <div class="feature">
            <span class="dot dot-primary"></span>
            <span>扫码取号，减少排队拥堵</span>
          </div>
          <div class="feature">
            <span class="dot dot-success"></span>
            <span>窗口叫号，实时联动大屏</span>
          </div>
          <div class="feature">
            <span class="dot dot-warning"></span>
            <span>后台管理，区域/窗口/用户统一配置</span>
          </div>
        </div>

        <div class="brand-visual" aria-hidden="true">
          <div class="ticket-card">
            <div class="ticket-top">
              <span class="ticket-label">当前叫号</span>
              <span class="ticket-badge">窗口 03</span>
            </div>
            <div class="ticket-no">A-018</div>
            <div class="ticket-meta">
              <span class="meta-pill">预计等待 6 分钟</span>
              <span class="meta-pill">状态：办理中</span>
            </div>
          </div>
          <div class="mini-stats">
            <div class="stat">
              <div class="stat-num">128</div>
              <div class="stat-label">今日取号</div>
            </div>
            <div class="stat">
              <div class="stat-num">6</div>
              <div class="stat-label">正在等候</div>
            </div>
            <div class="stat">
              <div class="stat-num">4</div>
              <div class="stat-label">窗口在线</div>
            </div>
          </div>
        </div>

        <div class="brand-meta">
          <span class="pill">安全登录</span>
          <span class="pill">多角色</span>
          <span class="pill">v1.0.0</span>
        </div>
      </section>

      <section class="login-card" aria-label="登录">
        <div class="login-header">
          <h2 class="login-title">欢迎回来</h2>
          <p class="login-subtitle">请使用你的账号密码登录</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
          <div class="field-group">
            <label class="field-label">用户名</label>
            <el-form-item prop="username">
              <el-input v-model="form.username" :disabled="loading" placeholder="请输入用户名" size="large" autocomplete="username" clearable />
            </el-form-item>
          </div>

          <div class="field-group">
            <label class="field-label">密码</label>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                :disabled="loading"
                :type="showPassword ? 'text' : 'password'"
                placeholder="请输入密码"
                size="large"
                autocomplete="current-password"
                @keyup.enter="handleLogin"
              >
                <template #suffix>
                  <el-icon class="pwd-toggle" :class="{ 'is-disabled': loading }" @click.stop="loading ? null : (showPassword = !showPassword)">
                    <component :is="showPassword ? View : Hide" />
                  </el-icon>
                </template>
              </el-input>
            </el-form-item>
          </div>

          <el-form-item class="submit-item">
            <el-button type="primary" size="large" :loading="loading" class="login-btn" @click="handleLogin">
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="link-actions" role="navigation" aria-label="登录辅助入口">
          <button class="link-action" type="button" :disabled="loading" @click="router.push('/register')">
            <span class="link-icon" aria-hidden="true">
              <el-icon><User /></el-icon>
            </span>
            <span class="link-text">注册账号</span>
            <span class="link-arrow" aria-hidden="true">
              <el-icon><ArrowRight /></el-icon>
            </span>
          </button>
          <button class="link-action" type="button" :disabled="loading" @click="router.push('/forgot-password')">
            <span class="link-icon" aria-hidden="true">
              <el-icon><Key /></el-icon>
            </span>
            <span class="link-text">忘记密码</span>
            <span class="link-arrow" aria-hidden="true">
              <el-icon><ArrowRight /></el-icon>
            </span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { useUserStore } from '../stores/user'
import { View, Hide, User, Key, ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const showPassword = ref(false)

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
.login-shell {
  min-height: 100vh;
  display: flex;
  align-items: stretch;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: var(--bg-body);
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(1200px 520px at 18% 22%, rgba(0, 82, 217, 0.18), transparent 55%),
    radial-gradient(980px 520px at 86% 18%, rgba(43, 196, 138, 0.12), transparent 56%),
    radial-gradient(860px 520px at 70% 92%, rgba(255, 160, 0, 0.12), transparent 60%),
    linear-gradient(135deg, rgba(0, 82, 217, 0.06) 0%, rgba(0, 82, 217, 0.02) 42%, transparent 72%);
}

.bg-orb {
  position: absolute;
  filter: blur(24px);
  opacity: 0.8;
  transform: translateZ(0);
}

.orb-1 {
  width: 260px;
  height: 260px;
  left: -60px;
  top: 60px;
  border-radius: 84px;
  background: rgba(0, 82, 217, 0.2);
}

.orb-2 {
  width: 220px;
  height: 220px;
  right: 10%;
  top: -40px;
  border-radius: 9999px;
  background: rgba(43, 196, 138, 0.16);
}

.orb-3 {
  width: 260px;
  height: 260px;
  right: -80px;
  bottom: -70px;
  border-radius: 96px;
  background: rgba(0, 82, 217, 0.12);
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 0, 0, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(600px 420px at 20% 20%, rgba(0, 0, 0, 0.85), transparent 70%);
  opacity: 0.55;
}

.login-wrap {
  width: min(1080px, 100%);
  margin: 0 auto;
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 28px;
  padding: 56px 24px;
  position: relative;
  z-index: 1;
}

.brand-panel {
  flex: 1.05;
  padding: 36px;
  border-radius: 18px;
  border: 1px solid rgba(0, 82, 217, 0.16);
  background:
    linear-gradient(135deg, rgba(0, 82, 217, 0.12), rgba(0, 82, 217, 0.06)),
    rgba(255, 255, 255, 0.66);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.08);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 420px;
}

.brand-top {
  display: flex;
  gap: 14px;
  align-items: center;
}

.logo-mark {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: var(--primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-logo-svg {
  width: 44px;
  height: 44px;
  display: block;
}

.brand-title {
  font-size: 30px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: 0.02em;
}

.brand-subtitle {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  margin-top: 2px;
}

.brand-features {
  margin-top: 26px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--text-secondary);
  font-size: var(--text-base);
  line-height: 1.7;
}

.feature {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 0 4px rgba(0, 0, 0, 0.02);
}

.dot-primary { background: var(--primary); }
.dot-success { background: var(--success); }
.dot-warning { background: var(--warning); }

.brand-meta {
  margin-top: 28px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.brand-visual {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ticket-card {
  padding: 14px 14px 12px;
  border-radius: 14px;
  border: 1px solid rgba(0, 82, 217, 0.14);
  background:
    radial-gradient(380px 180px at 12% 10%, rgba(0, 82, 217, 0.18), transparent 60%),
    rgba(255, 255, 255, 0.74);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.08);
}

.ticket-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.ticket-label {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  font-weight: 700;
  letter-spacing: 0.06em;
}

.ticket-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 9999px;
  background: rgba(0, 82, 217, 0.1);
  border: 1px solid rgba(0, 82, 217, 0.16);
  color: var(--primary);
  font-size: var(--text-xs);
  font-weight: 700;
}

.ticket-no {
  margin-top: 10px;
  font-size: 34px;
  font-weight: 900;
  letter-spacing: 0.06em;
  color: var(--text-primary);
}

.ticket-meta {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(0, 0, 0, 0.06);
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-secondary);
}

.mini-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.stat {
  padding: 12px 12px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(0, 0, 0, 0.06);
}

.stat-num {
  font-size: 18px;
  font-weight: 900;
  color: var(--text-primary);
}

.stat-label {
  margin-top: 4px;
  font-size: var(--text-xs);
  color: var(--text-secondary);
  font-weight: 600;
}

.pill {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(0, 0, 0, 0.06);
  color: var(--text-secondary);
  font-size: var(--text-xs);
  font-weight: 600;
}

.login-card {
  flex: 0.95;
  width: 100%;
  max-width: 440px;
  padding: 34px 32px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 18px;
  box-shadow: 0 18px 46px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
}

.login-header {
  margin-bottom: 18px;
}

.login-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: 0.01em;
}

.login-subtitle {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  margin-top: 6px;
}

.login-form {
  margin-top: 14px;
}

.field-group {
  margin-bottom: 16px;
}

.pwd-toggle {
  cursor: pointer;
  color: var(--text-muted);
  transition: color var(--duration-fast) var(--ease-out);
}

.pwd-toggle.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.pwd-toggle:hover {
  color: var(--primary);
}

.field-label {
  display: block;
  margin-bottom: 8px;
  font-size: var(--text-sm);
  color: var(--text-primary);
  font-weight: 500;
}

.login-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.login-card :deep(.el-input__wrapper) {
  min-height: 40px;
}

.login-card :deep(.el-form-item__error) {
  font-size: 12px;
  padding-top: 4px;
}

.submit-item {
  margin-top: 6px;
}

.login-btn {
  width: 100%;
  min-height: 40px;
  font-size: var(--text-base);
}

.link-actions {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.link-action {
  appearance: none;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.7);
  border-radius: 12px;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition:
    transform var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    background var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.link-action:hover {
  transform: translateY(-1px);
  border-color: rgba(0, 82, 217, 0.22);
  background: rgba(235, 242, 254, 0.7);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.08);
}

.link-action:active {
  transform: translateY(0);
}

.link-action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.link-icon {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: rgba(0, 82, 217, 0.1);
  border: 1px solid rgba(0, 82, 217, 0.14);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  flex-shrink: 0;
}

.link-text {
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--text-primary);
}

.link-arrow {
  margin-left: auto;
  color: rgba(0, 0, 0, 0.35);
  transition: transform var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out);
}

.link-action:hover .link-arrow {
  transform: translateX(2px);
  color: rgba(0, 82, 217, 0.75);
}

@media (max-width: 920px) {
  .login-wrap {
    padding: 44px 18px;
    gap: 18px;
  }

  .brand-panel {
    padding: 28px;
  }
}

@media (max-width: 760px) {
  .login-wrap {
    flex-direction: column;
    align-items: center;
    padding: 20px 16px;
    gap: 14px;
  }

  .brand-panel {
    width: 100%;
    min-height: auto;
    padding: 22px 18px;
  }

  .login-card {
    max-width: 520px;
    padding: 28px 20px;
  }

  .link-actions {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .brand-title {
    font-size: 26px;
  }

  .login-title {
    font-size: 20px;
  }
}
</style>
