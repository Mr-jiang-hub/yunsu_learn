<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, NavBar, Popup, showToast } from 'vant'
import {
  interviewApi,
  pollReport,
  type InterviewReportResponse,
} from '@/api/interview'
import { recordsApi, type InterviewCompareResponse } from '@/api/records'

const props = defineProps<{ sessionId: string }>()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const report = ref<InterviewReportResponse | null>(null)
const comparing = ref(false)
const compare = ref<InterviewCompareResponse | null>(null)
const compareVisible = ref(false)

const incomplete = computed(() => report.value?.status === 'INCOMPLETE')

/** 找同一学科中"上一场"有正常报告的面试，用于对比 */
async function openCompare(): Promise<void> {
  if (comparing.value) {
    return
  }
  comparing.value = true
  try {
    const [detail, page] = await Promise.all([
      interviewApi.detail(Number(props.sessionId)),
      interviewApi.list({ page: 1, size: 30 }),
    ])
    const current = page.items.find((item) => item.sessionId === Number(props.sessionId))
    const candidates = page.items.filter(
      (item) =>
        item.sessionId !== Number(props.sessionId) &&
        item.reportStatus === 'NORMAL' &&
        item.subjectId === detail.session.subjectId &&
        (!current?.finishedAt || (item.finishedAt ?? '') < current.finishedAt),
    )
    const base = candidates[0]
    if (!base) {
      showToast('还没有可对比的历史面试（需同一学科且报告正常）')
      return
    }
    compare.value = await recordsApi.compare(base.sessionId, Number(props.sessionId))
    compareVisible.value = true
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '对比失败，请稍后重试')
  } finally {
    comparing.value = false
  }
}

function deltaClass(delta: number | null): string {
  if (delta === null || delta === 0) {
    return 'delta-flat'
  }
  return delta > 0 ? 'delta-up' : 'delta-down'
}

