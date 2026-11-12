<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, showToast } from 'vant'
import { ACTIVITY_LABELS, homeApi, type HomeResponse } from '@/api/home'
import { messageApi } from '@/api/message'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const session = useSessionStore()

const loading = ref(true)
const error = ref('')
const home = ref<HomeResponse | null>(null)
const unreadCount = ref(0)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) {
    return '夜深了'
  }
  if (hour < 12) {
    return '早上好'
  }
  if (hour < 18) {
    return '下午好'
  }
  return '晚上好'
})

const currentPath = computed(() => home.value?.currentPath ?? null)
const continueLearning = computed(() => home.value?.continueLearning ?? null)
const practice = computed(() => home.value?.practice ?? null)
const interview = computed(() => home.value?.interview ?? null)
const activities = computed(() => home.value?.recentActivities ?? [])
const notices = computed(() => home.value?.notices ?? [])
const degraded = computed(() => home.value?.meta.degradedSections ?? [])
const hasPath = computed(() => Boolean(home.value?.hasPath))

const dataAsOfText = computed(() => {
  const value = home.value?.meta.dataAsOf
  return value ? formatTime(value) : ''
})

/** 后端时间是 UTC RFC3339，展示时转本地 timezone */
function formatTime(value: string | null): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.slice(0, 16).replace('T', ' ')
  }
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 公告正文默认折叠两行，超过阈值才给出展开入口 */
const expandedNotices = ref<number[]>([])

function isExpanded(noticeId: number): boolean {
  return expandedNotices.value.includes(noticeId)
}

function toggleNotice(noticeId: number): void {
  expandedNotices.value = isExpanded(noticeId)
    ? expandedNotices.value.filter((id) => id !== noticeId)
    : [...expandedNotices.value, noticeId]
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    home.value = await homeApi.get()
    // 首页是聚合数据，顺手刷新一次资料，保证顶栏昵称/头像与个人中心一致
    if (!session.profile) {
      void session.loadProfile()
    }
  } catch (caught) {
    error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
  // 未读数独立拉取：即使首页聚合降级，消息入口仍然可用
  void loadUnread()
}

async function loadUnread(): Promise<void> {
  try {
    const result = await messageApi.unreadCount()
    unreadCount.value = result.unreadCount
  } catch {
    unreadCount.value = 0
  }
}

function goPractice(): void {
  const section = practice.value
  if (!section) {
    return
  }
  if (section.inProgressPracticeId) {
    showToast('你有未完成的练习，正在回到该练习')
    void router.push({ name: 'quiz', params: { stageId: String(section.stageId) } })
    return
  }
  void router.push({ name: 'quiz', params: { stageId: String(section.stageId) } })
}

function goReport(): void {
  const report = interview.value
  if (!report?.lastReportId) {
    return
  }
  void router.push({ name: 'records' })
}

onMounted(load)
</script>

