<template>
  <main class="login-page">
    <section class="hero-card">
      <div class="hero-copy">
        <img class="brand-logo" src="../assets/brand/logo-full.svg" alt="旅游客户 CRM" />
        <p>客资录入、绩效统计、成交记录和组织权限统一管理。</p>
        <div class="hero-badges">
          <span>移动端录入</span>
          <span>团队绩效</span>
          <span>成交追踪</span>
        </div>
      </div>

      <section class="login-panel">
        <h2>账号登录</h2>
        <p>使用员工编号进入业务系统</p>

        <el-form label-position="top">
          <el-form-item label="员工编号">
            <el-input v-model="loginForm.employeeCode" placeholder="请输入员工编号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" class="login-button" :loading="loading" @click="loginSubmit">登录系统</el-button>
        </el-form>
      </section>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { showError } from '../utils/feedback'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)

const loginForm = reactive({
  employeeCode: '',
  password: ''
})

async function loginSubmit() {
  loading.value = true
  try {
    await authStore.loginWithPassword({
      ...loginForm,
      employeeCode: loginForm.employeeCode.trim().toUpperCase()
    })
    router.push('/index')
  } catch (error) {
    await showError(error.message || '登录失败，请检查账号和密码')
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  overflow: hidden;
  background:
    linear-gradient(rgba(248, 250, 255, 0.22), rgba(248, 250, 255, 0.22)),
    url("../assets/brand/login-bg.svg") center / cover no-repeat;
}

.login-page::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 24%, rgba(254, 123, 167, 0.26), transparent 22%),
    radial-gradient(circle at 78% 68%, rgba(113, 164, 213, 0.28), transparent 26%),
    linear-gradient(135deg, rgba(75, 75, 254, 0.05), rgba(255, 255, 255, 0.2));
}

.hero-card {
  position: relative;
  z-index: 1;
  width: min(1180px, 100%);
  min-height: 560px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  align-items: center;
  gap: 34px;
  padding: 56px;
  border: 1px solid rgba(255, 255, 255, 0.86);
  border-radius: 36px;
  background:
    radial-gradient(circle at 12% 20%, rgba(254, 123, 167, 0.18), transparent 28%),
    radial-gradient(circle at 86% 84%, rgba(113, 164, 213, 0.2), transparent 28%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.78));
  box-shadow: 0 30px 80px rgba(29, 25, 92, 0.14);
  backdrop-filter: blur(18px);
}

.hero-copy {
  display: grid;
  gap: 22px;
  min-width: 0;
}

.brand-logo {
  width: min(520px, 100%);
  height: auto;
  filter: drop-shadow(0 18px 34px rgba(75, 75, 254, 0.12));
}

.hero-copy p {
  max-width: 500px;
  margin: 0;
  color: var(--text-muted);
  font-size: 18px;
  line-height: 1.8;
}

.hero-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-badges span {
  padding: 9px 14px;
  color: var(--brand);
  border-radius: 999px;
  background: rgba(75, 75, 254, 0.1);
  font-size: 14px;
  font-weight: 800;
}

.login-panel {
  width: 100%;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 28px;
  padding: 32px;
  box-shadow: 0 24px 56px rgba(29, 25, 92, 0.12);
}

.login-panel h2 {
  margin: 0 0 6px;
  color: var(--text-strong);
  font-size: 28px;
}

.login-panel p {
  margin: 0 0 22px;
  color: var(--text-muted);
}

.login-button {
  width: 100%;
  min-height: 44px;
}

@media (max-width: 960px) {
  .hero-card {
    grid-template-columns: 1fr;
    min-height: auto;
    padding: 28px;
  }

  .brand-logo {
    width: min(440px, 100%);
  }
}

@media (max-width: 520px) {
  .login-page {
    align-items: stretch;
    padding: 12px;
  }

  .hero-card {
    gap: 18px;
    border-radius: 24px;
    padding: 20px;
  }

  .hero-copy {
    gap: 14px;
  }

  .hero-copy p {
    font-size: 15px;
    line-height: 1.7;
  }

  .login-panel {
    padding: 20px;
  }
}
</style>
