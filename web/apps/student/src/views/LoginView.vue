<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button, CellGroup, Field, Form, NavBar, showToast } from 'vant'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const route = useRoute()
const session = useSessionStore()

const mode = ref<'password' | 'sms'>('password')
const phone = ref('')
const password = ref('')
const code = ref('')
const submitting = ref(false)
const countdown = ref(0)
let timer: number | undefined

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const canSendCode = computed(() => countdown.value === 0 && phoneValid.value)

onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer)
  }
})

function startCountdown(seconds: number): void {
  countdown.value = seconds > 0 ? seconds : 60
  if (timer) {
    window.clearInterval(timer)
  }
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      window.clearInterval(timer)
      timer = undefined
      countdown.value = 0
    }
  }, 1000)
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function onSendCode(): Promise<void> {
  if (!phoneValid.value) {
    showToast('请输入正确的手机号')
    return
  }
  try {
    const result = await session.sendSmsCode(phone.value, 'LOGIN')
    startCountdown(result.retryAfterSeconds)
    showToast(`验证码已发送，${result.expireSeconds} 秒内有效`)
  } catch (error) {
    showToast(errorMessage(error, '验证码发送失败'))
  }
}

async function onSubmit(): Promise<void> {
  if (submitting.value) {
    return
  }
  if (!phoneValid.value) {
    showToast('请输入正确的手机号')
    return
  }
  if (mode.value === 'password' && !password.value) {
    showToast('请输入密码')
    return
  }
  if (mode.value === 'sms' && !code.value) {
    showToast('请输入验证码')
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'password') {
      await session.loginByPassword(phone.value, password.value)
    } else {
      await session.loginBySms(phone.value, code.value)
    }
    const redirect = (route.query.redirect as string) || '/home'
    await router.replace(redirect)
  } catch (error) {
    showToast(errorMessage(error, '登录失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <NavBar title="登录" />
    <section class="login-hero">
      <p class="eyebrow">
        Yunshu Zhixue
      </p>
      <h1>云枢智学</h1>
      <p class="muted">
        学习路线 · 随堂练习 · 错题本 · 模拟面试
      </p>
    </section>

    <div class="login-switch">
      <button
        type="button"
        :class="{ active: mode === 'password' }"
        @click="mode = 'password'"
      >
        密码登录
      </button>
      <button
        type="button"
        :class="{ active: mode === 'sms' }"
        @click="mode = 'sms'"
      >
        验证码登录
      </button>
    </div>

    <Form
      class="login-form"
      @submit="onSubmit"
    >
      <CellGroup inset>
        <Field
          v-model="phone"
          name="phone"
          label="手机号"
          placeholder="请输入手机号"
          type="tel"
          maxlength="11"
          :rules="[{ required: true, message: '请输入手机号' }]"
        />
        <Field
          v-if="mode === 'password'"
          v-model="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          type="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
        <Field
          v-else
          v-model="code"
          name="code"
          label="验证码"
          placeholder="请输入 6 位验证码"
          maxlength="6"
          :rules="[{ required: true, message: '请输入验证码' }]"
        >
          <template #button>
            <Button
              size="small"
              plain
              type="primary"
              :disabled="!canSendCode"
              @click="onSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重试` : '获取验证码' }}
            </Button>
          </template>
        </Field>
      </CellGroup>

      <div class="login-actions">
        <Button
          block
          round
          type="primary"
          native-type="submit"
          :loading="submitting"
        >
          登录
        </Button>
        <div class="login-extra">
          <RouterLink
            class="link"
            to="/register"
          >
            还没有账号？立即注册
          </RouterLink>
        </div>
      </div>
    </Form>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  background: var(--bg);
}

.login-hero {
  padding: 32px 24px 20px;
}

.login-hero h1 {
  margin: 0 0 10px;
  font-size: 28px;
  color: var(--ink);
}

.login-switch {
  display: flex;
  gap: 8px;
  padding: 0 16px 8px;
}

.login-switch button {
  flex: 1;
  padding: 10px 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--muted-ink, #6b7280);
  background: transparent;
  border: none;
  border-radius: 999px;
  cursor: pointer;
}

.login-switch button.active {
  color: #fff;
  background: var(--brand, #4f46e5);
}

.login-form {
  padding-top: 8px;
}

.login-actions {
  padding: 24px 16px;
}

.login-extra {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
}

.link {
  color: var(--brand, #4f46e5);
  text-decoration: none;
}
</style>
