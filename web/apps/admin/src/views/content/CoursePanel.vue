<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contentApi, type ApiStatus, type CourseItem } from '@/api/content'
import { useContentOptions } from './useContentOptions'

const { subjects, stages, loadSubjects, loadStages } = useContentOptions()

const loading = ref(false)
const items = ref<CourseItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '' as ApiStatus | '',
  subjectId: undefined as number | undefined,
  stageId: undefined as number | undefined,
})

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const courseForm = ref({
  title: '',
  subjectId: undefined as number | undefined,
  stageId: undefined as number | undefined,
  bilibiliUrl: '',
  description: '',
  durationSeconds: 0,
  sortNo: 0,
})

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const result = await contentApi.courses.list({
      page: query.page,
      size: query.size,
      keyword: query.keyword,
      status: query.status,
      subjectId: query.subjectId,
      stageId: query.stageId,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载课程失败'))
  } finally {
    loading.value = false
  }
}

async function onQuerySubjectChange(subjectId?: number): Promise<void> {
  query.stageId = undefined
  await loadStages(subjectId)
  onSearch()
}

function onSearch(): void {
  query.page = 1
  void fetchList()
}

async function openCreate(): Promise<void> {
  editingId.value = null
  courseForm.value.title = ''
  courseForm.value.subjectId = query.subjectId
  courseForm.value.stageId = undefined
  courseForm.value.bilibiliUrl = ''
  courseForm.value.description = ''
  courseForm.value.durationSeconds = 0
  courseForm.value.sortNo = items.value.length
  await loadStages(courseForm.value.subjectId)
  dialogVisible.value = true
}

async function openEdit(row: CourseItem): Promise<void> {
  editingId.value = row.id
  courseForm.value.title = row.title
  courseForm.value.subjectId = row.subjectId
  courseForm.value.bilibiliUrl = row.bilibiliUrl
  courseForm.value.description = row.description ?? ''
  courseForm.value.durationSeconds = row.durationSeconds ?? 0
  courseForm.value.sortNo = row.sortNo
  await loadStages(row.subjectId)
  courseForm.value.stageId = row.stageId
  dialogVisible.value = true
}

async function onFormSubjectChange(subjectId?: number): Promise<void> {
  courseForm.value.stageId = undefined
  await loadStages(subjectId)
}

async function submit(): Promise<void> {
  if (!courseForm.value.title.trim()) {
    ElMessage.warning('请填写课程标题')
    return
  }
  if (!courseForm.value.subjectId || !courseForm.value.stageId) {
    ElMessage.warning('请选择学科与阶段')
    return
  }
  if (!courseForm.value.bilibiliUrl.trim()) {
    ElMessage.warning('请填写 B 站链接')
    return
  }
  submitting.value = true
  const payload = {
    title: courseForm.value.title.trim(),
    subjectId: courseForm.value.subjectId,
    stageId: courseForm.value.stageId,
    bilibiliUrl: courseForm.value.bilibiliUrl.trim(),
    description: courseForm.value.description.trim(),
    durationSeconds: courseForm.value.durationSeconds,
    sortNo: courseForm.value.sortNo,
  }
  try {
    if (editingId.value === null) {
      await contentApi.courses.create(payload)
      ElMessage.success('课程已创建')
    } else {
      await contentApi.courses.update(editingId.value, payload)
      ElMessage.success('课程已更新')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: CourseItem): Promise<void> {
  const nextDisabled = row.status === 'ENABLED'
  try {
    if (nextDisabled) {
      await ElMessageBox.confirm(
        `停用后学员不再看到「${row.title}」，已开始学习的学员仍可完成。确定停用？`,
        '停用课程',
        { type: 'warning' },
      )
      await contentApi.courses.disable(row.id)
    } else {
      await contentApi.courses.enable(row.id)
    }
    ElMessage.success(nextDisabled ? '已停用' : '已启用')
    await fetchList()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '操作失败'))
  }
}

function formatDuration(seconds: number | null): string {
  if (!seconds) {
    return '—'
  }
  const minutes = Math.round(seconds / 60)
  return minutes >= 60 ? `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分` : `${minutes} 分钟`
}

