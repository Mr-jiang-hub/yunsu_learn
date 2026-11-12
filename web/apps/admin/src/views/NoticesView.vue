<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  contentApi,
  NOTICE_STATUS_LABELS,
  type NoticeItem,
  type NoticeStatus,
} from '@/api/content'

const loading = ref(false)
const items = ref<NoticeItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '' as NoticeStatus | '',
})

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  title: '',
  content: '',
  pinned: false,
  sortNo: 0,
  expireAt: '' as string,
})

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/** 后端返回 RFC 3339 UTC，列表里去掉 T/Z 只显示到分钟 */
function displayTime(value: string | null): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').replace('Z', '').slice(0, 16)
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const result = await contentApi.notices.list({
      page: query.page,
      size: query.size,
      keyword: query.keyword,
      status: query.status,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载公告失败'))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.page = 1
  void fetchList()
}

function openCreate(): void {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.pinned = false
  form.sortNo = 0
  form.expireAt = ''
  dialogVisible.value = true
}

function openEdit(row: NoticeItem): void {
  editingId.value = row.id
  form.title = row.title
  form.content = row.content
  form.pinned = row.pinned
  form.sortNo = row.sortNo
  form.expireAt = row.expireAt ?? ''
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请填写公告标题')
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      pinned: form.pinned,
      sortNo: form.sortNo,
      expireAt: form.expireAt || null,
      // 编辑时清空失效时间需要显式声明，否则后端视为"不修改"
      clearExpire: editingId.value !== null && !form.expireAt,
    }
    if (editingId.value === null) {
      await contentApi.notices.create(payload)
      ElMessage.success('公告已创建（草稿）')
    } else {
      await contentApi.notices.update(editingId.value, payload)
      ElMessage.success('公告已更新')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

async function publish(row: NoticeItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `发布后 C 端首页会展示「${row.title}」（首页有 60 秒缓存）。确定发布？`,
      '发布公告',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await contentApi.notices.publish(row.id)
    ElMessage.success('已发布')
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '发布失败'))
  }
}

async function offline(row: NoticeItem): Promise<void> {
  try {
    await contentApi.notices.offline(row.id)
    ElMessage.success('已下线')
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '下线失败'))
  }
}

async function remove(row: NoticeItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除后不可恢复，确定删除草稿「${row.title}」？`,
      '删除公告',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await contentApi.notices.remove(row.id)
    ElMessage.success('已删除')
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '删除失败'))
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="按标题搜索"
        clearable
        style="width: 220px"
        @clear="onSearch"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="query.status"
        placeholder="全部状态"
        clearable
        style="width: 140px"
        @change="onSearch"
      >
        <el-option
          v-for="(label, value) in NOTICE_STATUS_LABELS"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-button
        type="primary"
        @click="onSearch"
      >
        查询
      </el-button>
      <div class="panel-toolbar-right">
        <el-button
          type="primary"
          @click="openCreate"
        >
          新增公告
        </el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="公告为全局内容，C 端首页展示「已发布且未过期」的前 3 条（置顶优先）；只有草稿可以删除，已发布请先下线。"
      class="panel-tip"
    />

    <el-table
      v-loading="loading"
      :data="items"
      stripe
      empty-text="暂无公告"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        prop="title"
        label="标题"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="content"
        label="正文"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column
        label="状态"
        width="90"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'PUBLISHED' ? 'success' : (row.status === 'OFFLINE' ? 'warning' : 'info')"
            size="small"
          >
            {{ NOTICE_STATUS_LABELS[row.status as NoticeStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="置顶"
        width="80"
      >
        <template #default="{ row }">
          <el-tag
            v-if="row.pinned"
            type="danger"
            size="small"
            effect="plain"
          >
            置顶
          </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column
        prop="sortNo"
        label="排序"
        width="70"
      />
      <el-table-column
        label="发布时间"
        width="150"
      >
        <template #default="{ row }">
          {{ displayTime(row.publishedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="失效时间"
        width="150"
      >
        <template #default="{ row }">
          {{ displayTime(row.expireAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="更新时间"
        width="150"
      >
        <template #default="{ row }">
          {{ displayTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openEdit(row as NoticeItem)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status !== 'PUBLISHED'"
            link
            type="success"
            @click="publish(row as NoticeItem)"
          >
            发布
          </el-button>
          <el-button
            v-else
            link
            type="warning"
            @click="offline(row as NoticeItem)"
          >
            下线
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status !== 'DRAFT'"
            @click="remove(row as NoticeItem)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="panel-pagination"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      :page-sizes="[10, 20, 50]"
      @current-change="(page: number) => { query.page = page; fetchList() }"
      @size-change="(size: number) => { query.size = size; query.page = 1; fetchList() }"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="editingId === null ? '新增公告' : '编辑公告'"
      width="560px"
    >
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input
            v-model="form.title"
            maxlength="100"
            show-word-limit
            placeholder="如：系统维护通知"
          />
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="纯文本，首页最多展示完整正文"
          />
        </el-form-item>
        <el-form-item label="置顶">
          <el-switch v-model="form.pinned" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number
            v-model="form.sortNo"
            :min="0"
          />
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            placeholder="留空表示长期有效"
            clearable
            value-format="YYYY-MM-DDTHH:mm:ssZ"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.panel-toolbar-right {
  margin-left: auto;
}

.panel-tip {
  margin-bottom: 16px;
}

.panel-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
