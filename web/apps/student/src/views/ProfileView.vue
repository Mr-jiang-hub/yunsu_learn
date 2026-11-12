<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Cell, CellGroup, Field, Popup, showConfirmDialog, showToast } from 'vant'
import { profileApi } from '@/api/profile'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

type Panel = 'nickname' | 'password' | 'phone' | 'cancel' | null

const panel = ref<Panel>(null)
const submitting = ref(false)
const uploading = ref(false)

const nicknameDraft = ref('')
/** 改密支持两种凭证：原密码或验证码二选一（PRD 5.10 / 缺口 G-02） */
const passwordMode = ref<'password' | 'sms'>('password')
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirm: '', smsCode: '' })
const phoneForm = reactive({ newPhone: '', oldCode: '', newCode: '' })
const cancelForm = reactive({ code: '', confirmText: '' })

/** 四处验证码各自独立倒计时，避免互相覆盖 */
const countdown = reactive({ old: 0, new: 0, cancel: 0, password: 0 })
const timers: Record<string, number> = {}

const profile = computed(() => session.profile)
const avatarUrl = computed(() => profile.value?.avatarUrl || '')
const initial = computed(() => (profile.value?.nickname ?? '学').slice(0, 1))
const statusText = computed(() => {
  if (profile.value?.status === 2) {
    return '已停用'
  }
  if (profile.value?.status === 3) {
    return '已注销'
  }
  return '正常'
})
const phonePattern = /^1[3-9]\d{9}$/
const passwordUsable = computed(() => {
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(passwordForm.newPassword)) {
    return false
  }
  if (passwordForm.newPassword !== passwordForm.confirm) {
    return false
  }
  // 用原密码时不能与原密码相同；用验证码时无从比较，只靠后端校验
  if (passwordMode.value === 'password') {
    return passwordForm.oldPassword !== '' && passwordForm.newPassword !== passwordForm.oldPassword
  }
  return /^\d{6}$/.test(passwordForm.smsCode)
})

onMounted(() => {
  void session.loadProfile()
})

function startCountdown(key: 'old' | 'new' | 'cancel' | 'password', seconds: number): void {
  countdown[key] = Math.max(1, seconds)
  window.clearInterval(timers[key])
  timers[key] = window.setInterval(() => {
    countdown[key] -= 1
    if (countdown[key] <= 0) {
      window.clearInterval(timers[key])
    }
  }, 1000)
}

function message(caught: unknown, fallback: string): string {
  return caught instanceof Error && caught.message ? caught.message : fallback
}

function openPanel(target: Panel): void {
  if (target === 'nickname') {
    nicknameDraft.value = profile.value?.nickname ?? ''
  }
  if (target === 'password') {
    passwordMode.value = 'password'
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirm = ''
    passwordForm.smsCode = ''
  }
  if (target === 'phone') {
    phoneForm.newPhone = ''
    phoneForm.oldCode = ''
    phoneForm.newCode = ''
  }
  if (target === 'cancel') {
    cancelForm.code = ''
    cancelForm.confirmText = ''
  }
  panel.value = target
}

// ------------------------------------------------------------------ 昵称 / 头像

async function saveNickname(): Promise<void> {
  const nickname = nicknameDraft.value.trim()
  if (!nickname) {
    showToast('昵称不能为空')
    return
  }
  if (nickname === profile.value?.nickname) {
    panel.value = null
    return
  }
  submitting.value = true
  try {
    session.profile = await profileApi.update({ nickname })
    showToast('昵称已更新')
    panel.value = null
  } catch (caught) {
    showToast(message(caught, '保存失败'))
  } finally {
    submitting.value = false
  }
}

async function onAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
    showToast('只支持 JPG / PNG / WEBP')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    showToast('图片不能超过 2MB')
    return
  }
  uploading.value = true
  try {
    const uploaded = await profileApi.uploadAvatar(file)
    session.profile = await profileApi.update({ avatarKey: uploaded.key })
    showToast('头像已更新')
  } catch (caught) {
    showToast(message(caught, '上传失败'))
  } finally {
    uploading.value = false
  }
}

// ------------------------------------------------------------------ 改密码

