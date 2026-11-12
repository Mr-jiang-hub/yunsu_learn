<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  interviewApi,
  type DifficultyItem,
  type DimensionItem,
} from '@/api/interview'
import { useContentOptions } from '@/views/content/useContentOptions'

const activeTab = ref<'difficulty' | 'dimension'>('difficulty')

/* ------------------------------------------------------------ 难度档位 */

const difficulties = ref<DifficultyItem[]>([])
const loadingDifficulties = ref(false)
const difficultyDialog = ref(false)
const difficultySubmitting = ref(false)
const editingDifficultyId = ref<number | null>(null)
const difficultyForm = ref({
  name: '',
  description: '',
  focus: '',
  sortNo: 0,
})

async function fetchDifficulties(): Promise<void> {
  loadingDifficulties.value = true
  try {
    const result = await interviewApi.difficulties.list()
    difficulties.value = result.items
  } catch (error) {
    ElMessage.error(errorText(error, '加载难度档位失败'))
  } finally {
    loadingDifficulties.value = false
  }
}

function openDifficultyEdit(row: DifficultyItem): void {
  editingDifficultyId.value = row.id
  difficultyForm.value = {
    name: row.name,
    description: row.description,
    focus: row.focus,
    sortNo: row.sortNo,
  }
  difficultyDialog.value = true
}

async function submitDifficulty(): Promise<void> {
  const form = difficultyForm.value
  if (!form.name.trim()) {
    ElMessage.warning('请填写难度名称')
    return
  }
  difficultySubmitting.value = true
  try {
    await interviewApi.difficulties.update(editingDifficultyId.value!, {
      name: form.name.trim(),
      description: form.description.trim(),
      focus: form.focus.trim(),
      sortNo: form.sortNo,
    })
    ElMessage.success('难度档位已更新')
    difficultyDialog.value = false
    await fetchDifficulties()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    difficultySubmitting.value = false
  }
}

async function toggleDifficulty(row: DifficultyItem): Promise<void> {
  const disabling = row.status === 'ENABLED'
  try {
    if (disabling) {
      await ElMessageBox.confirm(
        `停用后 C 端不可选择「${row.name}」难度，历史面试保留难度快照。确定停用？`,
        '停用难度',
        { type: 'warning' },
      )
      await interviewApi.difficulties.disable(row.id)
    } else {
      await interviewApi.difficulties.enable(row.id)
    }
    ElMessage.success(disabling ? '已停用' : '已启用')
    await fetchDifficulties()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '操作失败'))
  }
}

/* ------------------------------------------------------------ 能力维度 */

const { subjects, loadSubjects } = useContentOptions()
const subjectId = ref<number | null>(null)
const dimensions = ref<DimensionItem[]>([])
const loadingDimensions = ref(false)
const dimensionDialog = ref(false)
const dimensionSubmitting = ref(false)
const editingDimensionId = ref<number | null>(null)
const dimensionForm = ref({
  name: '',
  sortNo: 0,
})

const activeSubjectName = computed(
  () => subjects.value.find((item) => item.id === subjectId.value)?.name ?? '',
)

async function fetchDimensions(): Promise<void> {
  if (!subjectId.value) {
    dimensions.value = []
    return
  }
  loadingDimensions.value = true
  try {
    const result = await interviewApi.dimensions.list(subjectId.value)
    dimensions.value = result.items
  } catch (error) {
    ElMessage.error(errorText(error, '加载能力维度失败'))
  } finally {
    loadingDimensions.value = false
  }
}

function onSubjectChange(): void {
  void fetchDimensions()
}

function openDimensionCreate(): void {
  if (!subjectId.value) {
    ElMessage.warning('请先选择学科')
    return
  }
  editingDimensionId.value = null
  dimensionForm.value = {
    name: '',
    sortNo: (dimensions.value.length + 1) * 10,
  }
  dimensionDialog.value = true
}

function openDimensionEdit(row: DimensionItem): void {
  editingDimensionId.value = row.id
  dimensionForm.value = { name: row.name, sortNo: row.sortNo }
  dimensionDialog.value = true
}

async function submitDimension(): Promise<void> {
  const form = dimensionForm.value
  if (!form.name.trim()) {
    ElMessage.warning('请填写维度名称')
    return
  }
  dimensionSubmitting.value = true
  try {
    if (editingDimensionId.value === null) {
      await interviewApi.dimensions.create({
        subjectId: subjectId.value!,
        name: form.name.trim(),
        sortNo: form.sortNo,
      })
      ElMessage.success('维度已新增')
    } else {
      await interviewApi.dimensions.update(editingDimensionId.value, {
        name: form.name.trim(),
        sortNo: form.sortNo,
      })
      ElMessage.success('维度已更新')
    }
    dimensionDialog.value = false
    await fetchDimensions()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    dimensionSubmitting.value = false
  }
}

async function toggleDimension(row: DimensionItem): Promise<void> {
  const disabling = row.status === 'ENABLED'
  try {
    if (disabling) {
      await ElMessageBox.confirm(
        `停用后「${row.name}」不再参与出题与评分，历史报告保留维度快照。确定停用？`,
        '停用维度',
        { type: 'warning' },
      )
      await interviewApi.dimensions.disable(row.id)
    } else {
      await interviewApi.dimensions.enable(row.id)
    }
    ElMessage.success(disabling ? '已停用' : '已启用')
    await fetchDimensions()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(errorText(error, '操作失败'))
  }
}

