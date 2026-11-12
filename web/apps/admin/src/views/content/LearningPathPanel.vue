<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  contentApi,
  QUESTION_TYPE_LABELS,
  type ApiStatus,
  type LearningPathDetail,
  type LearningPathItem,
  type StageBrief,
  type StageContents,
  type SubjectItem,
} from '@/api/content'

const loading = ref(false)
const items = ref<LearningPathItem[]>([])
const total = ref(0)
const subjects = ref<SubjectItem[]>([])
const query = reactive({
  page: 1,
  size: 10,
  subjectId: undefined as number | undefined,
  status: '' as ApiStatus | '',
})

/** 新建路线弹窗 */
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  subjectId: undefined as number | undefined,
  name: '',
  description: '',
})

/** 阶段编排抽屉 */
const drawerVisible = ref(false)
const detailLoading = ref(false)
const current = ref<LearningPathDetail | null>(null)

/** 阶段新增/编辑弹窗 */
const stageDialogVisible = ref(false)
const stageSubmitting = ref(false)
const stageEditingId = ref<number | null>(null)
const stageForm = reactive({
  name: '',
  description: '',
  sortNo: 0,
})

/** 阶段已挂载内容 */
const contentsVisible = ref(false)
const contentsLoading = ref(false)
const contentsTitle = ref('')
const contents = ref<StageContents | null>(null)

const stages = computed<StageBrief[]>(() => current.value?.stages ?? [])

