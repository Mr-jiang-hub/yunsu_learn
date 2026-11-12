<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Field, NavBar, showConfirmDialog, showToast } from 'vant'
import {
  INTERVIEW_STATUS_LABELS,
  interviewApi,
  type InterviewSessionDetailResponse,
} from '@/api/interview'

const props = defineProps<{ sessionId: string }>()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const detail = ref<InterviewSessionDetailResponse | null>(null)
const answerText = ref('')
const submitting = ref(false)
const finishing = ref(false)
const recording = ref(false)
const uploading = ref(false)
const audioUsed = ref(false)

const session = computed(() => detail.value?.session ?? null)
const messages = computed(() => detail.value?.messages ?? [])
const round = computed(() => session.value?.currentRound ?? 1)
const active = computed(() => detail.value?.canAnswer === true)
const finished = computed(
  () => session.value?.status === 'COMPLETED' || session.value?.status === 'INCOMPLETE',
)
const canSubmit = computed(() => active.value && answerText.value.trim().length > 0)

let recorder: MediaRecorder | null = null
let chunks: Blob[] = []
let stream: MediaStream | null = null
const bottomEl = ref<HTMLElement | null>(null)

function scrollToBottom(): void {
  void nextTick(() => bottomEl.value?.scrollIntoView({ behavior: 'smooth', block: 'end' }))
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    detail.value = await interviewApi.detail(Number(props.sessionId))
    scrollToBottom()
  } catch (caught) {
    error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!canSubmit.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    const response = await interviewApi.answer(
      Number(props.sessionId),
      round.value,
      answerText.value.trim(),
      audioUsed.value ? 'AUDIO' : 'TEXT',
    )
    answerText.value = ''
    audioUsed.value = false
    await load()
    if (response.degraded) {
      showToast('追问生成失败，本场按未完成结束')
    } else if (response.completed) {
      showToast('面试已完成，正在生成报告')
    }
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

async function finish(): Promise<void> {
  if (finishing.value) {
    return
  }
  try {
    await showConfirmDialog({
      title: '结束面试',
      message: '提前结束后会按已答轮次出一份「未完成」报告，本场次数不返还。确定结束吗？',
      confirmButtonText: '确定结束',
      cancelButtonText: '继续作答',
    })
  } catch {
    return
  }
  finishing.value = true
  try {
    await interviewApi.finish(Number(props.sessionId), true)
    await goReport()
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '结束失败，请重试')
  } finally {
    finishing.value = false
  }
}

async function goReport(): Promise<void> {
  await router.replace({ name: 'interview-report', params: { sessionId: props.sessionId } })
}

async function toggleRecord(): Promise<void> {
  if (recording.value) {
    recorder?.stop()
    return
  }
  if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
    showToast('当前浏览器不支持录音，请改用文字作答')
    return
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    chunks = []
    recorder = new MediaRecorder(stream)
    recorder.ondataavailable = (event: BlobEvent) => {
      if (event.data.size > 0) {
        chunks.push(event.data)
      }
    }
    recorder.onstop = () => {
      stream?.getTracks().forEach((track) => track.stop())
      stream = null
      recording.value = false
      void upload()
    }
    recorder.start()
    recording.value = true
  } catch {
    showToast('无法访问麦克风，请改用文字作答')
  }
}

async function upload(): Promise<void> {
  if (chunks.length === 0) {
    return
  }
  const blob = new Blob(chunks, { type: 'audio/webm' })
  if (blob.size === 0) {
    return
  }
  if (blob.size > 2 * 1024 * 1024) {
    showToast('录音过长（上限 2MB / 60 秒），请缩短后重试')
    return
  }
  uploading.value = true
  try {
    const response = await interviewApi.uploadAudio(Number(props.sessionId), round.value, blob)
    if (response.asrDegraded) {
      showToast('语音识别失败，请改用文字作答')
      return
    }
    answerText.value = response.transcript
    audioUsed.value = true
    showToast('已转写，确认后发送')
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '转写失败，请改用文字')
  } finally {
    uploading.value = false
  }
}

function leave(): void {
  void router.replace({ name: 'interview' })
}

onMounted(load)
onUnmounted(() => {
  if (recorder?.state === 'recording') {
    recorder.stop()
  }
  stream?.getTracks().forEach((track) => track.stop())
})
</script>

