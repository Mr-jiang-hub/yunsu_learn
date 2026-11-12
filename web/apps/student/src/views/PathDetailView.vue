<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, NavBar, showToast } from 'vant'
import { learningApi, type PathDetailResponse } from '@/api/learning'
import { useSessionStore } from '@/stores/session'

const props = defineProps<{ pathId: string }>()
const router = useRouter()
const session = useSessionStore()

const loading = ref(true)
const error = ref('')
const detail = ref<PathDetailResponse | null>(null)
const switching = ref(false)

const stages = computed(() => detail.value?.stages ?? [])
const pathInfo = computed(() => detail.value?.path ?? null)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    detail.value = await learningApi.pathDetail(Number(props.pathId))
  } catch (caught) {
    error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openStage(stageId: number): void {
  void router.push({ name: 'stage', params: { stageId: String(stageId) } })
}

async function setCurrent(): Promise<void> {
  if (!pathInfo.value || switching.value) {
    return
  }
  switching.value = true
  try {
    await learningApi.setCurrentPath(pathInfo.value.pathId)
    await Promise.all([load(), session.loadProfile()])
    showToast('已设为当前路线')
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '设置失败')
  } finally {
    switching.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="route-detail">
    <NavBar
      title="路线详情"
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

      <template v-else-if="pathInfo">
        <section class="card">
          <div class="path-head">
            <div>
              <h1>{{ pathInfo.pathName }}</h1>
              <p class="muted">
                {{ pathInfo.subjectName }} · {{ pathInfo.stageCount }} 个阶段 ·
                {{ pathInfo.courseCount }} 门课程 · {{ pathInfo.questionCount }} 道题
              </p>
            </div>
            <span
              v-if="pathInfo.isCurrent"
              class="tag"
            >当前</span>
          </div>
          <p
            v-if="pathInfo.description"
            class="muted desc"
          >
            {{ pathInfo.description }}
          </p>

          <div class="summary">
            <div class="summary-item">
              <span class="summary-value">{{ pathInfo.progressRate }}%</span>
              <span class="summary-label">课程完成度</span>
            </div>
            <div class="summary-item">
              <span class="summary-value">{{ pathInfo.masteryRate }}%</span>
              <span class="summary-label">平均掌握度</span>
            </div>
          </div>

          <Button
            v-if="!pathInfo.isCurrent"
            block
            round
            type="primary"
            :loading="switching"
            @click="setCurrent"
          >
            设为当前学习路线
          </Button>
        </section>

        <h2 class="section-title">
          阶段
        </h2>

        <p
          v-if="stages.length === 0"
          class="card muted"
        >
          该路线还没有阶段，等待运营配置。
        </p>

        <article
          v-for="stage in stages"
          v-else
          :key="stage.stageId"
          class="card stage-card"
          :class="{ disabled: stage.status === 'DISABLED' }"
        >
          <div class="stage-head">
            <h3>{{ stage.stageName }}</h3>
            <span
              v-if="detail?.currentStageId === stage.stageId"
              class="tag tag-now"
            >继续学习</span>
            <span
              v-else-if="stage.status === 'DISABLED'"
              class="tag tag-off"
            >已停用</span>
          </div>
          <p class="muted">
            课程 {{ stage.completedCourseCount }}/{{ stage.courseCount }} · 题目
            {{ stage.questionCount }} 道 ·
            掌握度 {{ stage.hasPracticeSample ? `${stage.masteryRate}%` : '暂无练习' }}
          </p>

          <div class="bar">
            <div
              class="bar-fill"
              :style="{
                width: `${stage.courseCount === 0 ? 0 : Math.min((stage.completedCourseCount / stage.courseCount) * 100, 100)}%`,
              }"
            />
          </div>

          <Button
            size="small"
            round
            plain
            type="primary"
            class="stage-open"
            :disabled="stage.status === 'DISABLED'"
            @click="openStage(stage.stageId)"
          >
            {{ stage.status === 'DISABLED' ? '阶段已停用' : '进入阶段' }}
          </Button>
        </article>
      </template>
    </div>
  </div>
</template>

<style scoped>
.route-detail {
  min-height: 100vh;
  background: var(--bg);
}

.path-head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
}

.path-head h1 {
  margin: 0 0 6px;
  font-size: 20px;
  line-height: 1.35;
  color: var(--ink);
}

.desc {
  margin-top: 12px;
}

.summary {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 16px 0;
}

.summary-item {
  padding: 12px;
  border-radius: var(--r-sm);
  background: var(--line-2);
  text-align: center;
}

.summary-value {
  display: block;
  font-size: 20px;
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

.stage-card + .stage-card {
  margin-top: 12px;
}

.stage-card.disabled {
  opacity: 0.72;
}

.stage-head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.stage-head h3 {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}

.tag-now {
  background: #e8f7f0;
  color: #22a06b;
}

.tag-off {
  background: #f2f3f5;
  color: var(--muted);
}

.bar {
  height: 6px;
  margin: 12px 0;
  border-radius: 999px;
  background: var(--line-2);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--brand);
}

.stage-open {
  margin-top: 2px;
}

.retry {
  margin-top: 12px;
}
</style>
