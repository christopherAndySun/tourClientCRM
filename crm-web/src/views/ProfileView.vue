<template>
  <section>
    <div class="toolbar">
      <div class="page-title">个人资料</div>
    </div>

    <el-form class="panel profile-form" label-position="top">
      <el-form-item label="员工编号">
        <el-input :model-value="authStore.user?.employeeCode" disabled />
      </el-form-item>
      <el-form-item label="姓名">
        <el-input v-model="form.name" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="form.password" type="password" show-password placeholder="不修改密码时留空" />
      </el-form-item>
      <div class="form-actions">
        <el-button type="primary" :loading="saving" @click="submit">保存修改</el-button>
      </div>
    </el-form>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const saving = ref(false)
const form = reactive({
  name: '',
  password: ''
})

onMounted(() => {
  form.name = authStore.user?.name || ''
})

async function submit() {
  if (!form.name.trim()) {
    await showError('请填写姓名')
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

function showError(message) {
  return ElMessageBox.alert(message, '提示', {
    confirmButtonText: '我知道了',
    type: 'warning'
  })
}
</script>

<style scoped>
.profile-form {
  max-width: 520px;
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