function signed(value: number | null): string {
  if (value === null) {
    return '—'
  }
  return value > 0 ? `+${value}` : String(value)
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    // 刚结束的会话报告可能还在生成，30051 时短轮询；30052 表示本场没有报告
    const result = await pollReport(Number(props.sessionId), 5)
    if (!result) {
      error.value = '报告还在生成中，请稍后从面试记录里再打开。'
      return
    }
    report.value = result
  } catch (caught) {
    const code = (caught as { code?: number }).code
    if (code === 30052) {
      error.value = '本场面试没有生成报告（未作答或会话已作废）。'
    } else {
      error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

function scoreWidth(score: number): string {
  return `${Math.max(0, Math.min(100, score))}%`
}

async function backToInterview(): Promise<void> {
  await router.replace({ name: 'interview' })
}

onMounted(load)
</script>

<template>
  <div class="report-page">
    <NavBar
      title="面试报告"
      left-arrow
      @click-left="backToInterview"
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
        <Button
          block
          round
          plain
          type="primary"
          class="retry"
          @click="backToInterview"
        >
          返回面试首页
        </Button>
      </div>

      <template v-else-if="report">
        <section class="card score-card">
          <span
            v-if="incomplete"
            class="badge"
          >报告不完整（未答满 4 轮）</span>
          <p class="score">
            {{ report.totalScore ?? '—' }}
            <span class="score-unit">分</span>
          </p>
          <p class="muted">
            生成于 {{ report.generatedAt ? report.generatedAt.slice(0, 16).replace('T', ' ') : '—' }}
          </p>
        </section>

        <section class="card">
          <h2 class="card-title">
            总评
          </h2>
          <p class="summary">
            {{ report.summary || '暂无总评' }}
          </p>
        </section>

        <section class="card">
          <h2 class="card-title">
            能力维度
          </h2>
          <p
            v-if="report.dimensions.length === 0"
            class="muted"
          >
            该学科未配置能力维度，暂无维度得分。
          </p>
          <div
            v-for="dimension in report.dimensions"
            :key="dimension.dimensionId"
            class="dimension"
          >
            <div class="dimension-head">
              <span>{{ dimension.dimensionName }}</span>
              <span class="dimension-score">{{ dimension.score }}</span>
            </div>
            <div class="bar">
              <div
                class="bar-fill"
                :style="{ width: scoreWidth(dimension.score) }"
              />
            </div>
          </div>
        </section>

        <section class="card">
          <h2 class="card-title">
            改进建议
          </h2>
          <p
            v-if="report.suggestions.length === 0"
            class="muted"
          >
            暂无建议。
          </p>
          <ol
            v-else
            class="suggestions"
          >
            <li
              v-for="(item, index) in report.suggestions"
              :key="index"
            >
              {{ item }}
            </li>
          </ol>
        </section>

        <Button
          block
          round
          type="primary"
          class="retry"
          @click="backToInterview"
        >
          再来一场
        </Button>
        <Button
          block
          round
          plain
          type="primary"
          class="retry"
          :loading="comparing"
          @click="openCompare"
        >
          和历史面试对比
        </Button>
      </template>
    </div>

    <Popup
      v-model:show="compareVisible"
      position="bottom"
      round
      class="compare-popup"
    >
      <div class="compare-body">
        <h2>与上次面试对比</h2>
        <p
          v-if="compare"
          class="muted"
        >
          {{ compare.base.finishedAt?.slice(0, 10) }} {{ compare.base.totalScore }} 分 →
          {{ compare.target.finishedAt?.slice(0, 10) }} {{ compare.target.totalScore }} 分
        </p>
        <p
          v-if="compare"
          class="total-delta"
          :class="deltaClass(compare.totalDelta)"
        >
          总分 {{ signed(compare.totalDelta) }}
        </p>
        <div
          v-for="dimension in compare?.dimensions ?? []"
          :key="dimension.dimensionId"
          class="compare-row"
        >
          <span class="compare-name">{{ dimension.dimensionName }}</span>
          <span class="compare-score">
            {{ dimension.baseScore ?? '—' }} → {{ dimension.targetScore ?? '—' }}
          </span>
          <span
            class="compare-delta"
            :class="deltaClass(dimension.delta)"
          >{{ signed(dimension.delta) }}</span>
        </div>
        <Button
          block
          round
          plain
          class="close"
          @click="compareVisible = false"
        >
          关闭
        </Button>
      </div>
    </Popup>
  </div>
</template>

<style scoped>
.report-page {
  min-height: 100vh;
  background: var(--bg);
}

.score-card {
  text-align: center;
}

.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  background: #fff5e6;
  color: #d9822b;
  font-size: 12px;
  font-weight: 600;
}

.score {
  margin: 10px 0 4px;
  font-size: 40px;
  font-weight: 700;
  color: var(--brand);
  line-height: 1.1;
}

.score-unit {
  font-size: 14px;
  font-weight: 500;
  color: var(--muted);
}

.summary {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--ink);
}

.dimension + .dimension {
  margin-top: 14px;
}

.dimension-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ink-2);
}

.dimension-score {
  font-weight: 700;
  color: var(--ink);
}

.bar {
  height: 8px;
  margin-top: 6px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--line-2);
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  background: var(--brand);
}

.suggestions {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--ink);
}

.retry {
  margin-top: 12px;
}

.compare-body {
  padding: 20px 18px 24px;
}

.compare-body h2 {
  margin: 0 0 8px;
  font-size: 16px;
  color: var(--ink);
}

.total-delta {
  margin: 10px 0 4px;
  font-size: 15px;
  font-weight: 700;
}

.compare-row {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}

.compare-name {
  flex: 1;
  color: var(--ink-2);
}

.compare-score {
  color: var(--muted);
}

.compare-delta {
  width: 56px;
  text-align: right;
  font-weight: 700;
}

.delta-up {
  color: #22a06b;
}

.delta-down {
  color: #d9822b;
}

.delta-flat {
  color: var(--muted);
}

.close {
  margin-top: 16px;
}
</style>
