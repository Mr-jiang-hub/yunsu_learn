<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, showToast } from 'vant'
import { learningApi, type LearningPathCard } from '@/api/learning'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const loading = ref(true)
const error = ref('')
const items = ref<LearningPathCard[]>([])
const switching = ref<number | null>(null)

const hasItems = computed(() => items.value.length > 0)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await learningApi.paths()
    items.value = result.items
  } catch (caught) {
    error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openDetail(pathId: number): void {
  void router.push({ name: 'route-detail', params: { pathId: String(pathId) } })
}

async function setCurrent(item: LearningPathCard): Promise<void> {
  if (item.isCurrent || switching.value !== null) {
    return
  }
  switching.value = item.pathId
  try {
    await learningApi.setCurrentPath(item.pathId)
    await Promise.all([load(), session.loadProfile()])
    showToast(`已切换到「${item.pathName}」`)
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '切换失败')
  } finally {
    switching.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <p class="eyebrow">
        Learning Path
      </p>
      <h1>选择你的学习路线</h1>
      <p class="desc">
        路线按阶段组织课程与练习。切换路线不会清除已完成的课程与练习记录。
      </p>
    </header>

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

    <div
      v-else-if="!hasItems"
      class="card"
    >
      <p class="card-title">
        暂无可用路线
      </p>
      <p class="muted">
        运营尚未发布学习路线，稍后再来看看。
      </p>
    </div>

    <template v-else>
      <article
        v-for="item in items"
        :key="item.pathId"
        class="card path-card"
      >
        <div class="path-head">
          <div>
            <h2 class="path-name">
              {{ item.pathName }}
            </h2>
            <p class="muted">
              {{ item.subjectName }} · {{ item.stageCount }} 个阶段 · {{ item.courseCount }} 门课程 ·
              {{ item.questionCount }} 道题
            </p>
          </div>
          <span
            v-if="item.isCurrent"
            class="tag"
          >当前</span>
        </div>

        <div class="path-metrics">
          <div class="metric">
            <span class="metric-label">完成度</span>
            <div class="bar">
              <div
                class="bar-fill"
                :style="{ width: `${Math.min(item.progressRate, 100)}%` }"
              />
            </div>
            <span class="metric-value">{{ item.progressRate }}%</span>
          </div>
          <div class="metric">
            <span class="metric-label">掌握度</span>
            <div class="bar">
              <div
                class="bar-fill bar-mastery"
                :style="{ width: `${Math.min(item.masteryRate, 100)}%` }"
              />
            </div>
            <span class="metric-value">{{ item.masteryRate }}%</span>
          </div>
        </div>

        <div class="path-actions">
          <Button
            size="small"
            round
            plain
            type="primary"
            @click="openDetail(item.pathId)"
          >
            查看阶段
          </Button>
          <Button
            v-if="!item.isCurrent"
            size="small"
            round
            type="primary"
            :loading="switching === item.pathId"
            @click="setCurrent(item)"
          >
            设为当前路线
          </Button>
          <span
            v-else
            class="muted current-hint"
          >正在学习这条路线</span>
        </div>
      </article>
    </template>
  </div>
</template>

<style scoped>
.path-card + .path-card {
  margin-top: 12px;
}

.path-head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
}

.path-name {
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 700;
  color: var(--ink);
}

.path-metrics {
  display: grid;
  gap: 10px;
  margin: 16px 0 14px;
}

.metric {
  display: grid;
  grid-template-columns: 48px 1fr 42px;
  gap: 8px;
  align-items: center;
}

.metric-label {
  font-size: 12px;
  color: var(--muted);
}

.metric-value {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink-2);
  text-align: right;
}

.bar {
  height: 6px;
  border-radius: 999px;
  background: var(--line-2);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--brand);
}

.bar-mastery {
  background: #22a06b;
}

.path-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.current-hint {
  align-self: center;
}

.retry {
  margin-top: 12px;
}
</style>
