<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contentApi, type LearningPathItem } from '@/api/content'
import {
  ACTIVITY_TYPE_LABELS,
  STUDENT_STATUS_LABELS,
  studentApi,
  type ResetPasswordResult,
  type StudentDetail,
  type StudentListItem,
  type StudentStatus,
} from '@/api/students'

const loading = ref(false)
const items = ref<StudentListItem[]>([])
const total = ref(0)
const paths = ref<LearningPathItem[]>([])
const query = reactive({
  page: 1,
  size: 10,
  phone: '',
  nickname: '',
  pathId: undefined as number | undefined,
  status: '' as StudentStatus | '',
})
const dateRange = ref<[string, string] | null>(null)

/** 详情抽屉 */
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<StudentDetail | null>(null)
const pathSaving = ref(false)
const editingPathId = ref<number | undefined>(undefined)

/** 重置密码结果（明文只展示这一次） */
const resetVisible = ref(false)
const resetResult = ref<ResetPasswordResult | null>(null)

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function fetchPaths(): Promise<void> {
  try {
    const result = await contentApi.learningPaths.list({ page: 1, size: 100 })
    paths.value = result.items
  } catch (error) {
    ElMessage.error(errorText(error, '加载学习路线失败'))
  }
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const result = await studentApi.list({
      page: query.page,
      size: query.size,
      phone: query.phone.trim(),
      nickname: query.nickname.trim(),
      pathId: query.pathId,
      status: query.status,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载学员列表失败'))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  query.page = 1
  void fetchList()
}

function onReset(): void {
  query.phone = ''
  query.nickname = ''
  query.pathId = undefined
  query.status = ''
  dateRange.value = null
  onSearch()
}

function statusTagType(status: StudentStatus): 'success' | 'info' | 'danger' {
  if (status === 'ACTIVE') {
    return 'success'
  }
  return status === 'DISABLED' ? 'info' : 'danger'
}

async function openDetail(row: StudentListItem): Promise<void> {
  drawerVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const data = await studentApi.detail(row.id)
    detail.value = data
    editingPathId.value = data.student.currentPath?.id
  } catch (error) {
    ElMessage.error(errorText(error, '加载学员详情失败'))
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function reloadDetail(id: number): Promise<void> {
  try {
    const data = await studentApi.detail(id)
    detail.value = data
    editingPathId.value = data.student.currentPath?.id
  } catch (error) {
    ElMessage.error(errorText(error, '刷新学员详情失败'))
  }
}

async function saveCurrentPath(): Promise<void> {
  if (!detail.value || editingPathId.value === undefined) {
    ElMessage.warning('请选择学习路线')
    return
  }
  pathSaving.value = true
  try {
    await studentApi.updateCurrentPath(detail.value.student.id, editingPathId.value)
    ElMessage.success('当前学习路线已更新')
    await reloadDetail(detail.value.student.id)
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '调整路线失败'))
  } finally {
    pathSaving.value = false
  }
}

async function resetPassword(row: StudentListItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `将生成一次性临时密码并强制「${row.nickname}」下次登录修改，同时注销其全部登录状态。继续？`,
      '重置密码',
      { type: 'warning' },
    )
    const result = await studentApi.resetPassword(row.id)
    resetResult.value = result
    resetVisible.value = true
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '重置密码失败'))
  }
}

async function copyPassword(): Promise<void> {
  if (!resetResult.value) {
    return
  }
  try {
    await navigator.clipboard.writeText(resetResult.value.temporaryPassword)
    ElMessage.success('临时密码已复制')
  } catch {
    ElMessage.warning('浏览器未授权剪贴板，请手动选中复制')
  }
}