/** 可建路线的学科：尚无路线的学科（路线与学科 1:1） */
const subjectOptions = computed(() => {
  const bound = new Set(items.value.map((item) => item.subjectId))
  return subjects.value.filter((subject) => !bound.has(subject.id) || subject.id === form.subjectId)
})

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function fetchSubjects(): Promise<void> {
  try {
    const result = await contentApi.subjects.list({ page: 1, size: 100 })
    subjects.value = result.items
  } catch (error) {
    ElMessage.error(errorText(error, '加载学科失败'))
  }
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const result = await contentApi.learningPaths.list({
      page: query.page,
      size: query.size,
      subjectId: query.subjectId,
      status: query.status,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载学习路线失败'))
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
  form.subjectId = undefined
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(row: LearningPathItem): void {
  editingId.value = row.id
  form.subjectId = row.subjectId
  form.name = row.name
  form.description = row.description
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!form.name.trim()) {
    ElMessage.warning('请填写路线名称')
    return
  }
  if (editingId.value === null && form.subjectId === undefined) {
    ElMessage.warning('请选择所属学科')
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await contentApi.learningPaths.create({
        subjectId: form.subjectId,
        name: form.name.trim(),
        description: form.description.trim(),
      })
      ElMessage.success('学习路线已创建')
    } else {
      // 路线创建后学科不可改：04 §6.10.6 禁止 PATCH subjectId
      await contentApi.learningPaths.update(editingId.value, {
        name: form.name.trim(),
        description: form.description.trim(),
      })
      ElMessage.success('学习路线已更新')
    }
    dialogVisible.value = false
    await Promise.all([fetchList(), fetchSubjects()])
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: LearningPathItem): Promise<void> {
  const nextDisabled = row.status === 'ENABLED'
  try {
    if (nextDisabled) {
      await ElMessageBox.confirm(
        `停用后 C 端不再展示「${row.name}」，学员已有进度保留。确定停用？`,
        '停用学习路线',
        { type: 'warning' },
      )
      await contentApi.learningPaths.disable(row.id)
    } else {
      await contentApi.learningPaths.enable(row.id)
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

async function openStages(row: LearningPathItem): Promise<void> {
  drawerVisible.value = true
  await reloadDetail(row.id)
}

async function reloadDetail(id: number): Promise<void> {
  detailLoading.value = true
  try {
    current.value = await contentApi.learningPaths.detail(id)
  } catch (error) {
    ElMessage.error(errorText(error, '加载路线详情失败'))
    drawerVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

function openStageCreate(): void {
  stageEditingId.value = null
  stageForm.name = ''
  stageForm.description = ''
  stageForm.sortNo = stages.value.length * 10 + 10
  stageDialogVisible.value = true
}

function openStageEdit(stage: StageBrief): void {
  stageEditingId.value = stage.id
  stageForm.name = stage.name
  stageForm.description = stage.description
  stageForm.sortNo = stage.sortNo
  stageDialogVisible.value = true
}

async function submitStage(): Promise<void> {
  if (!current.value) {
    return
  }
  if (!stageForm.name.trim()) {
    ElMessage.warning('请填写阶段名称')
    return
  }
  stageSubmitting.value = true
  try {
    if (stageEditingId.value === null) {
      await contentApi.learningPaths.createStage(current.value.id, {
        name: stageForm.name.trim(),
        description: stageForm.description.trim(),
        sortNo: stageForm.sortNo,
      })
      ElMessage.success('阶段已新增')
    } else {
      await contentApi.stages.update(stageEditingId.value, {
        name: stageForm.name.trim(),
        description: stageForm.description.trim(),
        sortNo: stageForm.sortNo,
      })
      ElMessage.success('阶段已更新')
    }
    stageDialogVisible.value = false
    await reloadDetail(current.value.id)
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    stageSubmitting.value = false
  }
}

async function toggleStage(stage: StageBrief): Promise<void> {
  if (!current.value) {
    return
  }
  const nextDisabled = stage.status === 'ENABLED'
  try {
    if (nextDisabled) {
      await ElMessageBox.confirm(
        `停用后 C 端不再展示「${stage.name}」，历史练习与面试的阶段名快照保留。确定停用？`,
        '停用阶段',
        { type: 'warning' },
      )
      await contentApi.stages.disable(stage.id)
    } else {
      await contentApi.stages.enable(stage.id)
    }
    ElMessage.success(nextDisabled ? '已停用' : '已启用')
    await reloadDetail(current.value.id)
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '操作失败'))
  }
}

/** 上移/下移：全量提交该路线阶段排序（04 §6.10.6 要求全量、不缺项） */
async function moveStage(index: number, delta: number): Promise<void> {
  if (!current.value) {
    return
  }
  const target = index + delta
  if (target < 0 || target >= stages.value.length) {
    return
  }
  const ordered = [...stages.value]
  const [moved] = ordered.splice(index, 1)
  ordered.splice(target, 0, moved)
  try {
    await contentApi.learningPaths.reorderStages(
      current.value.id,
      ordered.map((stage, position) => ({ id: stage.id, sortNo: (position + 1) * 10 })),
    )
    await reloadDetail(current.value.id)
  } catch (error) {
    ElMessage.error(errorText(error, '调整排序失败'))
  }
}

async function openContents(stage: StageBrief): Promise<void> {
  contentsTitle.value = stage.name
  contents.value = null
  contentsVisible.value = true
  contentsLoading.value = true
  try {
    contents.value = await contentApi.stages.contents(stage.id)
  } catch (error) {
    ElMessage.error(errorText(error, '加载阶段内容失败'))
  } finally {
    contentsLoading.value = false
  }
}

onMounted(() => {
  void fetchSubjects()
  void fetchList()
})
</script>

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-select
        v-model="query.subjectId"
        placeholder="全部学科"
        clearable
        style="width: 200px"
        @change="onSearch"
      >
        <el-option
          v-for="subject in subjects"
          :key="subject.id"
          :label="subject.name"
          :value="subject.id"
        />
      </el-select>
      <el-select
        v-model="query.status"
        placeholder="全部状态"
        clearable
        style="width: 140px"
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
          新增路线
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="items"
      stripe
      empty-text="暂无学习路线"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        prop="name"
        label="路线名称"
        min-width="180"
      />
      <el-table-column
        prop="subjectName"
        label="所属学科"
        min-width="140"
      />
      <el-table-column
        prop="description"
        label="简介"
        min-width="240"
        show-overflow-tooltip
      />
      <el-table-column
        prop="stageCount"
        label="阶段数"
        width="90"
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
        prop="updatedAt"
        label="更新时间"
        width="180"
      />
      <el-table-column
        label="操作"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openStages(row as LearningPathItem)"
          >
            阶段编排
          </el-button>
          <el-button
            link
            type="primary"
            @click="openEdit(row as LearningPathItem)"
          >
            编辑
          </el-button>
          <el-button
            link
            :type="row.status === 'ENABLED' ? 'danger' : 'success'"
            @click="toggleStatus(row as LearningPathItem)"
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
      :title="editingId === null ? '新增学习路线' : '编辑学习路线'"
      width="520px"
    >
      <el-form label-width="90px">
        <el-form-item label="所属学科">
          <el-select
            v-model="form.subjectId"
            placeholder="选择学科（一个学科仅一条路线）"
            :disabled="editingId !== null"
            style="width: 100%"
          >
            <el-option
              v-for="subject in editingId === null ? subjectOptions : subjects"
              :key="subject.id"
              :label="subject.name"
              :value="subject.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="路线名称">
          <el-input
            v-model="form.name"
            maxlength="50"
            show-word-limit
            placeholder="如 Java 后端成长路线"
          />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
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

    <el-drawer
      v-model="drawerVisible"
      :title="current ? `阶段编排 · ${current.name}` : '阶段编排'"
      size="820px"
    >
      <div
        v-loading="detailLoading"
        class="stage-body"
      >
        <div class="stage-toolbar">
          <span class="stage-hint">阶段按排序号升序展示；删除即停用，不物理删除。</span>
          <el-button
            type="primary"
            size="small"
            @click="openStageCreate"
          >
            新增阶段
          </el-button>
        </div>

        <el-table
          :data="stages"
          stripe
          empty-text="还没有阶段，先新增一个"
        >
          <el-table-column
            prop="sortNo"
            label="排序"
            width="70"
          />
          <el-table-column
            prop="name"
            label="阶段名称"
            min-width="140"
          />
          <el-table-column
            prop="description"
            label="简介"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="courseCount"
            label="课程"
            width="70"
          />
          <el-table-column
            prop="questionCount"
            label="题目"
            width="70"
          />
          <el-table-column
            label="状态"
            width="80"
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
            width="260"
            fixed="right"
          >
            <template #default="{ row, $index }">
              <el-button
                link
                :disabled="$index === 0"
                @click="moveStage($index, -1)"
              >
                上移
              </el-button>
              <el-button
                link
                :disabled="$index === stages.length - 1"
                @click="moveStage($index, 1)"
              >
                下移
              </el-button>
              <el-button
                link
                type="primary"
                @click="openStageEdit(row as StageBrief)"
              >
                编辑
              </el-button>
              <el-button
                link
                @click="openContents(row as StageBrief)"
              >
                内容
              </el-button>
              <el-button
                link
                :type="row.status === 'ENABLED' ? 'danger' : 'success'"
                @click="toggleStage(row as StageBrief)"
              >
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-dialog
      v-model="stageDialogVisible"
      :title="stageEditingId === null ? '新增阶段' : '编辑阶段'"
      width="480px"
      append-to-body
    >
      <el-form label-width="90px">
        <el-form-item label="阶段名称">
          <el-input
            v-model="stageForm.name"
            maxlength="50"
            show-word-limit
            placeholder="如 Java 基础"
          />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="stageForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number
            v-model="stageForm.sortNo"
            :min="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="stageSubmitting"
          @click="submitStage"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="contentsVisible"
      :title="`阶段内容 · ${contentsTitle}`"
      width="720px"
      append-to-body
    >
      <div v-loading="contentsLoading">
        <h4 class="contents-title">
          课程（{{ contents?.courses.length ?? 0 }}）
        </h4>
        <el-table
          :data="contents?.courses ?? []"
          size="small"
          empty-text="该阶段暂无课程"
        >
          <el-table-column
            prop="sortNo"
            label="排序"
            width="70"
          />
          <el-table-column
            prop="title"
            label="课程标题"
            min-width="200"
          />
          <el-table-column
            prop="bilibiliUrl"
            label="B 站链接"
            min-width="220"
            show-overflow-tooltip
          />
          <el-table-column
            label="状态"
            width="80"
          >
            <template #default="{ row }">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </template>
          </el-table-column>
        </el-table>

        <h4 class="contents-title">
          题目（{{ contents?.questions.length ?? 0 }}）
        </h4>
        <el-table
          :data="contents?.questions ?? []"
          size="small"
          empty-text="该阶段暂无题目"
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
              {{ QUESTION_TYPE_LABELS[row.type as keyof typeof QUESTION_TYPE_LABELS] ?? row.type }}
            </template>
          </el-table-column>
          <el-table-column
            prop="contentPreview"
            label="题干"
            min-width="280"
            show-overflow-tooltip
          />
          <el-table-column
            label="状态"
            width="80"
          >
            <template #default="{ row }">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </template>
          </el-table-column>
        </el-table>
        <p class="contents-hint">
          挂载内容请在「课程管理」「题库管理」中通过编辑所选阶段完成。
        </p>
      </div>
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

.panel-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.stage-body {
  min-height: 200px;
}

.stage-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.stage-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.contents-title {
  margin: 4px 0 10px;
  font-size: 14px;
  font-weight: 600;
}

.contents-hint {
  margin-top: 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
