<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, Empty, Popup, showToast } from 'vant'
import {
  QUESTION_TYPE_LABELS,
  wrongBookApi,
  type WrongQuestionDetail,
  type WrongQuestionItem,
} from '@/api/practice'

const router = useRouter()

const loading = ref(true)
const errorText = ref('')
const items = ref<WrongQuestionItem[]>([])
const page = ref(1)
const size = 10
const total = ref(0)
const hasNext = ref(false)
const loadingMore = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<WrongQuestionDetail | null>(null)

const isEmpty = computed(() => !loading.value && items.value.length === 0)

function toMessage(caught: unknown, fallback: string): string {
  return caught instanceof Error && caught.message ? caught.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const result = await wrongBookApi.list({ page: 1, size })
    items.value = result.items
    total.value = result.total
    hasNext.value = result.hasNext
    page.value = 1
  } catch (caught) {
    errorText.value = toMessage(caught, '加载错题本失败')
  } finally {
    loading.value = false
  }
}

async function loadMore(): Promise<void> {
  if (!hasNext.value || loadingMore.value) {
    return
  }
  loadingMore.value = true
  try {
    const next = page.value + 1
    const result = await wrongBookApi.list({ page: next, size })
    items.value = [...items.value, ...result.items]
    hasNext.value = result.hasNext
    page.value = next
  } catch (caught) {
    showToast(toMessage(caught, '加载更多失败'))
  } finally {
    loadingMore.value = false
  }
}

async function openDetail(questionId: number): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await wrongBookApi.detail(questionId)
  } catch (caught) {
    showToast(toMessage(caught, '加载错题详情失败'))
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function startRetry(): void {
  if (total.value === 0) {
    showToast('错题本为空')
    return
  }
  void router.push({ name: 'retry' })
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <p class="eyebrow">
        Review
      </p>
      <h1>错题本</h1>
      <p class="desc">
        只记录阶段练习中答错的题目；重练答对后自动移出。
      </p>
    </header>

    <div class="toolbar">
      <span class="muted">共 {{ total }} 道</span>
      <Button
        size="small"
        round
        type="primary"
        :disabled="total === 0"
        @click="startRetry"
      >
        错题重练
      </Button>
    </div>

    <p
      v-if="loading"
      class="muted"
    >
      加载中…
    </p>

    <div
      v-else-if="errorText"
      class="card"
    >
      <p class="muted">
        {{ errorText }}
      </p>
      <Button
        block
        round
        type="primary"
        class="mt"
        @click="load"
      >
        重新加载
      </Button>
    </div>

    <Empty
      v-else-if="isEmpty"
      description="错题本是空的，去做一次阶段练习吧"
    />

    <template v-else>
      <article
        v-for="item in items"
        :key="item.questionId"
        class="card wrong-card"
        @click="openDetail(item.questionId)"
      >
        <div class="wrong-head">
          <span class="q-type">{{ QUESTION_TYPE_LABELS[item.type] }}</span>
          <span class="muted">{{ item.stageName }}</span>
        </div>
        <p class="q-content">
          {{ item.contentPreview }}
        </p>
        <p class="muted">
          最近答错 {{ item.lastWrongAt.slice(0, 10) }}
        </p>
      </article>

      <Button
        v-if="hasNext"
        block
        round
        plain
        type="primary"
        :loading="loadingMore"
        class="mt"
        @click="loadMore"
      >
        加载更多
      </Button>
    </template>

    <Popup
      v-model:show="detailVisible"
      position="bottom"
      round
      closeable
      :style="{ maxHeight: '82%' }"
    >
      <div class="detail">
        <p
          v-if="detailLoading"
          class="muted"
        >
          加载中…
        </p>
        <template v-else-if="detail">
          <p class="q-type">
            {{ QUESTION_TYPE_LABELS[detail.question.type] }}
          </p>
          <h2 class="detail-title">
            {{ detail.question.content }}
          </h2>

          <div
            v-for="option in detail.question.options"
            :key="option.optionId"
            class="detail-option"
            :class="{ correct: option.isCorrect }"
          >
            <span class="option-key">{{ option.optionKey }}</span>
            <span>{{ option.content }}</span>
            <span
              v-if="option.isCorrect"
              class="option-flag"
            >正确答案</span>
          </div>

          <p
            v-if="detail.myLastAnswer.answerText"
            class="muted"
          >
            我的回答：{{ detail.myLastAnswer.answerText }}
          </p>

          <p
            v-if="detail.referenceAnswer"
            class="muted"
          >
            参考答案：{{ detail.referenceAnswer }}
          </p>

          <template v-if="detail.scoringPoints.length > 0">
            <p class="detail-subtitle">
              评分点
            </p>
            <ul class="points">
              <li
                v-for="point in detail.scoringPoints"
                :key="point"
              >
                {{ point }}
              </li>
            </ul>
          </template>

          <p class="detail-subtitle">
            解析
          </p>
          <p class="muted">
            {{ detail.analysis }}
          </p>
        </template>
      </div>
    </Popup>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.wrong-card + .wrong-card {
  margin-top: 12px;
}

.wrong-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.q-type {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  background: #eef0ff;
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
}

.q-content {
  margin: 0 0 8px;
  font-size: 15px;
  line-height: 1.65;
  color: var(--ink);
}

.detail {
  padding: 22px 18px 28px;
}

.detail-title {
  margin: 8px 0 14px;
  font-size: 17px;
  line-height: 1.6;
  color: var(--ink);
}

.detail-option {
  display: flex;
  gap: 8px;
  align-items: baseline;
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  font-size: 14px;
  line-height: 1.6;
}

.detail-option.correct {
  border-color: #22a06b;
  background: #f2fbf6;
}

.option-key {
  font-weight: 700;
  color: var(--ink-2);
}

.option-flag {
  margin-left: auto;
  font-size: 12px;
  color: #22a06b;
}

.detail-subtitle {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink-2);
}

.points {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--muted);
}

.mt {
  margin-top: 14px;
}
</style>