async function toggleStatus(row: StudentListItem): Promise<void> {
  const nextDisabled = row.status === 'ACTIVE'
  try {
    if (nextDisabled) {
      await ElMessageBox.confirm(
        `停用后「${row.nickname}」无法登录，登录态会立即失效。确定停用？`,
        '停用学员',
        { type: 'warning' },
      )
      await studentApi.disable(row.id)
    } else {
      await studentApi.enable(row.id)
    }
    ElMessage.success(nextDisabled ? '已停用' : '已启用')
    await fetchList()
    if (detail.value && detail.value.student.id === row.id) {
      await reloadDetail(row.id)
    }
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '操作失败'))
  }
}

async function cancelStudent(row: StudentListItem): Promise<void> {
  try {
    const input = await ElMessageBox.prompt(
      '注销将释放手机号、清空昵称与头像，历史练习与面试记录脱敏保留，且不可恢复。请输入「注销」确认。',
      '注销学员',
      {
        type: 'warning',
        inputPlaceholder: '注销',
        inputValidator: (value: string) => (value === '注销' ? true : '请输入「注销」两字'),
      },
    )
    await studentApi.cancel(row.id, input.value === '注销' ? '' : '')
    ElMessage.success('学员已注销')
    await fetchList()
    if (detail.value && detail.value.student.id === row.id) {
      await reloadDetail(row.id)
    }
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '注销失败'))
  }
}

function masteryText(): string {
  const rate = detail.value?.progress.masteryRate
  if (rate === null || rate === undefined) {
    return '—'
  }
  const count = detail.value?.progress.masteryQuestionCount ?? 0
  return `${rate}%${count < 10 ? '（样本较少）' : ''}`
}

