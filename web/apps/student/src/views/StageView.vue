<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, NavBar, showToast } from 'vant'
import {
  formatDuration,
  learningApi,
  type StageCourseItem,
  type StageDetailResponse,
} from '@/api/learning'
import { recordsApi, type MasteryTrendResponse } from '@/api/records'

const props = defineProps<{ stageId: string }>()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const detail = ref<StageDetailResponse | null>(null)
const trend = ref<MasteryTrendResponse | null>(null)
const busyCourseId = ref<number | null>(null)

const courses = computed(() => detail.value?.courses ?? [])
const stageInfo = computed(() => detail.value?.stage ?? null)
const progress = computed(() => detail.value?.progress ?? null)
const practice = computed(() => detail.value?.practice ?? null)

const statusText: Record<StageCourseItem['progressStatus'], string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '学习中',
  COMPLETED: '已完成',
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    detail.value = await learningApi.stage(Number(props.stageId))
    // 趋势是辅助信息，取不到不影响主流程
    trend.value = await recordsApi.masteryTrend(Number(props.stageId), 8).catch(() => null)
  } catch (caught) {
    error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/**
 * 开始学习后按 PRD 5.4 跳转 B 站（评审缺口 G-01）。
 * 地址由服务端下发，前端不拼接；未配置视频时只记录进度。
 */
async function startCourse(course: StageCourseItem): Promise<void> {
  if (busyCourseId.value !== null) {
    return
  }
  busyCourseId.value = course.courseId
  try {
    const result = await learningApi.startCourse(course.courseId)
    showToast('已记录开始学习')
    await load()
    if (result.bilibiliUrl) {
      window.open(result.bilibiliUrl, '_blank', 'noopener')
    }
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '操作失败')
  } finally {
    busyCourseId.value = null
  }
}

/** 已开始的课程可随时重看视频；地址为空时不展示入口 */
function watchVideo(course: StageCourseItem): void {
  if (!course.bilibiliUrl) {
    showToast('该课程未配置视频')
    return
  }
  window.open(course.bilibiliUrl, '_blank', 'noopener')
}

async function completeCourse(course: StageCourseItem): Promise<void> {
  if (busyCourseId.value !== null) {
    return
  }
  busyCourseId.value = course.courseId
  try {
    await learningApi.completeCourse(course.courseId)
    showToast('已标记完成')
    await load()
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '操作失败')
  } finally {
    busyCourseId.value = null
  }
}

function openQuiz(): void {
  if (!detail.value?.practice.canStart) {
    showToast(detail.value?.practice.reason || '暂不可练习')
    return
  }
  void router.push({ name: 'quiz', params: { stageId: String(props.stageId) } })
}

onMounted(load)
</script>

