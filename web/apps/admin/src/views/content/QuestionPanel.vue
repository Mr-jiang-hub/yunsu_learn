<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  contentApi,
  QUESTION_TYPE_LABELS,
  type ApiStatus,
  type QuestionListItem,
  type QuestionPayload,
  type ImportTaskResult,
  type QuestionType,
} from '@/api/content'
import { useContentOptions } from './useContentOptions'

const { subjects, stages, loadSubjects, loadStages } = useContentOptions()

const loading = ref(false)
const items = ref<QuestionListItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '' as ApiStatus | '',
  subjectId: undefined as number | undefined,
  stageId: undefined as number | undefined,
  type: '' as QuestionType | '',
})

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  type: 'SINGLE_CHOICE' as QuestionType,
  subjectId: undefined as number | undefined,
  stageId: undefined as number | undefined,
  content: '',
  analysis: '',
  referenceAnswer: '',
  options: [] as { content: string; isCorrect: boolean }[],
  scoringPoints: [] as { pointText: string }[],
})

const isChoice = computed(() => form.type !== 'SHORT_ANSWER')
const isMulti = computed(() => form.type === 'MULTIPLE_CHOICE')

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function resetOptionsByType(): void {
  form.referenceAnswer = ''
  form.scoringPoints = []
  if (form.type === 'TRUE_FALSE') {
    form.options = [
      { content: '正确', isCorrect: true },
      { content: '错误', isCorrect: false },
    ]
  } else if (form.type === 'SHORT_ANSWER') {
    form.options = []
    form.scoringPoints = [{ pointText: '' }]
  } else {
    form.options = [
      { content: '', isCorrect: false },
      { content: '', isCorrect: false },
    ]
  }
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const result = await contentApi.questions.list({
      page: query.page,
      size: query.size,
      keyword: query.keyword,
      status: query.status,
      subjectId: query.subjectId,
      stageId: query.stageId,
      type: query.type,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载题目失败'))
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
  form.type = 'SINGLE_CHOICE'
  form.subjectId = query.subjectId
  form.stageId = query.stageId
  form.content = ''
  form.analysis = ''
  resetOptionsByType()
  await loadStages(form.subjectId)
  form.stageId = query.stageId ?? form.stageId
  dialogVisible.value = true
}

async function openEdit(row: QuestionListItem): Promise<void> {
  try {
    const detail = await contentApi.questions.detail(row.id)
    editingId.value = row.id
    form.type = detail.type
    form.subjectId = detail.subjectId
    form.stageId = detail.stageId
    form.content = detail.content
    form.analysis = detail.analysis ?? ''
    form.referenceAnswer = detail.referenceAnswer ?? ''
    form.options = detail.options.map(option => ({
      content: option.content,
      isCorrect: option.isCorrect,
    }))
    form.scoringPoints = detail.scoringPoints.map(point => ({ pointText: point.pointText }))
    await loadStages(detail.subjectId)
    form.stageId = detail.stageId
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(errorText(error, '加载题目详情失败'))
  }
}

async function onFormSubjectChange(subjectId?: number): Promise<void> {
  form.stageId = undefined
  await loadStages(subjectId)
}

function addOption(): void {
  if (form.options.length >= 8) {
    ElMessage.warning('最多 8 个选项')
    return
  }
  form.options.push({ content: '', isCorrect: false })
}

function removeOption(index: number): void {
  form.options.splice(index, 1)
}

function addScoringPoint(): void {
  form.scoringPoints.push({ pointText: '' })
}

function removeScoringPoint(index: number): void {
  form.scoringPoints.splice(index, 1)
}

async function submit(): Promise<void> {
  if (!form.subjectId || !form.stageId) {
    ElMessage.warning('请选择学科与阶段')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写题干')
    return
  }
  if (isChoice.value && form.options.some(option => !option.content.trim())) {
    ElMessage.warning('选项内容不能为空')
    return
  }
  if (form.type === 'SHORT_ANSWER' && form.scoringPoints.every(point => !point.pointText.trim())) {
    ElMessage.warning('简答题至少需要一条评分点')
    return
  }

  const payload: QuestionPayload = {
    type: form.type,
    subjectId: form.subjectId,
    stageId: form.stageId,
    content: form.content.trim(),
    analysis: form.analysis.trim(),
    options: isChoice.value
      ? form.options.map((option, index) => ({
          optionKey: String.fromCharCode(65 + index),
          content: option.content.trim(),
          isCorrect: option.isCorrect,
          sortNo: index,
        }))
      : [],
    scoringPoints:
      form.type === 'SHORT_ANSWER'
        ? form.scoringPoints
            .filter(point => point.pointText.trim())
            .map((point, index) => ({ pointText: point.pointText.trim(), sortNo: index }))
        : [],
  }
  if (form.type === 'SHORT_ANSWER' && form.referenceAnswer.trim()) {
    payload.referenceAnswer = form.referenceAnswer.trim()
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await contentApi.questions.create(payload)
      ElMessage.success('题目已创建')
    } else {
      await contentApi.questions.update(editingId.value, payload)
      ElMessage.success('题目已更新')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: QuestionListItem): Promise<void> {
  const nextDisabled = row.status === 'ENABLED'
  try {
    if (nextDisabled) {
      await ElMessageBox.confirm('停用后该题不再出题，历史作答保留。确定停用？', '停用题目', {
        type: 'warning',
      })
      await contentApi.questions.disable(row.id)
    } else {
      await contentApi.questions.enable(row.id)
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

// ------------------------------------------------------------------ 批量导入

const importVisible = ref(false)
const importing = ref(false)
const importFile = ref<File | null>(null)
const importTask = ref<ImportTaskResult | null>(null)
const importProgressText = ref('')

function resetImport(): void {
  importing.value = false
  importFile.value = null
  importTask.value = null
  importProgressText.value = ''
}

function openImport(): void {
  resetImport()
  importVisible.value = true
}

async function downloadTemplate(): Promise<void> {
  try {
    const blob = await contentApi.questions.importTemplate()
    const url = URL.createObjectURL(blob instanceof Blob ? blob : new Blob([blob as BlobPart]))
    const link = document.createElement('a')
    link.href = url
    link.download = 'question-import-template.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(errorText(error, '模板下载失败'))
  }
}

function onImportFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  input.value = ''
  if (!file) {
    return
  }
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('只支持 .xlsx 模板文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('文件不能超过 5MB')
    return
  }
  importFile.value = file
  importTask.value = null
  importProgressText.value = ''
}

/** 轮询任务：间隔 2 秒、最多 60 次（04 §6.10.4） */
async function startImport(): Promise<void> {
  if (!importFile.value || importing.value) {
    return
  }
  importing.value = true
  importTask.value = null
  try {
    const started = await contentApi.questions.importQuestions(importFile.value)
    importProgressText.value = '已提交，正在解析…'
    for (let i = 0; i < 60; i += 1) {
      await new Promise((resolve) => setTimeout(resolve, 2000))
      try {
        const result = await contentApi.questions.importStatus(started.taskId)
        importTask.value = result
        if (result.status === 'SUCCEEDED') {
          ElMessage.success(`导入成功：${result.successRows} 条`)
          await fetchList()
        } else {
          ElMessage.error('导入失败，整批未写入')
        }
        break
      } catch (error) {
        // 40022 仍在处理中：继续轮询；其他错误直接抛出
        if (!(error instanceof Error)) {
          throw error
        }
        continue
      }
    }
    if (!importTask.value) {
      importProgressText.value = '任务处理超时，请稍后在导入记录中确认结果'
    } else {
      importProgressText.value = ''
    }
  } catch (error) {
    ElMessage.error(errorText(error, '导入失败'))
    importProgressText.value = ''
  } finally {
    importing.value = false
  }
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
        style="width: 150px"
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
        style="width: 150px"
        @change="onSearch"
      >
        <el-option
          v-for="stage in stages"
          :key="stage.id"
          :label="stage.name"
          :value="stage.id"
        />
      </el-select>
      <el-select
        v-model="query.type"
        placeholder="全部题型"
        clearable
        style="width: 130px"
        @change="onSearch"
      >
        <el-option
          v-for="(label, value) in QUESTION_TYPE_LABELS"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-input
        v-model="query.keyword"
        placeholder="按题干搜索"
        clearable
        style="width: 200px"
        @clear="onSearch"
        @keyup.enter="onSearch"
      />
      <el-button
        type="primary"
        @click="onSearch"
      >
        查询
      </el-button>
      <div class="panel-toolbar-right">
        <el-button @click="openImport">
          批量导入
        </el-button>
        <el-button
          type="primary"
          @click="openCreate"
        >
          新增题目
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="items"
      stripe
      empty-text="暂无题目"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        label="题型"
        width="100"
      >
        <template #default="{ row }">
          {{ QUESTION_TYPE_LABELS[row.type as QuestionType] }}
        </template>
      </el-table-column>
      <el-table-column
        prop="contentPreview"
        label="题干预览"
        min-width="300"
        show-overflow-tooltip
      />
      <el-table-column
        prop="stageName"
        label="阶段"
        width="140"
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
            @click="openEdit(row as QuestionListItem)"
          >
            编辑
          </el-button>
          <el-button
            link
            :type="row.status === 'ENABLED' ? 'danger' : 'success'"
            @click="toggleStatus(row as QuestionListItem)"
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
      :title="editingId === null ? '新增题目' : '编辑题目'"
      width="680px"
      top="6vh"
    >
      <el-form label-width="96px">
        <el-form-item label="题型">
          <el-select
            v-model="form.type"
            :disabled="editingId !== null"
            style="width: 200px"
            @change="resetOptionsByType"
          >
            <el-option
              v-for="(label, value) in QUESTION_TYPE_LABELS"
              :key="value"
              :label="label"
              :value="value"
            />
          </el-select>
          <span
            v-if="editingId !== null"
            class="form-hint"
          >
            题型创建后不可修改
          </span>
        </el-form-item>
        <el-form-item label="学科">
          <el-select
            v-model="form.subjectId"
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
            v-model="form.stageId"
            placeholder="请先选择学科"
            :disabled="!form.subjectId"
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
        <el-form-item label="题干">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item
          v-if="isChoice"
          label="选项"
        >
          <div class="option-list">
            <div
              v-for="(option, index) in form.options"
              :key="index"
              class="option-row"
            >
              <span class="option-key">{{ String.fromCharCode(65 + index) }}</span>
              <el-input
                v-model="option.content"
                :disabled="form.type === 'TRUE_FALSE'"
                placeholder="选项内容"
              />
              <el-checkbox
                v-model="option.isCorrect"
                :disabled="form.type === 'TRUE_FALSE'"
              >
                正确
              </el-checkbox>
              <el-button
                v-if="form.type !== 'TRUE_FALSE'"
                link
                type="danger"
                @click="removeOption(index)"
              >
                删除
              </el-button>
            </div>
            <div
              v-if="form.type !== 'TRUE_FALSE'"
              class="option-actions"
            >
              <el-button
                link
                type="primary"
                @click="addOption"
              >
                添加选项
              </el-button>
              <span class="form-hint">
                {{ isMulti ? '多选题至少 2 个正确答案' : '单选题恰好 1 个正确答案' }}
              </span>
            </div>
            <span
              v-else
              class="form-hint"
            >
              判断题固定 A=正确、B=错误，勾选其中一个作为正确答案
            </span>
          </div>
        </el-form-item>

        <el-form-item
          v-if="form.type === 'SHORT_ANSWER'"
          label="评分点"
        >
          <div class="option-list">
            <div
              v-for="(point, index) in form.scoringPoints"
              :key="index"
              class="option-row"
            >
              <span class="option-key">{{ index + 1 }}</span>
              <el-input
                v-model="point.pointText"
                placeholder="评分点内容，等权计分"
                maxlength="500"
              />
              <el-button
                link
                type="danger"
                @click="removeScoringPoint(index)"
              >
                删除
              </el-button>
            </div>
            <div class="option-actions">
              <el-button
                link
                type="primary"
                @click="addScoringPoint"
              >
                添加评分点
              </el-button>
              <span class="form-hint">至少一条，AI 判分按命中数/总数计算得分率</span>
            </div>
          </div>
        </el-form-item>

        <el-form-item
          v-if="form.type === 'SHORT_ANSWER'"
          label="参考答案"
        >
          <el-input
            v-model="form.referenceAnswer"
            type="textarea"
            :rows="2"
            maxlength="2000"
            placeholder="选填，用于判分失败时展示给学员自评"
          />
        </el-form-item>
        <el-form-item label="解析">
          <el-input
            v-model="form.analysis"
            type="textarea"
            :rows="2"
            maxlength="2000"
            show-word-limit
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

    <el-dialog
      v-model="importVisible"
      title="批量导入题目"
      width="620px"
      @closed="resetImport"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="任一行校验失败则整批不写入；单文件 ≤ 5MB、≤ 1000 行。"
        style="margin-bottom: 14px"
      />
      <div class="import-actions">
        <el-button @click="downloadTemplate">
          下载 xlsx 模板
        </el-button>
        <label class="import-file">
          <input
            type="file"
            accept=".xlsx"
            hidden
            @change="onImportFileChange"
          >
          <span>{{ importFile ? importFile.name : '选择 xlsx 文件' }}</span>
        </label>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!importFile"
          @click="startImport"
        >
          开始导入
        </el-button>
      </div>

      <p
        v-if="importProgressText"
        class="import-progress"
      >
        {{ importProgressText }}
      </p>

      <template v-if="importTask">
        <el-descriptions
          :column="4"
          border
          size="small"
          style="margin-top: 14px"
        >
          <el-descriptions-item label="总行数">
            {{ importTask.totalRows }}
          </el-descriptions-item>
          <el-descriptions-item label="成功">
            {{ importTask.successRows }}
          </el-descriptions-item>
          <el-descriptions-item label="失败">
            {{ importTask.failedRows }}
          </el-descriptions-item>
          <el-descriptions-item label="结果">
            {{ importTask.status === 'SUCCEEDED' ? '已导入' : '整批回滚' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-table
          v-if="importTask.errors.length"
          :data="importTask.errors"
          size="small"
          max-height="260"
          style="margin-top: 12px"
        >
          <el-table-column
            prop="rowNo"
            label="行号"
            width="80"
          />
          <el-table-column
            prop="column"
            label="列"
            width="110"
          />
          <el-table-column
            prop="message"
            label="原因"
          />
        </el-table>
      </template>

      <template #footer>
        <el-button @click="importVisible = false">
          关闭
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

.option-list {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 8px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-key {
  width: 20px;
  font-weight: 700;
  color: var(--ink);
}

.option-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
}

.import-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.import-file {
  flex: 1;
  min-width: 220px;
  padding: 7px 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  text-align: center;
  cursor: pointer;
}

.import-progress {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
