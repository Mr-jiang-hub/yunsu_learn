<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const username = ref('')
const password = ref('')
const submitting = ref(false)

const onSubmit = async (): Promise<void> => {
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    await session.login(username.value, password.value)
    const redirect = (router.currentRoute.value.query.redirect as string) || '/dashboard'
    await router.push(redirect)
  } catch (error) {
    const message = error instanceof Error && error.message ? error.message : '登录失败，请检查用户名和密码'
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-panel">
      <div class="login-brand">
        <span class="admin-brand-mark">云</span>
        <span>云枢智学</span>
      </div>
      <h1>管理后台</h1>
      <p class="admin-muted login-desc">
        输入管理员账号密码登录
      </p>

      <el-form
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="用户名">
          <el-input
            v-model="username"
            placeholder="请输入管理员用户名"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button
          class="login-submit"
          type="primary"
          native-type="submit"
          :loading="submitting"
        >
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 20% 20%, rgba(79, 70, 229, 0.12), transparent 45%),
    radial-gradient(circle at 80% 80%, rgba(124, 58, 237, 0.1), transparent 40%),
    var(--bg);
}

.login-panel {
  width: 100%;
  max-width: 400px;
  padding: 32px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 800;
  color: var(--ink);
}

.login-panel h1 {
  margin: 20px 0 8px;
  font-size: 24px;
  color: var(--ink);
}

.login-desc {
  margin-bottom: 24px;
}

.login-submit {
  width: 100%;
  margin-top: 4px;
}
</style>
