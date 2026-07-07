<template>
  <section>
    <div class="toolbar">
      <div class="page-title">个人资料</div>
    </div>

    <el-form class="panel profile-form" label-position="top">
      <el-alert
        v-if="mustChangePassword"
        class="password-alert"
        title="当前账号正在使用初始密码，请先修改密码后再使用业务功能。密码至少 6 位，方便记住即可。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form-item label="员工编号">
        <el-input :model-value="authStore.user?.employeeCode" disabled />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="form.name" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          :placeholder="mustChangePassword ? '请输入新密码，至少 6 位' : '不修改密码时留空'"
        />
      </el-form-item>
      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="submit">保存修改</el-button>
      </div>
    </el-form>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { showError } from '../utils/feedback'

const authStore = useAuthStore()
const saving = ref(false)
const form = reactive({
  name: '',
  password: ''
})
const mustChangePassword = computed(() => Boolean(authStore.user?.mustChangePassword))

onMounted(() => {
  form.name = authStore.user?.name || ''
})

async function submit() {
  if (!form.name.trim()) {
    await showError('请填写姓名')
    return
  }
  if (mustChangePassword.value && !form.password) {
    await showError('请先设置新密码')
    return
  }
  if (form.password && form.password.length < 6) {
    await showError('密码至少 6 位')
    return
  }

  saving.value = true
  try {
    await authStore.updateProfile({
      name: form.name.trim(),
      password: form.password
    })
    form.password = ''
    ElMessage.success('资料已更新')
  } catch (error) {
    await showError(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-form {
  max-width: 520px;
}

.password-alert {
  margin-bottom: 18px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .profile-form {
    max-width: none;
  }

  .form-actions .el-button {
    width: 100%;
  }
}
</style>