<template>
  <div class="stage-page">
    <NavBar
      title="阶段学习"
      left-arrow
      @click-left="router.back()"
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

      <template v-else-if="stageInfo && progress">
        <section class="card">
          <h1>{{ stageInfo.stageName }}</h1>
          <p class="muted">
            {{ stageInfo.pathName }}
          </p>

          <div class="summary">
            <div class="summary-item">
              <span class="summary-value">{{ progress.completedCourseCount }}/{{ progress.courseCount }}</span>
              <span class="summary-label">课程完成</span>
            </div>
            <div class="summary-item">
              <span class="summary-value">{{ progress.progressRate }}%</span>
              <span class="summary-label">完成度</span>
            </div>
            <div class="summary-item">
              <span class="summary-value">{{ progress.hasPracticeSample ? `${progress.masteryRate}%` : '—' }}</span>
              <span class="summary-label">掌握度</span>
            </div>
          </div>

          <p
            v-if="!progress.hasPracticeSample"
            class="muted"
          >
            该阶段还没有完成的练习，掌握度暂不可用。
          </p>
        </section>

        <section
          v-if="trend && trend.points.length > 0"
          class="card trend-card"
        >
          <h2 class="card-title">
            掌握度趋势
          </h2>
          <p class="muted">
            最近 {{ trend.points.length }} 次阶段练习的正确率（错题重练不计入）
          </p>
          <div
            v-for="point in trend.points"
            :key="point.practiceId"
            class="trend-row"
          >
            <span class="trend-date">{{ point.finishedAt?.slice(5, 10) ?? '—' }}</span>
            <div class="trend-bar">
              <div
                class="trend-fill"
                :style="{ width: `${Math.max(2, Math.min(100, point.correctRate))}%` }"
              />
            </div>
            <span class="trend-value">{{ point.correctRate }}%</span>
          </div>
        </section>

        <h2 class="section-title">
          课程
        </h2>
        <p
          v-if="courses.length === 0"
          class="card muted"
        >
          该阶段暂无课程。
        </p>

        <article
          v-for="course in courses"
          v-else
          :key="course.courseId"
          class="card course-card"
        >
          <div class="course-head">
            <h3>{{ course.title }}</h3>
            <span
              class="status"
              :class="`status-${course.progressStatus.toLowerCase()}`"
            >
              {{ statusText[course.progressStatus] }}
            </span>
          </div>
          <p class="muted">
            <span v-if="formatDuration(course.durationSeconds)">
              {{ formatDuration(course.durationSeconds) }}
            </span>
            <span v-if="course.status === 'DISABLED'"> · 已停用</span>
            <span v-if="course.completedAt"> · 完成于 {{ course.completedAt.slice(0, 10) }}</span>
          </p>

          <div class="course-actions">
            <Button
              v-if="course.progressStatus === 'NOT_STARTED'"
              size="small"
              round
              type="primary"
              :loading="busyCourseId === course.courseId"
              :disabled="course.status === 'DISABLED'"
              @click="startCourse(course)"
            >
              开始学习
            </Button>
            <Button
              v-else-if="course.progressStatus === 'IN_PROGRESS'"
              size="small"
              round
              type="primary"
              :loading="busyCourseId === course.courseId"
              @click="completeCourse(course)"
            >
              标记完成
            </Button>
            <span
              v-else
              class="muted"
            >已完成</span>
            <Button
              v-if="course.bilibiliUrl && course.progressStatus !== 'NOT_STARTED'"
              size="small"
              round
              plain
              @click="watchVideo(course)"
            >
              观看视频
            </Button>
          </div>
        </article>

        <section class="card practice-card">
          <h2 class="card-title">
            随堂练习
          </h2>
          <p class="muted">
            <template v-if="practice">
              <template v-if="practice.canStart">
                本阶段共 {{ practice.questionCount }} 道题，完成后计入掌握度。
              </template>
              <template v-else>
                {{ practice.reason }}
              </template>
            </template>
          </p>
          <Button
            block
            round
            type="primary"
            class="practice-start"
            :disabled="!practice?.canStart"
            @click="openQuiz"
          >
            {{ practice?.canStart ? '开始随堂练习' : '暂不可练习' }}
          </Button>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.stage-page {
  min-height: 100vh;
  background: var(--bg);
}

h1 {
  margin: 0 0 6px;
  font-size: 20px;
  line-height: 1.35;
  color: var(--ink);
}

.summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin: 16px 0;
}

.summary-item {
  padding: 12px 8px;
  border-radius: var(--r-sm);
  background: var(--line-2);
  text-align: center;
}

.summary-value {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: var(--brand);
}

.summary-label {
  font-size: 12px;
  color: var(--muted);
}

.section-title {
  margin: 22px 4px 12px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-2);
}

.trend-card {
  margin-top: 16px;
}

.trend-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
  font-size: 12px;
  color: var(--muted);
}

.trend-date {
  width: 44px;
  flex: none;
}

.trend-bar {
  flex: 1;
  height: 8px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--line-2);
}

.trend-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--brand);
}

.trend-value {
  width: 40px;
  flex: none;
  text-align: right;
  font-weight: 700;
  color: var(--ink);
}

.course-card + .course-card {
  margin-top: 12px;
}

.course-head {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
}

.course-head h3 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.45;
  color: var(--ink);
}

.status {
  flex: none;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.status-not_started {
  background: #f2f3f5;
  color: var(--muted);
}

.status-in_progress {
  background: #eef0ff;
  color: var(--brand);
}

.status-completed {
  background: #e8f7f0;
  color: #22a06b;
}

.course-actions {
  margin-top: 12px;
}

.practice-card {
  margin-top: 16px;
}

.practice-start {
  margin-top: 14px;
}

.retry {
  margin-top: 12px;
}
</style>
