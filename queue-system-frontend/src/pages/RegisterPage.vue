<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <div class="logo-mark">
          <img src="/favicon.png" alt="QMS" />
        </div>
        <h1 class="auth-title">注册账号</h1>
        <p class="auth-subtitle">注册后需管理员激活才能登录</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" class="auth-form">
        <div class="field-group">
          <label class="field-label">用户名</label>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">姓名</label>
          <el-form-item prop="name">
            <el-input v-model="form.name" placeholder="请输入姓名" size="large" />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">邮箱</label>
          <el-form-item prop="email">
            <el-input v-model="form.email" placeholder="用于找回密码" size="large" />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">注册角色</label>
          <el-form-item prop="role">
            <el-select v-model="form.role" placeholder="请选择角色" size="large" style="width:100%">
              <el-option label="区域管理员" value="REGION_ADMIN" />
              <el-option label="窗口操作员" value="WINDOW_OPERATOR" />
            </el-select>
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">注册区域</label>
          <el-form-item prop="regionId">
            <el-tree-select
              v-model="form.regionId"
              :data="regionTree"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              placeholder="请选择注册区域"
              clearable
              check-strictly
              :render-after-expand="false"
              filterable
              :filter-method="filterRegionByName"
              style="width:100%"
              class="region-select"
              popper-class="region-select-popper"
            />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">密码</label>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="至少6位" size="large" />
          </el-form-item>
        </div>

        <div class="field-group">
          <label class="field-label">确认密码</label>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" size="large" />
          </el-form-item>
        </div>

        <el-form-item class="submit-item">
          <el-button type="primary" size="large" :loading="loading" class="submit-btn" @click="handleSubmit">
            提交注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        <el-button link type="primary" @click="router.push('/login')">返回登录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const regions = ref([])

const form = reactive({
  username: '',
  name: '',
  email: '',
  role: '',
  regionId: null,
  password: '',
  confirmPassword: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  regionId: [{ required: true, message: '请选择注册区域', trigger: 'change' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_, value, cb) => {
        if (value !== form.password) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const regionTree = computed(() => buildRegionTree(regions.value))

function buildRegionTree(flatRegions) {
  if (!flatRegions || flatRegions.length === 0) return []
  const map = {}
  const roots = []
  flatRegions.forEach(r => { map[r.id] = { ...r, children: [] } })
  flatRegions.forEach(r => {
    const node = map[r.id]
    if (!r.parentId || !map[r.parentId]) roots.push(node)
    else map[r.parentId].children.push(node)
  })
  return roots
}

function filterRegionByName(query, node) {
  if (!query) return true
  const lowerQuery = query.toLowerCase()
  if (node.name && node.name.toLowerCase().includes(lowerQuery)) return true
  if (node.children && node.children.some(child => filterRegionByName(query, child))) return true
  return false
}

async function fetchRegions() {
  try {
    const res = await axios.get('/api/v1/regions')
    regions.value = res.data?.data || []
  } catch {
    regions.value = []
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (String(form.password || '').length < 6) {
      ElMessage.warning('密码至少6位')
      return
    }
    loading.value = true
    try {
      const res = await axios.post('/api/v1/auth/register', {
        username: form.username,
        name: form.name,
        email: form.email,
        role: form.role,
        regionId: form.regionId,
        password: form.password
      })
      if (res.data?.code === 200) {
        ElMessage.success('注册成功，请等待管理员激活')
        router.push('/login')
      } else {
        ElMessage.error(res.data?.message || '注册失败')
      }
    } catch (err) {
      const msg = err.response?.data?.message || '注册失败'
      ElMessage.error(msg)
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  fetchRegions()
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
  max-width: 520px;
  padding: 40px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
}

.auth-header {
  text-align: center;
  margin-bottom: 24px;
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
</style>