/** 上下移动：本地交换后按 10 的步长全量提交（后端要求覆盖全部维度） */
async function moveDimension(index: number, delta: number): Promise<void> {
  const target = index + delta
  if (target < 0 || target >= dimensions.value.length || !subjectId.value) {
    return
  }
  const next = [...dimensions.value]
  const [moved] = next.splice(index, 1)
  if (!moved) {
    return
  }
  next.splice(target, 0, moved)
  dimensions.value = next
  try {
    await interviewApi.dimensions.reorder({
      subjectId: subjectId.value,
      items: next.map((item, position) => ({ id: item.id, sortNo: (position + 1) * 10 })),
    })
    await fetchDimensions()
  } catch (error) {
    ElMessage.error(errorText(error, '调整排序失败'))
    await fetchDimensions()
  }
}

/* ------------------------------------------------------------ 公共 */

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

function onTabChange(name: string | number): void {
  if (name === 'dimension' && dimensions.value.length === 0) {
    void fetchDimensions()
  }
}

onMounted(async () => {
  await fetchDifficulties()
  await loadSubjects()
  const first = subjects.value[0]
  if (first) {
    subjectId.value = first.id
  }
})
</script>

<template>
  <div class="interview-settings">
    <el-tabs
      v-model="activeTab"
      @tab-change="onTabChange"
    >
      <el-tab-pane
        label="难度档位"
        name="difficulty"
      >
        <el-alert
          class="settings-tip"
          type="info"
          :closable="false"
          show-icon
          title="难度为全局配置（固定三档），编码创建后不可修改；停用后 C 端不可选，历史面试保留难度快照。"
        />
        <el-table
          v-loading="loadingDifficulties"
          :data="difficulties"
          stripe
          empty-text="暂无难度档位，请检查基线种子数据"
        >
          <el-table-column
            prop="sortNo"
            label="排序"
            width="80"
          />
          <el-table-column
            prop="code"
            label="编码"
            width="110"
          >
            <template #default="{ row }">
              <el-tag
                type="info"
                size="small"
                effect="plain"
              >
                {{ row.code }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="name"
            label="名称"
            width="110"
          />
          <el-table-column
            prop="description"
            label="难度描述"
            min-width="240"
            show-overflow-tooltip
          />
          <el-table-column
            prop="focus"
            label="考察重点"
            min-width="200"
            show-overflow-tooltip
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
                @click="openDifficultyEdit(row as DifficultyItem)"
              >
                编辑
              </el-button>
              <el-button
                link
                :type="row.status === 'ENABLED' ? 'danger' : 'success'"
                @click="toggleDifficulty(row as DifficultyItem)"
              >
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane
        label="能力维度"
        name="dimension"
      >
        <div class="panel-toolbar">
          <el-select
            v-model="subjectId"
            placeholder="选择学科"
            style="width: 220px"
            @change="onSubjectChange"
          >
            <el-option
              v-for="item in subjects"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <span
            v-if="activeSubjectName"
            class="toolbar-hint"
          >
            维度用于面试出题与报告评分，同一学科内名称唯一
          </span>
          <div class="panel-toolbar-right">
            <el-button
              type="primary"
              @click="openDimensionCreate"
            >
              新增维度
            </el-button>
          </div>
        </div>

        <el-table
          v-loading="loadingDimensions"
          :data="dimensions"
          stripe
          empty-text="该学科暂无能力维度"
        >
          <el-table-column
            label="排序"
            width="110"
          >
            <template #default="{ $index }">
              <el-button
                link
                :disabled="$index === 0"
                @click="moveDimension($index, -1)"
              >
                上移
              </el-button>
              <el-button
                link
                :disabled="$index === dimensions.length - 1"
                @click="moveDimension($index, 1)"
              >
                下移
              </el-button>
            </template>
          </el-table-column>
          <el-table-column
            prop="sortNo"
            label="排序号"
            width="90"
          />
          <el-table-column
            prop="name"
            label="维度名称"
            min-width="200"
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
                @click="openDimensionEdit(row as DimensionItem)"
              >
                编辑
              </el-button>
              <el-button
                link
                :type="row.status === 'ENABLED' ? 'danger' : 'success'"
                @click="toggleDimension(row as DimensionItem)"
              >
                {{ row.status === 'ENABLED' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="difficultyDialog"
      title="编辑难度档位"
      width="520px"
    >
      <el-form label-width="90px">
        <el-form-item label="编码">
          <el-tag
            type="info"
            effect="plain"
          >
            {{ difficulties.find((item) => item.id === editingDifficultyId)?.code ?? '—' }}
          </el-tag>
          <span class="form-hint">稳定标识，不可修改</span>
        </el-form-item>
        <el-form-item label="名称">
          <el-input
            v-model="difficultyForm.name"
            maxlength="20"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="难度描述">
          <el-input
            v-model="difficultyForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="考察重点">
          <el-input
            v-model="difficultyForm.focus"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number
            v-model="difficultyForm.sortNo"
            :min="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="difficultyDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="difficultySubmitting"
          @click="submitDifficulty"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="dimensionDialog"
      :title="editingDimensionId === null ? '新增维度' : '编辑维度'"
      width="480px"
    >
      <el-form label-width="90px">
        <el-form-item label="所属学科">
          <span>{{ activeSubjectName || '—' }}</span>
          <span class="form-hint">维度不跨学科移动</span>
        </el-form-item>
        <el-form-item label="维度名称">
          <el-input
            v-model="dimensionForm.name"
            maxlength="50"
            show-word-limit
            placeholder="如 集合与并发"
          />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number
            v-model="dimensionForm.sortNo"
            :min="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dimensionDialog = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="dimensionSubmitting"
          @click="submitDimension"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.settings-tip {
  margin-bottom: 16px;
}

.panel-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.panel-toolbar-right {
  margin-left: auto;
}

.toolbar-hint,
.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.form-hint {
  margin-left: 8px;
}
</style>
