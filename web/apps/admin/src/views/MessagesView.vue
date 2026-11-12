<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  contentApi,
  MESSAGE_AUDIENCE_LABELS,
  MESSAGE_STATUS_LABELS,
  MESSAGE_TYPE_LABELS,
  type MessageAudience,
  type MessageItem,
  type MessageStatus,
  type MessageType,
} from '@/api/content'
import { studentApi } from '@/api/students'

interface StudentOption {
  id: number
  label: string
}

const loading = ref(false)
const items = ref<MessageItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '' as MessageStatus | '',
})

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  title: '',
  content: '',
  type: 'SYSTEM' as MessageType,
  audience: 'ALL' as MessageAudience,
  studentIds: [] as number[],
})

// 发送弹窗：定向消息必须在这里确认接收人
const sendDialogVisible = ref(false)
const sending = ref(false)
const sendTargetId = ref<number | null>(null)
const sendTargetTitle = ref('')
const sendStudentIds = ref<number[]>([])

const studentOptions = ref<StudentOption[]>([])
const studentLoading = ref(false)

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function displayTime(value: string | null): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').replace('Z', '').slice(0, 16)
}

async function searchStudents(keyword: string): Promise<void> {
  studentLoading.value = true
  try {
    const result = await studentApi.list({
      page: 1,
      size: 20,
      nickname: keyword.trim(),
      status: 'ACTIVE',
    })
    studentOptions.value = result.items.map((item) => ({
      id: item.id,
      label: `${item.nickname || '未设置昵称'} · ID ${item.id}${item.phoneMasked ? ' · ' + item.phoneMasked : ''}`,
    }))
  } catch {
    studentOptions.value = []
  } finally {
    studentLoading.value = false
  }
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const result = await contentApi.messages.list({
      page: query.page,
      size: query.size,
      keyword: query.keyword,
      status: query.status,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载消息失败'))
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
  form.type = 'SYSTEM'
  form.audience = 'ALL'
  form.studentIds = []
  studentOptions.value = []
  dialogVisible.value = true
}

function openEdit(row: MessageItem): void {
  editingId.value = row.id
  form.title = row.title
  form.content = row.content
  form.type = row.type
  form.audience = row.audience
  form.studentIds = []
  studentOptions.value = []
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请填写消息标题')
    return
  }
  if (form.audience === 'SPECIFIED' && form.studentIds.length === 0) {
    ElMessage.warning('定向消息请至少选择一名接收学员')
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      content: form.content.trim(),
      type: form.type,
      audience: form.audience,
      studentIds: form.audience === 'SPECIFIED' ? form.studentIds : undefined,
    }
    if (editingId.value === null) {
      await contentApi.messages.create(payload)
      ElMessage.success('消息已创建（草稿）')
    } else {
      await contentApi.messages.update(editingId.value, payload)
      ElMessage.success('消息已更新')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

function openSend(row: MessageItem): void {
  sendTargetId.value = row.id
  sendTargetTitle.value = row.title
  sendStudentIds.value = []
  studentOptions.value = []
  // 定向消息：发送前必须确认名单；全体消息直接二次确认即可
  if (row.audience === 'SPECIFIED') {
    sendDialogVisible.value = true
    void searchStudents('')
    return
  }
  void confirmSend()
}

async function confirmSend(): Promise<void> {
  const id = sendTargetId.value
  if (id === null) {
    return
  }
  if (sendDialogVisible.value && sendStudentIds.value.length === 0) {
    ElMessage.warning('请至少选择一名接收学员')
    return
  }
  const ids = sendDialogVisible.value ? sendStudentIds.value : undefined
  if (!sendDialogVisible.value) {
    try {
      await ElMessageBox.confirm(
        `发送后学员在消息中心即可看到「${sendTargetTitle.value}」，且不可再编辑、不可删除，只能撤回。确定发送？`,
        '发送消息',
        { type: 'warning' },
      )
    } catch {
      return
    }
  }
  sending.value = true
  try {
    const result = await contentApi.messages.send(id, ids)
    ElMessage.success(`已发送，接收 ${result.targetCount} 人`)
    sendDialogVisible.value = false
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '发送失败'))
  } finally {
    sending.value = false
  }
}

function closeSend(): void {
  sendDialogVisible.value = false
}

async function revoke(row: MessageItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `撤回后学员立即看不到「${row.title}」，且不可重发。确定撤回？`,
      '撤回消息',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await contentApi.messages.revoke(row.id)
    ElMessage.success('已撤回')
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '撤回失败'))
  }
}

