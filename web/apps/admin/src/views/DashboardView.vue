<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardApi, type DashboardData, type DashboardRange } from '@/api/dashboard'

const loading = ref(false)
const range = ref<DashboardRange>('7D')
const data = ref<DashboardData | null>(null)
const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const RANGE_LABELS: { value: DashboardRange; label: string }[] = [
  { value: 'TODAY', label: '今日' },
  { value: '7D', label: '近 7 天' },
  { value: '30D', label: '近 30 天' },
]

interface StatCard {
  label: string
  value: string
  hint: string
}

function percent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return `${value}%`
}

function integer(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : String(value)
}

const stats = computed<StatCard[]>(() => {
  const overview = data.value?.sections.overview
  const learning = data.value?.sections.learning
  const practice = data.value?.sections.practice
  const interview = data.value?.sections.interview
  return [
    { label: '学员总数', value: integer(overview?.studentTotal), hint: '含已注销学员' },
    { label: '活跃学员', value: integer(overview?.activeStudentCount), hint: '登录/学习/练习/面试去重' },
    { label: '新增学员', value: integer(overview?.newStudentCount), hint: '区间内注册' },
    {
      label: '课程完成率',
      value: percent(learning?.courseCompletionRate),
      hint: `完成 ${integer(learning?.courseCompletionCount)} 门`,
    },
    { label: '阶段掌握度', value: percent(learning?.stageMasteryRate), hint: '最近一次阶段练习正确率均值' },
    {
      label: '练习完成率',
      value: percent(practice?.practiceCompletionRate),
      hint: `练习 ${integer(practice?.practiceCount)} 次`,
    },
    { label: '平均正确率', value: percent(practice?.averageCorrectRate), hint: '按题量加权，仅阶段练习' },
    {
      label: '面试完成率',
      value: percent(interview?.completionRate),
      hint: `场次 ${integer(interview?.sessionCount)}，均分 ${integer(interview?.averageScore)}`,
    },
  ]
})

const chartData = computed(() => {
  const overview = data.value?.sections.overview
  const learning = data.value?.sections.learning
  const practice = data.value?.sections.practice
  const interview = data.value?.sections.interview
  return {
    labels: ['新增学员', '完成课程', '练习次数', '面试场次'],
    values: [
      overview?.newStudentCount ?? 0,
      learning?.courseCompletionCount ?? 0,
      practice?.practiceCount ?? 0,
      interview?.sessionCount ?? 0,
    ],
  }
})

function renderChart(): void {
  if (!chart) {
    return
  }
  chart.setOption({
    grid: { top: 20, right: 24, bottom: 28, left: 48 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: chartData.value.labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#d9dce6' } },
      axisLabel: { color: '#6b7280' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#6b7280' },
      splitLine: { lineStyle: { color: '#eef0f6' } },
    },
    series: [
      {
        name: '数量',
        type: 'bar',
        data: chartData.value.values,
        barWidth: 36,
        itemStyle: { color: '#4F46E5', borderRadius: [6, 6, 0, 0] },
        label: { show: true, position: 'top', color: '#4b5563' },
      },
    ],
  })
}

function resize(): void {
  chart?.resize()
}

async function load(): Promise<void> {
  loading.value = true
  try {
    data.value = await dashboardApi.load(range.value)
    renderChart()
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '加载看板失败')
  } finally {
    loading.value = false
  }
}

watch(range, () => {
  void load()
})

onMounted(() => {
  if (chartEl.value) {
    chart = echarts.init(chartEl.value)
    window.addEventListener('resize', resize)
  }
  void load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div
    v-loading="loading"
    class="dashboard-page"
  >
    <div class="dashboard-toolbar">
      <el-radio-group v-model="range">
        <el-radio-button
          v-for="item in RANGE_LABELS"
          :key="item.value"
          :value="item.value"
        >
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
      <el-button @click="load">
        刷新
      </el-button>
      <span class="dashboard-meta">
        {{ data ? `${data.startDate} ~ ${data.endDate}` : '' }}
        <template v-if="data?.meta.dataAsOf">
          · 数据时间 {{ data.meta.dataAsOf }} · 缓存 {{ data.meta.cacheTtlSeconds }}s
        </template>
      </span>
    </div>

    <el-alert
      v-if="data && data.meta.degradedSections.length > 0"
      type="warning"
      show-icon
      :closable="false"
      class="dashboard-alert"
      :title="`以下区块取数失败已降级：${data.meta.degradedSections.join('、')}`"
    />

    <div class="stat-grid">
      <div
        v-for="item in stats"
        :key="item.label"
        class="admin-card stat-card"
      >
        <p class="stat-label">
          {{ item.label }}
        </p>
        <p class="stat-value">
          {{ item.value }}
        </p>
        <p class="admin-muted">
          {{ item.hint }}
        </p>
      </div>
    </div>

    <div class="dashboard-bottom">
      <div class="admin-card">
        <p class="admin-card-title">
          区间关键量
        </p>
        <div
          ref="chartEl"
          class="chart"
        />
      </div>

      <div class="admin-card side-card">
        <p class="admin-card-title">
          内容与账号
        </p>
        <el-descriptions
          :column="1"
          size="small"
          border
        >
          <el-descriptions-item label="启用学科">
            {{ integer(data?.sections.content?.subjectCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="启用课程">
            {{ integer(data?.sections.content?.courseCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="启用题目">
            {{ integer(data?.sections.content?.questionCount) }}
          </el-descriptions-item>
          <el-descriptions-item label="后台账号">
            {{ integer(data?.sections.users?.adminUserCount) }}（启用
            {{ integer(data?.sections.users?.activeAdminCount) }}）
          </el-descriptions-item>
        </el-descriptions>
        <p class="admin-muted side-hint">
          看板只读，不提供导出；自然日按 Asia/Shanghai 切分。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  min-height: 320px;
}

.dashboard-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.dashboard-meta {
  margin-left: auto;
  color: var(--muted);
  font-size: 12px;
}

.dashboard-alert {
  margin-bottom: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  padding: 18px 20px;
}

.stat-label {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.stat-value {
  margin: 8px 0 6px;
  font-size: 28px;
  font-weight: 800;
  color: var(--ink);
}

.dashboard-bottom {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
  gap: 16px;
}

.chart {
  height: 280px;
  margin-top: 8px;
}

.side-hint {
  margin-top: 12px;
  font-size: 12px;
}
</style>