async function savePassword(): Promise<void> {
  if (!passwordUsable.value) {
    showToast(
      passwordMode.value === 'password'
        ? '新密码需 8-32 位且同时包含字母和数字，两次输入需一致'
        : '请填写 6 位验证码，新密码需 8-32 位且同时包含字母和数字',
    )
    return
  }
  submitting.value = true
  try {
    // 二选一：只传当前模式对应的那一种凭证，另一种置 null，避免后端判定"都传"
    await profileApi.changePassword(
      passwordMode.value === 'password'
        ? { oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword, smsCode: null }
        : { oldPassword: null, newPassword: passwordForm.newPassword, smsCode: passwordForm.smsCode },
    )
    panel.value = null
    showToast('密码已修改，请重新登录')
    await session.logout()
    await router.replace('/login')
  } catch (caught) {
    showToast(message(caught, '修改密码失败'))
  } finally {
    submitting.value = false
  }
}

/** 验证码改密：发给本人已绑定手机号（前端只有掩码号） */
async function sendPasswordCode(): Promise<void> {
  try {
    const result = await profileApi.sendSelfCode('CHANGE_PASSWORD')
    startCountdown('password', result.retryAfterSeconds)
    showToast('验证码已发送到当前手机号')
  } catch (caught) {
    showToast(message(caught, '发送失败'))
  }
}

// ------------------------------------------------------------------ 换绑手机号

async function sendOldCode(): Promise<void> {
  try {
    const result = await profileApi.sendSelfCode('CHANGE_PHONE_OLD')
    startCountdown('old', result.retryAfterSeconds)
    showToast('验证码已发送到当前手机号')
  } catch (caught) {
    showToast(message(caught, '发送失败'))
  }
}

async function sendNewCode(): Promise<void> {
  if (!phonePattern.test(phoneForm.newPhone)) {
    showToast('请输入正确的新手机号')
    return
  }
  try {
    const result = await session.sendSmsCode(phoneForm.newPhone, 'CHANGE_PHONE_NEW')
    startCountdown('new', result.retryAfterSeconds)
    showToast('验证码已发送到新手机号')
  } catch (caught) {
    showToast(message(caught, '发送失败'))
  }
}

async function savePhone(): Promise<void> {
  if (!phonePattern.test(phoneForm.newPhone)) {
    showToast('请输入正确的新手机号')
    return
  }
  if (!phoneForm.oldCode || !phoneForm.newCode) {
    showToast('请填写两个验证码')
    return
  }
  submitting.value = true
  try {
    const result = await profileApi.changePhone(phoneForm.oldCode, phoneForm.newPhone, phoneForm.newCode)
    panel.value = null
    showToast(`手机号已换绑为 ${result.phoneMasked}，请重新登录`)
    await session.logout()
    await router.replace('/login')
  } catch (caught) {
    showToast(message(caught, '换绑失败'))
  } finally {
    submitting.value = false
  }
}

// ------------------------------------------------------------------ 注销账号

async function sendCancelCode(): Promise<void> {
  try {
    const result = await profileApi.sendSelfCode('CANCEL')
    startCountdown('cancel', result.retryAfterSeconds)
    showToast('注销验证码已发送')
  } catch (caught) {
    showToast(message(caught, '发送失败'))
  }
}