async function remove(row: MessageItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除后不可恢复，确定删除草稿「${row.title}」？`,
      '删除消息',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await contentApi.messages.remove(row.id)
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
          v-for="(label, value) in MESSAGE_STATUS_LABELS"
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
          新增消息
        </el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="全体消息采用懒投递：发送只记录当时人数，学员打开消息中心时补齐，注册之前发的消息不会推给新学员。已发送不可编辑、不可删除，只能撤回。"
      class="panel-tip"
    />

    <el-table
      v-loading="loading"
      :data="items"
      stripe
      empty-text="暂无消息"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        prop="title"
        label="标题"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="content"
        label="正文"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        label="类型"
        width="100"
      >
        <template #default="{ row }">
          {{ MESSAGE_TYPE_LABELS[row.type as MessageType] }}
        </template>
      </el-table-column>
      <el-table-column
        label="受众"
        width="100"
      >
        <template #default="{ row }">
          {{ MESSAGE_AUDIENCE_LABELS[row.audience as MessageAudience] }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="90"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'SENT' ? 'success' : (row.status === 'REVOKED' ? 'danger' : 'info')"
            size="small"
          >
            {{ MESSAGE_STATUS_LABELS[row.status as MessageStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="接收"
        width="80"
      >
        <template #default="{ row }">
          {{ row.targetCount }}
        </template>
      </el-table-column>
      <el-table-column
        label="已读"
        width="80"
      >
        <template #default="{ row }">
          {{ row.readCount }}
        </template>
      </el-table-column>
      <el-table-column
        label="发送时间"
        width="150"
      >
        <template #default="{ row }">
          {{ displayTime(row.sentAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="240"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.status !== 'DRAFT'"
            @click="openEdit(row as MessageItem)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            link
            type="success"
            @click="openSend(row as MessageItem)"
          >
            发送
          </el-button>
          <el-button
            v-else-if="row.status === 'SENT'"
            link
            type="warning"
            @click="revoke(row as MessageItem)"
          >
            撤回
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status !== 'DRAFT'"
            @click="remove(row as MessageItem)"
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
      :title="editingId === null ? '新增消息' : '编辑消息'"
      width="600px"
    >
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input
            v-model="form.title"
            maxlength="100"
            show-word-limit
            placeholder="如：本周学习提醒"
          />
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="纯文本，消息中心展示完整正文"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio
              v-for="(label, value) in MESSAGE_TYPE_LABELS"
              :key="value"
              :value="value"
            >
              {{ label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="受众">
          <el-radio-group v-model="form.audience">
            <el-radio value="ALL">
              全体学员
            </el-radio>
            <el-radio value="SPECIFIED">
              指定学员
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="form.audience === 'SPECIFIED'"
          label="接收学员"
        >
          <el-select
            v-model="form.studentIds"
            multiple
            filterable
            remote
            reserve-keyword
            clearable
            style="width: 100%"
            placeholder="按昵称搜索学员，最多 200 人"
            :remote-method="searchStudents"
            :loading="studentLoading"
          >
            <el-option
              v-for="option in studentOptions"
              :key="option.id"
              :label="option.label"
              :value="option.id"
            />
          </el-select>
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

    <el-dialog
      v-model="sendDialogVisible"
      title="选择接收学员"
      width="520px"
      @close="closeSend"
    >
      <p class="send-tip">
        定向消息「{{ sendTargetTitle }}」发送后不可编辑、不可删除，只能撤回。
      </p>
      <el-select
        v-model="sendStudentIds"
        multiple
        filterable
        remote
        reserve-keyword
        clearable
        style="width: 100%"
        placeholder="按昵称搜索学员，最多 200 人"
        :remote-method="searchStudents"
        :loading="studentLoading"
      >
        <el-option
          v-for="option in studentOptions"
          :key="option.id"
          :label="option.label"
          :value="option.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="closeSend">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="sending"
          @click="confirmSend"
        >
          确定发送
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

.send-tip {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
</style>