onMounted(() => {
  void fetchPaths()
  void fetchList()
})
</script>

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-input
        v-model="query.phone"
        placeholder="手机号（精确）"
        clearable
        style="width: 170px"
        @clear="onSearch"
        @keyup.enter="onSearch"
      />
      <el-input
        v-model="query.nickname"
        placeholder="昵称（模糊）"
        clearable
        style="width: 150px"
        @clear="onSearch"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="query.pathId"
        placeholder="全部路线"
        clearable
        style="width: 180px"
        @change="onSearch"
      >
        <el-option
          v-for="path in paths"
          :key="path.id"
          :label="path.name"
          :value="path.id"
        />
      </el-select>
      <el-select
        v-model="query.status"
        placeholder="全部状态"
        clearable
        style="width: 120px"
        @change="onSearch"
      >
        <el-option
          label="正常"
          value="ACTIVE"
        />
        <el-option
          label="已停用"
          value="DISABLED"
        />
        <el-option
          label="已注销"
          value="CANCELLED"
        />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="注册开始"
        end-placeholder="注册结束"
        style="width: 240px"
        @change="onSearch"
      />
      <el-button
        type="primary"
        @click="onSearch"
      >
        查询
      </el-button>
      <el-button @click="onReset">
        重置
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="items"
      stripe
      empty-text="暂无学员"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        prop="nickname"
        label="昵称"
        min-width="140"
      />
      <el-table-column
        label="手机号"
        width="140"
      >
        <template #default="{ row }">
          {{ row.phoneMasked || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="当前路线"
        min-width="160"
      >
        <template #default="{ row }">
          {{ row.currentPathName || '未选择' }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="90"
      >
        <template #default="{ row }">
          <el-tag
            :type="statusTagType(row.status as StudentStatus)"
            size="small"
          >
            {{ STUDENT_STATUS_LABELS[row.status as StudentStatus] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        label="注册时间"
        width="180"
      />
      <el-table-column
        label="最近登录"
        width="180"
      >
        <template #default="{ row }">
          {{ row.lastLoginAt || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="250"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openDetail(row as StudentListItem)"
          >
            详情
          </el-button>
          <el-button
            link
            :disabled="row.status === 'CANCELLED'"
            @click="resetPassword(row as StudentListItem)"
          >
            重置密码
          </el-button>
          <el-button
            link
            :disabled="row.status === 'CANCELLED'"
            :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
            @click="toggleStatus(row as StudentListItem)"
          >
            {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status === 'CANCELLED'"
            @click="cancelStudent(row as StudentListItem)"
          >
            注销
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

    <el-drawer
      v-model="drawerVisible"
      :title="detail ? `学员详情 · ${detail.student.nickname}` : '学员详情'"
      size="680px"
    >
      <div
        v-loading="detailLoading"
        class="detail-body"
      >
        <template v-if="detail">
          <el-descriptions
            :column="2"
            border
            size="small"
          >
            <el-descriptions-item label="ID">
              {{ detail.student.id }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag
                :type="statusTagType(detail.student.status)"
                size="small"
              >
                {{ STUDENT_STATUS_LABELS[detail.student.status] }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="手机号">
              {{ detail.student.phoneMasked || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间">
              {{ detail.student.createdAt || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="最近登录">
              {{ detail.student.lastLoginAt || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="最近活跃">
              {{ detail.progress.lastActiveAt || '—' }}
            </el-descriptions-item>
            <el-descriptions-item
              label="注销时间"
              :span="2"
            >
              {{ detail.student.cancelledAt || '—' }}
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="section-title">
            当前学习路线
          </h4>
          <div class="path-row">
            <el-select
              v-model="editingPathId"
              placeholder="选择路线"
              :disabled="detail.student.status === 'CANCELLED'"
              style="width: 300px"
            >
              <el-option
                v-for="path in paths"
                :key="path.id"
                :label="path.name"
                :value="path.id"
              />
            </el-select>
            <el-button
              type="primary"
              :loading="pathSaving"
              :disabled="detail.student.status === 'CANCELLED'"
              @click="saveCurrentPath"
            >
              保存
            </el-button>
            <span class="path-hint">切换路线不删除历史进度</span>
          </div>

          <h4 class="section-title">
            学习进度
          </h4>
          <el-descriptions
            :column="3"
            border
            size="small"
          >
            <el-descriptions-item label="完成课程">
              {{ detail.progress.completedCourses }} / {{ detail.progress.courseCount }}
            </el-descriptions-item>
            <el-descriptions-item label="掌握度">
              {{ masteryText() }}
            </el-descriptions-item>
            <el-descriptions-item label="面试次数">
              {{ detail.interviewSummary.completed }} / {{ detail.interviewSummary.total }}
            </el-descriptions-item>
          </el-descriptions>

          <h4 class="section-title">
            最近动态
          </h4>
          <el-table
            :data="detail.recentActivities"
            size="small"
            empty-text="暂无动态"
          >
            <el-table-column
              label="类型"
              width="80"
            >
              <template #default="{ row }">
                {{ ACTIVITY_TYPE_LABELS[row.type as string] ?? row.type }}
              </template>
            </el-table-column>
            <el-table-column
              prop="title"
              label="对象"
              min-width="180"
              show-overflow-tooltip
            />
            <el-table-column
              prop="detail"
              label="结果"
              min-width="120"
            />
            <el-table-column
              prop="at"
              label="时间"
              width="180"
            />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog
      v-model="resetVisible"
      title="临时密码已生成"
      width="440px"
    >
      <p class="reset-line">
        请把下面的临时密码交给学员，该密码只显示这一次：
      </p>
      <div class="reset-password">
        {{ resetResult?.temporaryPassword }}
      </div>
      <p class="reset-line">
        学员下次登录必须修改密码；其已有登录状态已全部失效。
      </p>
      <template #footer>
        <el-button @click="resetVisible = false">
          关闭
        </el-button>
        <el-button
          type="primary"
          @click="copyPassword"
        >
          复制密码
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.panel-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.detail-body {
  min-height: 200px;
}

.section-title {
  margin: 20px 0 10px;
  font-size: 14px;
  font-weight: 600;
}

.path-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.path-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.reset-line {
  margin: 0 0 10px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.reset-password {
  margin-bottom: 12px;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-family: 'Cascadia Mono', Consolas, monospace;
  font-size: 18px;
  letter-spacing: 1px;
  text-align: center;
  user-select: all;
}
</style>
