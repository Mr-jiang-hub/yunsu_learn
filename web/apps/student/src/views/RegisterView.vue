<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, CellGroup, Field, Form, NavBar, showToast } from 'vant'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const phone = ref('')
const code = ref('')
const password = ref('')
const nickname = ref('')
const submitting = ref(false)
const countdown = ref(0)
let timer: number | undefined

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const canSendCode = computed(() => countdown.value === 0 && phoneValid.value)
const passwordValid = computed(() => /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(password.value))

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
    const result = await session.sendSmsCode(phone.value, 'REGISTER')
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
  if (!code.value) {
    showToast('请输入验证码')
    return
  }
  if (!passwordValid.value) {
    showToast('密码需 8-32 位且同时包含字母和数字')
    return
  }
  submitting.value = true
  try {
    await session.register(phone.value, code.value, password.value, nickname.value.trim())
    await router.replace('/home')
  } catch (error) {
    showToast(errorMessage(error, '注册失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="register">
    <NavBar
      title="注册"
      left-arrow
      @click-left="router.back()"
    />
    <section class="register-hero">
      <h1>创建账号</h1>
      <p class="muted">
        手机号注册后即可开始学习；昵称留空将自动生成。
      </p>
    </section>

    <Form
      class="register-form"
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
        <Field
          v-model="password"
          name="password"
          label="密码"
          placeholder="8-32 位，含字母和数字"
          type="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
        <Field
          v-model="nickname"
          name="nickname"
          label="昵称"
          placeholder="选填，最多 20 字"
          maxlength="20"
        />
      </CellGroup>

      <div class="register-actions">
        <Button
          block
          round
          type="primary"
          native-type="submit"
          :loading="submitting"
        >
          注册并登录
        </Button>
        <div class="register-extra">
          <RouterLink
            class="link"
            to="/login"
          >
            已有账号？去登录
          </RouterLink>
        </div>
      </div>
    </Form>
  </div>
</template>

<style scoped>
.register {
  min-height: 100vh;
  background: var(--bg);
}

.register-hero {
  padding: 24px;
}

.register-hero h1 {
  margin: 0 0 8px;
  font-size: 24px;
  color: var(--ink);
}

.register-actions {
  padding: 24px 16px;
}

.register-extra {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
}

.link {
  color: var(--brand, #4f46e5);
  text-decoration: none;
}
</style>
