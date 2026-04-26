<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <div class="logo-mark">
          <img src="/favicon.png" alt="QMS" />
        </div>
        <h1 class="auth-title">找回密码</h1>
        <p class="auth-subtitle">输入用户名或邮箱。未绑定邮箱的账号请使用原密码重置或联系管理员</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="auth-form">
        <div class="field-group">
          <label class="field-label">用户名或邮箱</label>
          <el-form-item prop="identifier">
            <el-input v-model="form.identifier" placeholder="请输入用户名或邮箱" size="large" />
          </el-form-item>
        </div>

        <el-form-item class="submit-item">
          <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="handleSend">
            发送验证码
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        <el-button link type="primary" @click="router.push('/login')">返回登录</el-button>
        <el-divider direction="vertical" />
        <el-button link type="primary" @click="router.push('/register')">注册账号</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  identifier: ''
})

const rules = {
  identifier: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }]
}

async function handleSend() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const identifier = String(form.identifier || '').trim()
      const isEmail = identifier.includes('@')
      const payload = isEmail ? { email: identifier } : { username: identifier }
      const res = await axios.post('/api/v1/auth/password-reset/request', payload)
      if (res.data?.code === 200) {
        ElMessage.success('验证码已发送（如邮箱存在）')
      } else {
        ElMessage.error(res.data?.message || '发送失败')
      }
    } catch (err) {
      const msg = err.response?.data?.message || '发送失败'
      if (msg.includes('未绑定邮箱')) {
        try {
          await ElMessageBox.confirm(
            msg,
            '无法通过邮箱重置',
            {
              type: 'warning',
              confirmButtonText: '使用原密码重置',
              cancelButtonText: '联系管理员',
              showClose: true
            }
          )
          router.push('/reset-password?mode=old')
        } catch {
          ElMessageBox.alert('该账号未绑定邮箱，请联系管理员进行密码重置。', '提示', { type: 'info', confirmButtonText: '我知道了' })
        }
        return
      }
      ElMessage.error(msg)
    } finally {
      loading.value = false
    }
  })
}
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
  color: var(--text-muted);
}
</style>