onMounted(async () => {
  await loadSubjects()
  await fetchList()
})
</script>

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-select
        v-model="query.subjectId"
        placeholder="全部学科"
        clearable
        style="width: 160px"
        @change="onQuerySubjectChange"
      >
        <el-option
          v-for="subject in subjects"
          :key="subject.id"
          :label="subject.name"
          :value="subject.id"
        />
      </el-select>
      <el-select
        v-model="query.stageId"
        placeholder="全部阶段"
        clearable
        :disabled="!query.subjectId"
        style="width: 160px"
        @change="onSearch"
      >
        <el-option
          v-for="stage in stages"
          :key="stage.id"
          :label="stage.name"
          :value="stage.id"
        />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="按标题搜索"
        clearable
        style="width: 200px"
        @clear="onSearch"
        @keyup.enter="onSearch"
      />
      <el-select
        v-model="query.status"
        placeholder="全部状态"
        clearable
        style="width: 130px"
        @change="onSearch"
      >
        <el-option
          label="启用"
          value="ENABLED"
        />
        <el-option
          label="停用"
          value="DISABLED"
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
          新增课程
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="items"
      stripe
      empty-text="暂无课程"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        prop="title"
        label="课程标题"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="subjectName"
        label="学科"
        width="120"
      />
      <el-table-column
        prop="stageName"
        label="阶段"
        width="140"
      />
      <el-table-column
        label="时长"
        width="120"
      >
        <template #default="{ row }">
          {{ formatDuration(row.durationSeconds) }}
        </template>
      </el-table-column>
      <el-table-column
        label="B 站链接"
        width="110"
      >
        <template #default="{ row }">
          <a
            :href="row.bilibiliUrl"
            target="_blank"
            rel="noreferrer"
          >打开</a>
        </template>
      </el-table-column>
      <el-table-column
        prop="sortNo"
        label="排序"
        width="70"
      />
      <el-table-column
        label="状态"
        width="90"
      >
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'ENABLED' ? 'success' : 'info'"
            size="small"
          >
            {{ row.status === 'ENABLED' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="150"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openEdit(row as CourseItem)"
          >
            编辑
          </el-button>
          <el-button
            link
            :type="row.status === 'ENABLED' ? 'danger' : 'success'"
            @click="toggleStatus(row as CourseItem)"
          >
            {{ row.status === 'ENABLED' ? '停用' : '启用' }}
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
      :title="editingId === null ? '新增课程' : '编辑课程'"
      width="560px"
    >
      <el-form label-width="96px">
        <el-form-item label="课程标题">
          <el-input
            v-model="courseForm.title"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="学科">
          <el-select
            v-model="courseForm.subjectId"
            placeholder="请选择学科"
            style="width: 100%"
            @change="onFormSubjectChange"
          >
            <el-option
              v-for="subject in subjects"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="阶段">
          <el-select
            v-model="courseForm.stageId"
            placeholder="请先选择学科"
            :disabled="!courseForm.subjectId"
            style="width: 100%"
          >
            <el-option
              v-for="stage in stages"
              :key="stage.id"
              :label="stage.name"
              :value="stage.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="B 站链接">
          <el-input
            :model-value="courseForm.bilibiliUrl"
            placeholder="https://www.bilibili.com/video/BV..."
            @update:model-value="(value: string) => { courseForm.bilibiliUrl = value }"
          />
        </el-form-item>
        <el-form-item label="课程简介">
          <el-input
            :model-value="courseForm.description"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            @update:model-value="(value: string) => { courseForm.description = value }"
          />
        </el-form-item>
        <el-form-item label="时长（秒）">
          <el-input-number
            :model-value="courseForm.durationSeconds"
            :min="0"
            :step="60"
            @update:model-value="(value: number | undefined) => { courseForm.durationSeconds = value ?? 0 }"
          />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number
            :model-value="courseForm.sortNo"
            :min="0"
            @update:model-value="(value: number | undefined) => { courseForm.sortNo = value ?? 0 }"
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
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.panel-toolbar-right {
  margin-left: auto;
}

.panel-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
