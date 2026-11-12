<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, showToast } from 'vant'
import {
  INTERVIEW_STATUS_LABELS,
  interviewApi,
  type InterviewQuota,
  type InterviewSessionListItem,
  type InterviewSubjectOption,
} from '@/api/interview'

const router = useRouter()

const loading = ref(true)
const error = ref('')
const starting = ref(false)
const option = ref<InterviewSubjectOption | null>(null)
const quota = ref<InterviewQuota | null>(null)
const history = ref<InterviewSessionListItem[]>([])
const selectedDifficultyId = ref<number | null>(null)

const difficulties = computed(() => option.value?.difficulties ?? [])
const runningSession = computed(
  () => history.value.find((item) => item.status === 'IN_PROGRESS') ?? null,
)
const canStart = computed(
  () => Boolean(option.value?.canStart) && selectedDifficultyId.value !== null,
)

const reasonText = computed(() => {
  if (!option.value || option.value.canStart) {
    return ''
  }
  return option.value.reasonMessage || '当前暂不可开始面试'
})

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }
  return value.slice(0, 16).replace('T', ' ')
}

function statusClass(status: InterviewSessionListItem['status']): string {
  return `tag-${status.toLowerCase().replace('_', '-')}`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [optionsResponse, quotaResponse, historyResponse] = await Promise.all([
      interviewApi.options(),
      interviewApi.quota(),
      interviewApi.list({ page: 1, size: 10 }),
    ])
    option.value = optionsResponse.items[0] ?? null
    quota.value = quotaResponse
    history.value = historyResponse.items
    if (!selectedDifficultyId.value && option.value) {
      selectedDifficultyId.value = option.value.difficulties[0]?.difficultyId ?? null
    }
  } catch (caught) {
    error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function pickDifficulty(id: number): void {
  selectedDifficultyId.value = id
}

async function start(): Promise<void> {
  if (!option.value || selectedDifficultyId.value === null || starting.value) {
    return
  }
  starting.value = true
  try {
    const response = await interviewApi.create(option.value.subjectId, selectedDifficultyId.value)
    await router.push({
      name: 'interview-session',
      params: { sessionId: String(response.session.sessionId) },
    })
  } catch (caught) {
    // 30040 次数用完 / 50002 开场题失败且次数已返还，两种都要重新拉一次余量
    showToast(caught instanceof Error && caught.message ? caught.message : '创建面试失败')
    await load()
  } finally {
    starting.value = false
  }
}

async function openSession(item: InterviewSessionListItem): Promise<void> {
  if (item.status === 'IN_PROGRESS') {
    await router.push({ name: 'interview-session', params: { sessionId: String(item.sessionId) } })
    return
  }
  if (item.status === 'COMPLETED' || item.status === 'INCOMPLETE') {
    await router.push({ name: 'interview-report', params: { sessionId: String(item.sessionId) } })
  }
}

onMounted(load)
</script>

<template>
  <div class="interview-page">
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

      <template v-else>
        <section class="card hero">
          <div class="hero-head">
            <div>
              <h1>AI 模拟面试</h1>
              <p class="muted">
                4 轮对话（开场 1 + 追问 3），结束后出能力维度报告
              </p>
            </div>
            <div class="quota">
              <span class="quota-value">{{ quota?.remaining ?? 0 }}</span>
              <span class="quota-label">今日剩余</span>
            </div>
          </div>
          <p class="muted quota-note">
            每日 {{ quota?.limit ?? 3 }} 次，按北京时间自然日重置。
          </p>
        </section>

        <div
          v-if="!option"
          class="card"
        >
          <p class="muted">
            还没有主学习路线，去「学习路线」选一条再开始面试。
          </p>
          <Button
            block
            round
            type="primary"
            class="retry"
            @click="router.push({ name: 'routes' })"
          >
            去选路线
          </Button>
        </div>

        <template v-else>
          <section class="card">
            <h2 class="card-title">
              {{ option.subjectName }}
            </h2>
            <p class="muted">
              {{ option.pathName }} · {{ option.stageName || '暂无可用阶段' }} ·
              {{ option.dimensionCount }} 个能力维度
            </p>

            <p class="label">
              选择难度
            </p>
            <div class="difficulty-list">
              <button
                v-for="difficulty in difficulties"
                :key="difficulty.difficultyId"
                type="button"
                class="difficulty"
                :class="{ active: selectedDifficultyId === difficulty.difficultyId }"
                @click="pickDifficulty(difficulty.difficultyId)"
              >
                <span class="difficulty-name">{{ difficulty.name }}</span>
                <span class="difficulty-desc">{{ difficulty.description }}</span>
                <span class="difficulty-focus">考察：{{ difficulty.focus }}</span>
              </button>
            </div>

            <p
              v-if="reasonText"
              class="muted reason"
            >
              {{ reasonText }}
            </p>

            <Button
              block
              round
              type="primary"
              class="start"
              :loading="starting"
              :disabled="!canStart"
              @click="start"
            >
              {{ canStart ? '开始面试' : '暂不可开始' }}
            </Button>

            <Button
              v-if="runningSession"
              block
              round
              plain
              type="primary"
              class="continue"
              @click="openSession(runningSession)"
            >
              继续未完成的面试（第 {{ runningSession.currentRound }} 轮）
            </Button>
          </section>

          <h2 class="section-title">
            面试记录
          </h2>

          <p
            v-if="history.length === 0"
            class="card muted"
          >
            还没有面试记录，先来一场吧。
          </p>

          <article
            v-for="item in history"
            v-else
            :key="item.sessionId"
            class="card history-card"
            @click="openSession(item)"
          >
            <div class="history-head">
              <span class="history-title">{{ item.difficultyName }} · {{ item.stageName }}</span>
              <span
                class="tag"
                :class="statusClass(item.status)"
              >
                {{ INTERVIEW_STATUS_LABELS[item.status] }}
              </span>
            </div>
            <p class="muted">
              {{ formatDate(item.startedAt) }}
              <span v-if="item.totalScore !== null"> · 总分 {{ item.totalScore }}</span>
              <span v-else-if="item.status === 'VOIDED'"> · 次数已返还</span>
            </p>
          </article>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.interview-page {
  min-height: 100vh;
  background: var(--bg);
}

.hero-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

h1 {
  margin: 0 0 6px;
  font-size: 20px;
  color: var(--ink);
}

.quota {
  flex: none;
  padding: 8px 14px;
  border-radius: var(--r-sm);
  background: var(--line-2);
  text-align: center;
}

.quota-value {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--brand);
}

.quota-label {
  font-size: 11px;
  color: var(--muted);
}

.quota-note {
  margin: 12px 0 0;
  font-size: 12px;
}

.label {
  margin: 18px 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
}

.difficulty-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.difficulty {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: #fff;
  text-align: left;
}

.difficulty.active {
  border-color: var(--brand);
  background: #f5f6ff;
}

.difficulty-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
}

.difficulty-desc {
  font-size: 12px;
  color: var(--ink-2);
  line-height: 1.5;
}

.difficulty-focus {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
}

.reason {
  margin: 12px 0 0;
  font-size: 12px;
}

.start {
  margin-top: 16px;
}

.continue {
  margin-top: 10px;
}

.section-title {
  margin: 22px 4px 12px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-2);
}

.history-card + .history-card {
  margin-top: 10px;
}

.history-head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.history-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
}

.tag {
  flex: none;
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

.retry {
  margin-top: 12px;
}
</style>