async function doCancel(): Promise<void> {
  if (cancelForm.confirmText.trim() !== '注销') {
    showToast('请输入确认文本「注销」')
    return
  }
  if (!cancelForm.code) {
    showToast('请填写验证码')
    return
  }
  try {
    await showConfirmDialog({
      title: '确认注销账号',
      message: '注销后手机号会释放、历史学习记录脱敏，且无法恢复。确定继续吗？',
      confirmButtonText: '确认注销',
      confirmButtonColor: '#e5484d',
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    await profileApi.cancel(cancelForm.code, cancelForm.confirmText.trim())
    panel.value = null
    showToast('账号已注销')
    session.resetLocal()
    await router.replace('/login')
  } catch (caught) {
    showToast(message(caught, '注销失败'))
  } finally {
    submitting.value = false
  }
}

async function onLogout(): Promise<void> {
  await session.logout()
  showToast('已退出登录')
  await router.replace('/login')
}
</script>

<template>
  <div class="profile">
    <section class="profile-card">
      <label class="avatar-wrap">
        <img
          v-if="avatarUrl"
          :src="avatarUrl"
          alt="头像"
          class="avatar-img"
        >
        <span
          v-else
          class="avatar"
        >{{ initial }}</span>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          class="file-input"
          @change="onAvatarChange"
        >
        <span class="avatar-hint">{{ uploading ? '上传中…' : '更换头像' }}</span>
      </label>
      <div class="profile-info">
        <h1>{{ profile?.nickname || '学员' }}</h1>
        <p class="muted">
          {{ profile?.phoneMasked || '未绑定手机号' }}
        </p>
        <span class="tag">{{ statusText }}</span>
      </div>
    </section>

    <CellGroup inset>
      <Cell
        title="当前学习路线"
        :value="profile?.currentPath?.pathName || '未选择'"
        is-link
        to="/routes"
      />
      <Cell
        title="注册时间"
        :value="profile?.createdAt ? profile.createdAt.slice(0, 10) : '—'"
      />
      <Cell
        title="账号状态"
        :value="statusText"
      />
      <Cell
        title="学习记录"
        is-link
        to="/records"
      />
    </CellGroup>

    <CellGroup
      inset
      class="group-gap"
    >
      <Cell
        title="修改昵称"
        is-link
        @click="openPanel('nickname')"
      />
      <Cell
        title="修改密码"
        is-link
        @click="openPanel('password')"
      />
      <Cell
        title="换绑手机号"
        is-link
        @click="openPanel('phone')"
      />
    </CellGroup>

    <div class="profile-actions">
      <Button
        block
        round
        plain
        type="danger"
        @click="onLogout"
      >
        退出登录
      </Button>
      <button
        type="button"
        class="danger-link"
        @click="openPanel('cancel')"
      >
        注销账号
      </button>
    </div>

    <!-- 修改昵称 -->
    <Popup
      :show="panel === 'nickname'"
      position="bottom"
      round
      :style="{ padding: '20px 16px 24px' }"
      @update:show="panel = null"
    >
      <h2 class="sheet-title">
        修改昵称
      </h2>
      <Field
        v-model="nicknameDraft"
        label="昵称"
        maxlength="50"
        placeholder="1-50 个字符"
      />
      <Button
        block
        round
        type="primary"
        class="sheet-btn"
        :loading="submitting"
        @click="saveNickname"
      >
        保存
      </Button>
    </Popup>

    <!-- 修改密码 -->
    <Popup
      :show="panel === 'password'"
      position="bottom"
      round
      :style="{ padding: '20px 16px 24px' }"
      @update:show="panel = null"
    >
      <h2 class="sheet-title">
        修改密码
      </h2>
      <div class="mode-switch">
        <button
          type="button"
          :class="['mode-btn', { active: passwordMode === 'password' }]"
          @click="passwordMode = 'password'"
        >
          用原密码
        </button>
        <button
          type="button"
          :class="['mode-btn', { active: passwordMode === 'sms' }]"
          @click="passwordMode = 'sms'"
        >
          用验证码
        </button>
      </div>
      <Field
        v-if="passwordMode === 'password'"
        v-model="passwordForm.oldPassword"
        type="password"
        label="原密码"
        placeholder="请输入原密码"
      />
      <Field
        v-else
        v-model="passwordForm.smsCode"
        label="验证码"
        placeholder="6 位数字"
        maxlength="6"
      >
        <template #button>
          <Button
            size="small"
            type="primary"
            :disabled="countdown.password > 0"
            @click="sendPasswordCode"
          >
            {{ countdown.password > 0 ? `${countdown.password}s` : '发送验证码' }}
          </Button>
        </template>
      </Field>
      <Field
        v-model="passwordForm.newPassword"
        type="password"
        label="新密码"
        placeholder="8-32 位，含字母和数字"
      />
      <Field
        v-model="passwordForm.confirm"
        type="password"
        label="确认密码"
        placeholder="再次输入新密码"
      />
      <p class="sheet-tip">
        {{ passwordMode === 'sms' ? '验证码将发送到当前绑定的手机号。' : '' }}修改成功后需要用新密码重新登录。
      </p>
      <Button
        block
        round
        type="primary"
        class="sheet-btn"
        :loading="submitting"
        :disabled="!passwordUsable"
        @click="savePassword"
      >
        确认修改
      </Button>
    </Popup>

    <!-- 换绑手机号 -->
    <Popup
      :show="panel === 'phone'"
      position="bottom"
      round
      :style="{ padding: '20px 16px 24px' }"
      @update:show="panel = null"
    >
      <h2 class="sheet-title">
        换绑手机号
      </h2>
      <p class="sheet-tip">
        当前手机号 {{ profile?.phoneMasked || '—' }}，需先验证原号，再验证新号。
      </p>
      <Field
        v-model="phoneForm.oldCode"
        label="原号验证码"
        maxlength="6"
        placeholder="6 位验证码"
      >
        <template #button>
          <Button
            size="small"
            :disabled="countdown.old > 0"
            @click="sendOldCode"
          >
            {{ countdown.old > 0 ? `${countdown.old}s` : '发送' }}
          </Button>
        </template>
      </Field>
      <Field
        v-model="phoneForm.newPhone"
        label="新手机号"
        type="tel"
        maxlength="11"
        placeholder="请输入新手机号"
      />
      <Field
        v-model="phoneForm.newCode"
        label="新号验证码"
        maxlength="6"
        placeholder="6 位验证码"
      >
        <template #button>
          <Button
            size="small"
            :disabled="countdown.new > 0"
            @click="sendNewCode"
          >
            {{ countdown.new > 0 ? `${countdown.new}s` : '发送' }}
          </Button>
        </template>
      </Field>
      <Button
        block
        round
        type="primary"
        class="sheet-btn"
        :loading="submitting"
        @click="savePhone"
      >
        确认换绑
      </Button>
    </Popup>

    <!-- 注销账号 -->
    <Popup
      :show="panel === 'cancel'"
      position="bottom"
      round
      :style="{ padding: '20px 16px 24px' }"
      @update:show="panel = null"
    >
      <h2 class="sheet-title">
        注销账号
      </h2>
      <p class="sheet-tip cancel-tip">
        注销后手机号会释放、昵称与头像清空、学习记录脱敏，且无法恢复。
      </p>
      <Field
        v-model="cancelForm.code"
        label="验证码"
        maxlength="6"
        placeholder="发送到当前手机号"
      >
        <template #button>
          <Button
            size="small"
            :disabled="countdown.cancel > 0"
            @click="sendCancelCode"
          >
            {{ countdown.cancel > 0 ? `${countdown.cancel}s` : '发送' }}
          </Button>
        </template>
      </Field>
      <Field
        v-model="cancelForm.confirmText"
        label="确认文本"
        placeholder="请输入「注销」"
      />
      <Button
        block
        round
        type="danger"
        class="sheet-btn"
        :loading="submitting"
        @click="doCancel"
      >
        注销账号
      </Button>
    </Popup>
  </div>
</template>

<style scoped>
.profile {
  min-height: 100vh;
  padding: 16px 0 24px;
  background: var(--bg);
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 8px 16px 20px;
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 16px;
}

.avatar-wrap {
  position: relative;
  flex: none;
  cursor: pointer;
}

.avatar,
.avatar-img {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  background: var(--brand, #4f46e5);
  border-radius: 50%;
  object-fit: cover;
}

.file-input {
  display: none;
}

.avatar-hint {
  position: absolute;
  bottom: -14px;
  left: 50%;
  font-size: 10px;
  color: var(--brand);
  white-space: nowrap;
  transform: translateX(-50%);
}

.profile-info h1 {
  margin: 0 0 4px;
  font-size: 18px;
  color: var(--ink);
}

.profile-info .muted {
  margin: 0 0 6px;
  font-size: 13px;
}

.group-gap {
  margin-top: 12px;
}

.profile-actions {
  padding: 24px 16px 0;
}

.danger-link {
  display: block;
  width: 100%;
  margin-top: 14px;
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--muted);
  text-align: center;
}

.sheet-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}

.sheet-tip {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--muted);
}

.mode-switch {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.mode-btn {
  flex: 1;
  padding: 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--muted, #6b7280);
  background: var(--line-2, #f0f2f8);
  border: none;
  border-radius: 999px;
  cursor: pointer;
}

.mode-btn.active {
  color: #fff;
  background: var(--brand, #4f46e5);
}

.cancel-tip {
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: #fff1f0;
  color: #c9403f;
}

.sheet-btn {
  margin-top: 18px;
}
</style>