<template>
  <div class="home-page">
    <div class="page">
      <header class="page-head">
        <div>
          <p class="eyebrow">
            {{ greeting }}
          </p>
          <h1>{{ session.nickname }}</h1>
          <p class="desc">
            每天一点进步，今天的坚持会变成明天的底气。
          </p>
        </div>
        <button
          type="button"
          class="bell"
          aria-label="消息中心"
          @click="router.push({ name: 'messages' })"
        >
          <span class="bell-icon">🔔</span>
          <span
            v-if="unreadCount > 0"
            class="badge"
          >{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
        </button>
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
          class="gap-top"
          @click="load"
        >
          重新加载
        </Button>
      </div>

      <template v-else>
        <p
          v-if="degraded.length"
          class="degraded"
        >
          部分数据暂未取到（{{ degraded.map((item) => item.section).join('、') }}），稍后下拉刷新试试。
        </p>

        <section
          v-if="notices.length"
          class="card notice-card"
        >
          <h2 class="card-title">
            公告
          </h2>
          <ul class="notice-list">
            <li
              v-for="item in notices"
              :key="item.noticeId"
              class="notice"
            >
              <div class="notice-head">
                <span class="notice-title">{{ item.title }}</span>
                <span
                  v-if="item.pinned"
                  class="notice-pin"
                >置顶</span>
              </div>
              <p
                class="notice-content"
                :class="{ 'is-clamped': !isExpanded(item.noticeId) }"
              >
                {{ item.content }}
              </p>
              <button
                v-if="item.content.length > 40"
                type="button"
                class="notice-toggle"
                @click="toggleNotice(item.noticeId)"
              >
                {{ isExpanded(item.noticeId) ? '收起' : '展开' }}
              </button>
              <span class="notice-time">{{ formatTime(item.publishedAt) }}</span>
            </li>
          </ul>
        </section>

        <div
          v-if="!hasPath"
          class="card"
        >
          <h2 class="card-title">
            还没有主学习路线
          </h2>
          <p class="muted">
            选一条路线后，首页会显示学习进度、继续学习、随堂练习与模拟面试入口。
          </p>
          <Button
            block
            round
            type="primary"
            class="gap-top"
            @click="router.push({ name: 'routes' })"
          >
            去选路线
          </Button>
        </div>

        <template v-else>
          <section
            v-if="currentPath"
            class="card path-card"
          >
            <div class="path-head">
              <div>
                <h2 class="card-title">
                  {{ currentPath.pathName }}
                </h2>
                <p class="muted">
                  {{ currentPath.subjectName }} ·
                  已完成 {{ currentPath.completedStageCount }}/{{ currentPath.stageCount }} 个阶段
                </p>
              </div>
              <span class="rate">{{ currentPath.progressRate }}%</span>
            </div>
            <div class="bar">
              <span
                class="bar-fill"
                :style="{ width: `${Math.min(100, Math.max(0, currentPath.progressRate))}%` }"
              />
            </div>
            <p class="muted mastery">
              掌握度
              <template v-if="currentPath.hasPracticeSample">
                {{ currentPath.masteryRate }}%
              </template>
              <template v-else>
                暂无练习样本
              </template>
            </p>
          </section>

          <section
            v-if="continueLearning"
            class="card"
          >
            <h2 class="card-title">
              继续学习
            </h2>
            <p class="course">
              {{ continueLearning.courseName }}
            </p>
            <p class="muted">
              {{ continueLearning.stageName }} ·
              {{ continueLearning.progressStatus === 'IN_PROGRESS' ? '上次未学完' : '还没开始' }}
            </p>
            <Button
              block
              round
              type="primary"
              class="gap-top"
              @click="router.push({ name: 'stage', params: { stageId: String(continueLearning.stageId) } })"
            >
              {{ continueLearning.progressStatus === 'IN_PROGRESS' ? '继续学习' : '开始学习' }}
            </Button>
          </section>

          <div class="entry-grid">
            <section class="card entry">
              <h2 class="card-title">
                随堂练习
              </h2>
              <p class="muted">
                <template v-if="practice">
                  {{ practice.stageName }} ·
                  {{ practice.lastFinishedAt ? `上次 ${formatTime(practice.lastFinishedAt)}` : '还没练过' }}
                </template>
                <template v-else>
                  暂无可练习阶段
                </template>
              </p>
              <Button
                block
                round
                plain
                type="primary"
                class="gap-top"
                :disabled="!practice?.canStart"
                @click="goPractice"
              >
                {{ practice?.inProgressPracticeId ? '继续练习' : '开始练习' }}
              </Button>
            </section>

            <section class="card entry">
              <h2 class="card-title">
                AI 模拟面试
              </h2>
              <p class="muted">
                <template v-if="interview">
                  今日剩余 {{ interview.remaining }}/{{ interview.limit }} 次
                </template>
                <template v-else>
                  暂不可用
                </template>
              </p>
              <Button
                block
                round
                plain
                type="primary"
                class="gap-top"
                :disabled="!interview?.canStart"
                @click="router.push({ name: 'interview' })"
              >
                {{ interview?.canStart ? '去面试' : '今日已完成' }}
              </Button>
              <button
                v-if="interview?.lastReportId"
                type="button"
                class="link"
                @click="goReport"
              >
                查看最近报告
              </button>
            </section>
          </div>

          <h2 class="section-title">
            最近动态
          </h2>
          <p
            v-if="activities.length === 0"
            class="card muted"
          >
            还没有学习记录，从「继续学习」开始吧。
          </p>
          <ul
            v-else
            class="card activity-list"
          >
            <li
              v-for="item in activities"
              :key="`${item.type}-${item.occurredAt}`"
              class="activity"
            >
              <span class="activity-tag">{{ ACTIVITY_LABELS[item.type] }}</span>
              <span class="activity-title">{{ item.title }}</span>
              <span class="activity-time">{{ formatTime(item.occurredAt) }}</span>
            </li>
          </ul>

          <p
            v-if="dataAsOfText"
            class="asof"
          >
            数据更新于 {{ dataAsOfText }}（{{ home?.meta.cacheTtlSeconds }} 秒缓存）
          </p>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--bg);
}

.gap-top {
  margin-top: 14px;
}

.page-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.bell {
  position: relative;
  flex: none;
  padding: 6px 8px;
  border: none;
  border-radius: var(--r-sm);
  background: var(--line-2);
  line-height: 1;
}

.bell-icon {
  font-size: 18px;
}

.badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #f5222d;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}

.degraded {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: #fff5e6;
  color: #d9822b;
  font-size: 12px;
  line-height: 1.6;
}

.path-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.rate {
  flex: none;
  font-size: 20px;
  font-weight: 700;
  color: var(--brand);
}

.bar {
  height: 8px;
  margin: 14px 0 10px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--line-2);
}

.bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--brand);
  transition: width 0.3s ease;
}

.mastery {
  font-size: 12px;
}

.course {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}

.entry-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: 1fr 1fr;
  margin-top: 12px;
}

.entry .muted {
  font-size: 12px;
}

.link {
  display: block;
  width: 100%;
  margin-top: 8px;
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--brand);
  text-align: center;
}

.section-title {
  margin: 22px 4px 12px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-2);
}

.notice-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.notice + .notice {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.notice-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.notice-title {
  flex: 1;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.notice-pin {
  flex: none;
  padding: 1px 6px;
  border-radius: 5px;
  background: #fff1f0;
  color: #d4380d;
  font-size: 11px;
  font-weight: 600;
}

.notice-content {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink-2);
  white-space: pre-wrap;
}

.notice-content.is-clamped {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.notice-toggle {
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--brand);
}

.notice-time {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
}

.activity-list {
  margin: 0;
  padding: 6px 18px;
  list-style: none;
}

.activity {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 0;
  font-size: 13px;
  color: var(--ink-2);
}

.activity + .activity {
  border-top: 1px solid var(--line);
}

.activity-tag {
  flex: none;
  padding: 1px 6px;
  border-radius: 5px;
  background: var(--line-2);
  color: var(--brand);
  font-size: 11px;
  font-weight: 600;
}

.activity-title {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.activity-time {
  flex: none;
  font-size: 11px;
  color: var(--muted);
}

.asof {
  margin: 14px 0 0;
  font-size: 11px;
  color: var(--muted);
  text-align: center;
}
</style>
