<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button, NavBar, showConfirmDialog, showToast } from 'vant'
import { MESSAGE_TYPE_LABELS, messageApi, type MessageItem } from '@/api/message'

const router = useRouter()

const loading = ref(true)
const error = ref('')
const items = ref<MessageItem[]>([])
const unreadCount = ref(0)
const actionId = ref<number | null>(null)

const hasUnread = computed(() => unreadCount.value > 0)

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

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [page, unread] = await Promise.all([
      messageApi.list({ page: 1, size: 30 }),
      messageApi.unreadCount(),
    ])
    items.value = page.items
    unreadCount.value = unread.unreadCount
  } catch (caught) {
    error.value = caught instanceof Error && caught.message ? caught.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function readOne(item: MessageItem): Promise<void> {
  if (item.readAt || actionId.value !== null) {
    return
  }
  actionId.value = item.messageId
  try {
    const updated = await messageApi.read(item.messageId)
    const target = items.value.find((i) => i.messageId === item.messageId)
    if (target) {
      target.readAt = updated.readAt
    }
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '标记已读失败')
  } finally {
    actionId.value = null
  }
}

async function readAll(): Promise<void> {
  if (unreadCount.value === 0) {
    return
  }
  try {
    const result = await messageApi.readAll()
    items.value = items.value.map((item) => ({
      ...item,
      readAt: item.readAt ?? new Date().toISOString(),
    }))
    unreadCount.value = 0
    showToast(`已标记 ${result.updated} 条为已读`)
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '操作失败')
  }
}

async function remove(item: MessageItem): Promise<void> {
  try {
    await showConfirmDialog({
      title: '删除消息',
      message: `删除后你将看不到「${item.title}」，确定删除？`,
    })
  } catch {
    return
  }
  actionId.value = item.messageId
  try {
    await messageApi.remove(item.messageId)
    items.value = items.value.filter((i) => i.messageId !== item.messageId)
    showToast('已删除')
  } catch (caught) {
    showToast(caught instanceof Error && caught.message ? caught.message : '删除失败')
  } finally {
    actionId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="messages-page">
    <NavBar
      title="消息中心"
      left-arrow
      fixed
      placeholder
      @click-left="router.back()"
    />
    <div class="page">
      <header class="page-head">
        <h1>消息中心</h1>
        <button
          v-if="hasUnread"
          type="button"
          class="read-all"
          @click="readAll"
        >
          全部已读
        </button>
      </header>

      <p
        v-if="loading"
        class="muted"
      >
        加载中…
      </p>

      <template v-else-if="!error">
        <p class="unread-summary">
          <template v-if="hasUnread">
            有 {{ unreadCount }} 条未读消息
          </template>
          <template v-else>
            没有未读消息
          </template>
        </p>

        <p
          v-if="items.length === 0"
          class="card muted"
        >
          还没有收到消息。
        </p>

        <article
          v-for="item in items"
          v-else
          :key="item.messageId"
          class="card message-card"
          :class="{ 'is-unread': !item.readAt }"
          @click="readOne(item)"
        >
          <div class="message-head">
            <span class="message-title">{{ item.title }}</span>
            <span
              v-if="!item.readAt"
              class="dot"
            />
            <span class="tag">{{ MESSAGE_TYPE_LABELS[item.type] }}</span>
          </div>
          <p class="message-content">
            {{ item.content }}
          </p>
          <div class="message-foot">
            <span class="muted">{{ formatTime(item.sentAt) }}</span>
            <button
              type="button"
              class="link"
              :disabled="actionId === item.messageId"
              @click.stop="remove(item)"
            >
              删除
            </button>
          </div>
        </article>
      </template>

      <div
        v-if="error"
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
    </div>
  </div>
</template>

<style scoped>
.messages-page {
  min-height: 100vh;
  background: var(--bg);
}

.page-head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
}

.read-all {
  flex: none;
  padding: 0;
  border: none;
  background: none;
  font-size: 13px;
  color: var(--brand);
}

.unread-summary {
  margin: 0 4px 12px;
  font-size: 12px;
  color: var(--muted);
}

.message-card + .message-card {
  margin-top: 10px;
}

.message-card.is-unread {
  border-left: 3px solid var(--brand);
}

.message-head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.message-title {
  flex: 1;
  overflow: hidden;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand);
}

.tag {
  flex: none;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--line-2);
  color: var(--brand);
  font-size: 11px;
  font-weight: 600;
}

.message-content {
  margin: 8px 0 10px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink-2);
  white-space: pre-wrap;
}

.message-foot {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
}

.link {
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--brand);
}

.retry {
  margin-top: 12px;
}
</style>
