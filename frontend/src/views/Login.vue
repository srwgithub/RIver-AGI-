<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-left">
        <div class="brand-pattern"></div>
        <div class="left-content">
          <div class="brand-main">
            <div class="brand-logo-large">R</div>
            <h1 class="brand-title">RIver AGI</h1>
            <p class="brand-subtitle">智能市场需求预测平台</p>
            <p class="brand-slogan">数据驱动 · AI赋能 · 精准决策</p>
          </div>

          <div class="feature-list">
            <div class="feature-item">
              <div class="feature-icon"><el-icon :size="20"><DataAnalysis /></el-icon></div>
              <div class="feature-text">
                <div class="feature-name">智能预测引擎</div>
                <div class="feature-desc">基于深度学习的市场需求预测</div>
              </div>
            </div>
            <div class="feature-item">
              <div class="feature-icon"><el-icon :size="20"><TrendCharts /></el-icon></div>
              <div class="feature-text">
                <div class="feature-name">可视化分析</div>
                <div class="feature-desc">多维度数据趋势看板</div>
              </div>
            </div>
            <div class="feature-item">
              <div class="feature-icon"><el-icon :size="20"><Cpu /></el-icon></div>
              <div class="feature-text">
                <div class="feature-name">AI模型管理</div>
                <div class="feature-desc">模型训练、评估与自动调优</div>
              </div>
            </div>
          </div>

          <div class="tech-badges">
            <span class="tech-badge">Vue 3</span>
            <span class="tech-badge">Element Plus</span>
            <span class="tech-badge">AI Powered</span>
          </div>
        </div>
      </div>

      <div class="login-right">
        <div class="login-form-wrapper">
          <div class="form-header">
            <h2 class="form-title">欢迎登录</h2>
            <p class="form-subtitle">RIver AGI System</p>
          </div>

          <el-form :model="form" class="login-form" @keyup.enter="login">
            <el-form-item>
              <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                size="large"
                prefix-icon="User"
                class="login-input"
              />
            </el-form-item>

            <el-form-item>
              <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                prefix-icon="Lock"
                show-password
                class="login-input"
              />
            </el-form-item>

            <div class="form-options">
              <el-checkbox v-model="rememberMe" class="remember-me">记住我</el-checkbox>
              <a href="#" class="forgot-link" @click.prevent>忘记密码?</a>
            </div>

            <el-button
              type="primary"
              size="large"
              class="login-btn"
              :loading="loading"
              @click="login"
            >
              登 录
            </el-button>
          </el-form>

          <div class="form-footer">
            <p class="copyright">© 2026 RIver AGI System. All rights reserved.</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis, TrendCharts, Cpu } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()
const loading = ref(false)
const rememberMe = ref(false)
const form = reactive({
  username: '',
  password: ''
})

const login = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/v1/auth/login', form)
    const role = String(res?.user?.role || (form.username === 'admin' ? 'ADMIN' : 'USER')).toUpperCase()
    localStorage.setItem('token', res.accessToken)
    localStorage.setItem('user', JSON.stringify({ ...res.user, role }))
    localStorage.setItem('river_view_mode', role === 'ADMIN' ? 'admin' : 'customer')
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '登录失败，请检查服务连接')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
  padding: 20px;
}

.login-container {
  width: 100%;
  max-width: 960px;
  min-height: 560px;
  display: flex;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.login-left {
  flex: 1;
  background: linear-gradient(135deg, #1677ff 0%, #0958d9 50%, #003eb3 100%);
  position: relative;
  padding: 48px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

.brand-pattern {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: 0.1;
  background-image:
    radial-gradient(circle at 20% 80%, rgba(255,255,255,0.3) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255,255,255,0.3) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(255,255,255,0.1) 0%, transparent 30%);
}

.brand-pattern::before {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.1);
  top: -100px;
  right: -100px;
}

.brand-pattern::after {
  content: '';
  position: absolute;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.1);
  bottom: -50px;
  left: -50px;
}

.left-content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.brand-main {
  margin-bottom: 48px;
}

.brand-logo-large {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  font-weight: 800;
  color: #fff;
  margin-bottom: 24px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.brand-title {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.brand-subtitle {
  font-size: 20px;
  color: rgba(255, 255, 255, 0.9);
  margin: 0 0 16px 0;
  font-weight: 500;
}

.brand-slogan {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.7);
  margin: 0;
  letter-spacing: 2px;
}

.feature-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.feature-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.feature-text {
  color: #fff;
}

.feature-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.feature-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.tech-badges {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.tech-badge {
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
}

.login-right {
  flex: 1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.login-form-wrapper {
  width: 100%;
  max-width: 360px;
}

.form-header {
  margin-bottom: 36px;
  text-align: center;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 8px 0;
}

.form-subtitle {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

.login-form {
  margin-top: 8px;
}

.login-input {
  margin-bottom: 4px;
}

.login-input :deep(.el-input__wrapper) {
  border-radius: 8px !important;
  padding: 4px 16px;
  box-shadow: 0 0 0 1px #e5e7eb inset !important;
}

.login-input :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #4096ff inset !important;
}

.login-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #1677ff inset !important;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 24px 0;
}

.remember-me :deep(.el-checkbox__label) {
  color: #6b7280;
  font-size: 14px;
}

.forgot-link {
  font-size: 14px;
  color: #1677ff;
  text-decoration: none;
}

.forgot-link:hover {
  color: #4096ff;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
  letter-spacing: 4px;
}

.form-footer {
  margin-top: 40px;
  text-align: center;
}

.copyright {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
}

@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
    min-height: auto;
    max-width: 420px;
  }

  .login-left {
    padding: 32px;
    min-height: 280px;
  }

  .brand-logo-large {
    width: 48px;
    height: 48px;
    font-size: 28px;
    border-radius: 12px;
    margin-bottom: 16px;
  }

  .brand-title {
    font-size: 28px;
  }

  .brand-subtitle {
    font-size: 16px;
  }

  .feature-list {
    display: none;
  }

  .tech-badges {
    margin-top: 16px;
  }

  .login-right {
    padding: 32px 24px;
  }

  .form-header {
    margin-bottom: 24px;
  }

  .form-title {
    font-size: 24px;
  }
}
</style>
