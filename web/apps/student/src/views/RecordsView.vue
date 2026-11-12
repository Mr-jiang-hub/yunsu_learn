<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Tabs, Tab } from 'vant'
import {
  PRACTICE_STATUS_LABELS,
  PRACTICE_TYPE_LABELS,
  recordsApi,
  type CourseRecordItem,
  type PracticeRecordItem,
} from '@/api/records'
import {
  INTERVIEW_STATUS_LABELS,
  interviewApi,
  type InterviewSessionListItem,
} from '@/api/interview'

const router = useRouter()

const activeTab = ref(0)
const loading = ref(true)
const error = ref('')

const courses = ref<CourseRecordItem[]>([])
const practices = ref<PracticeRecordItem[]>([])
const interviews = ref<InterviewSessionListItem[]>([])

function formatDate(value: string | null): string {
  if (!value) {
    return '—'
  }
  return value.slice(0, 16).replace('T', ' ')
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [coursePage, practicePage, interviewPage] = await Promise.all([
      recordsApi.courses({ page: 1, size: 30 }),
      recordsApi.practices({ page: 1, size: 30 }),
      interviewApi.list({ page: 1, size: 30 }),
    ])
    courses.value = coursePage.items
    practices.value = practicePage.items
    interviews.value = interviewPage.items
  } catch (caught) {
    error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openInterview(item: InterviewSessionListItem): void {
  if (item.status === 'IN_PROGRESS') {
    void router.push({ name: 'interview-session', params: { sessionId: String(item.sessionId) } })
    return
  }
  if (item.status === 'COMPLETED' || item.status === 'INCOMPLETE') {
    void router.push({ name: 'interview-report', params: { sessionId: String(item.sessionId) } })
  }
}

watch(activeTab, () => {
  window.scrollTo({ top: 0 })
})

onMounted(load)
</script>

<template>
  <div class="records-page">
    <div class="page">
      <h1>学习记录</h1>

      <Tabs
        v-model:active="activeTab"
        shrink
        class="tabs"
      >
        <Tab title="课程">
          <p
            v-if="loading"
            class="muted"
          >
            加载中…
          </p>
          <p
            v-else-if="error"
            class="muted"
          >
            {{ error }}
          </p>
          <template v-else>
            <p
              v-if="courses.length === 0"
              class="card muted"
            >
              还没有完成的课程。
            </p>
            <article
              v-for="item in courses"
              v-else
              :key="item.courseId"
              class="card record-card"
            >
              <div class="record-head">
                <span class="record-title">{{ item.title }}</span>
                <span class="tag tag-done">已完成</span>
              </div>
              <p class="muted">
                {{ item.subjectName }} · {{ item.stageName }} · 完成于 {{ formatDate(item.completedAt) }}
              </p>
            </article>
          </template>
        </Tab>

        <Tab title="练习">
          <p
            v-if="loading"
            class="muted"
          >
            加载中…
          </p>
          <template v-else>
            <p
              v-if="practices.length === 0"
              class="card muted"
            >
              还没有练习记录。
            </p>
            <article
              v-for="item in practices"
              v-else
              :key="item.practiceId"
              class="card record-card"
            >
              <div class="record-head">
                <span class="record-title">{{ item.stageName }}</span>
                <span
                  class="tag"
                  :class="item.status === 'FINISHED' ? 'tag-done' : 'tag-muted'"
                >
                  {{ PRACTICE_STATUS_LABELS[item.status] }}
                </span>
              </div>
              <p class="muted">
                {{ PRACTICE_TYPE_LABELS[item.type] }} · {{ item.correctCount }}/{{ item.questionCount }} 正确
                · 正确率 {{ item.correctRate }}%
                <span v-if="item.masteryEligible"> · 计入掌握度</span>
                <span v-else> · 不计掌握度</span>
              </p>
              <p class="muted">
                {{ formatDate(item.finishedAt || item.startedAt) }}
              </p>
            </article>
          </template>
        </Tab>

        <Tab title="面试">
          <p
            v-if="loading"
            class="muted"
          >
            加载中…
          </p>
          <template v-else>
            <p
              v-if="interviews.length === 0"
              class="card muted"
            >
              还没有面试记录。
            </p>
            <article
              v-for="item in interviews"
              v-else
              :key="item.sessionId"
              class="card record-card"
              @click="openInterview(item)"
            >
              <div class="record-head">
                <span class="record-title">{{ item.difficultyName }} · {{ item.stageName }}</span>
                <span
                  class="tag"
                  :class="item.status === 'COMPLETED' ? 'tag-done' : 'tag-muted'"
                >
                  {{ INTERVIEW_STATUS_LABELS[item.status] }}
                </span>
              </div>
              <p class="muted">
                {{ formatDate(item.startedAt) }}
                <span v-if="item.totalScore !== null"> · 总分 {{ item.totalScore }}</span>
                <span v-if="item.reportStatus === 'INCOMPLETE'"> · 报告不完整</span>
              </p>
            </article>
          </template>
        </Tab>
      </Tabs>

      <Button
        v-if="error && !loading"
        block
        round
        type="primary"
        class="retry"
        @click="load"
      >
        重新加载
      </Button>
    </div>
  </div>
</template>

<style scoped>
.records-page {
  min-height: 100vh;
  background: var(--bg);
}

h1 {
  margin: 0 4px 12px;
  font-size: 20px;
  color: var(--ink);
}

.tabs {
  margin: 0 -4px;
}

.record-card + .record-card {
  margin-top: 10px;
}

.record-head {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
}

.record-title {
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

.tag-done {
  background: #e8f7f0;
  color: #22a06b;
}

.tag-muted {
  background: #f2f3f5;
  color: var(--muted);
}

.retry {
  margin-top: 12px;
}
</style>