<template>
  <div class="session-page">
    <NavBar
      :title="session ? `${session.difficultyName} · 第 ${round} 轮` : '模拟面试'"
      left-arrow
      @click-left="leave"
    />

    <div class="page">
      <p
        v-if="loading"
        class="muted"
      >
        加载中…
      </p>

      <div
        v-else-if="error"
        class="card"
      >
        <p class="muted">
          {{ error }}
        </p>
        <Button
          block
          round
          type="primary"
          class="retry"
          @click="load"
        >
          重新加载
        </Button>
      </div>

      <template v-else-if="session">
        <section class="card summary">
          <div class="summary-main">
            <span class="subject">{{ session.subjectName }}</span>
            <span
              class="tag"
              :class="`tag-${session.status.toLowerCase().replace('_', '-')}`"
            >
              {{ INTERVIEW_STATUS_LABELS[session.status] }}
            </span>
          </div>
          <p class="muted">
            阶段：{{ session.stageName }} · 已答 {{ messages.filter((m) => m.role === 'STUDENT').length }}/4 轮
          </p>
        </section>

        <div class="chat">
          <div
            v-for="message in messages"
            :key="message.messageId"
            class="bubble-row"
            :class="message.role === 'AI' ? 'from-ai' : 'from-student'"
          >
            <span class="role">{{ message.role === 'AI' ? '面试官' : '我' }}</span>
            <div class="bubble">
              {{ message.content }}
            </div>
          </div>
          <div ref="bottomEl" />
        </div>

        <div
          v-if="finished"
          class="card"
        >
          <p class="muted">
            <template v-if="session.status === 'INCOMPLETE'">
              本场面试按未完成结束，报告将标注「报告不完整」。
            </template>
            <template v-else>
              本场面试已完成，可以查看报告。
            </template>
          </p>
          <Button
            block
            round
            type="primary"
            class="retry"
            @click="goReport"
          >
            查看面试报告
          </Button>
        </div>

        <div
          v-else-if="!active"
          class="card"
        >
          <p class="muted">
            当前状态（{{ INTERVIEW_STATUS_LABELS[session.status] }}）不可作答。
          </p>
          <Button
            block
            round
            type="primary"
            class="retry"
            @click="leave"
          >
            返回面试首页
          </Button>
        </div>
      </template>
    </div>

    <footer
      v-if="active && !loading"
      class="composer"
    >
      <Field
        v-model="answerText"
        type="textarea"
        rows="2"
        autosize
        maxlength="2000"
        placeholder="输入你的回答，或按住右侧按钮语音作答"
        class="composer-field"
      />
      <div class="composer-actions">
        <Button
          size="small"
          round
          plain
          :type="recording ? 'danger' : 'primary'"
          :loading="uploading"
          class="voice"
          @click="toggleRecord"
        >
          {{ recording ? '停止录音' : '语音作答' }}
        </Button>
        <Button
          size="small"
          round
          plain
          class="finish"
          :loading="finishing"
          @click="finish"
        >
          提前结束
        </Button>
        <Button
          size="small"
          round
          type="primary"
          class="send"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="submit"
        >
          发送
        </Button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.session-page {
  min-height: 100vh;
  padding-bottom: 132px;
  background: var(--bg);
}

.summary-main {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.subject {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink);
}

.tag {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.tag-in-progress {
  background: #eef0ff;
  color: var(--brand);
}

.tag-completed {
  background: #e8f7f0;
  color: #22a06b;
}

.tag-incomplete {
  background: #fff5e6;
  color: #d9822b;
}

.tag-expired,
.tag-voided,
.tag-creating {
  background: #f2f3f5;
  color: var(--muted);
}

.chat {
  margin-top: 12px;
}

.bubble-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 14px;
}

.from-ai {
  align-items: flex-start;
}

.from-student {
  align-items: flex-end;
}

.role {
  font-size: 11px;
  color: var(--muted);
}

.bubble {
  max-width: 84%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.from-ai .bubble {
  background: #fff;
  color: var(--ink);
  border-top-left-radius: 4px;
}

.from-student .bubble {
  background: var(--brand);
  color: #fff;
  border-top-right-radius: 4px;
}

.composer {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 10px 14px 14px;
  border-top: 1px solid var(--line);
  background: #fff;
}

.composer-field {
  padding: 6px 10px;
  border-radius: var(--r-sm);
  background: var(--line-2);
}

.composer-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}

.voice,
.finish {
  flex: none;
}

.send {
  margin-left: auto;
}

.retry {
  margin-top: 12px;
}
</style>
