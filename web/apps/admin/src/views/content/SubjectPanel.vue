<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contentApi, type ApiStatus, type SubjectItem } from '@/api/content'

const loading = ref(false)
const items = ref<SubjectItem[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '' as ApiStatus | '',
})

const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  description: '',
  sortNo: 0,
})

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const result = await contentApi.subjects.list({
      page: query.page,
      size: query.size,
      keyword: query.keyword,
      status: query.status,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(errorText(error, '加载学科失败'))
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
  form.name = ''
  form.description = ''
  form.sortNo = items.value.length
  dialogVisible.value = true
}

function openEdit(row: SubjectItem): void {
  editingId.value = row.id
  form.name = row.name
  form.description = row.description
  form.sortNo = row.sortNo
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!form.name.trim()) {
    ElMessage.warning('请填写学科名称')
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await contentApi.subjects.create({
        name: form.name.trim(),
        description: form.description.trim(),
        sortNo: form.sortNo,
      })
      ElMessage.success('学科已创建')
    } else {
      await contentApi.subjects.update(editingId.value, {
        name: form.name.trim(),
        description: form.description.trim(),
        sortNo: form.sortNo,
      })
      ElMessage.success('学科已更新')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (error) {
    ElMessage.error(errorText(error, '保存失败'))
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: SubjectItem): Promise<void> {
  const nextDisabled = row.status === 'ENABLED'
  try {
    if (nextDisabled) {
      await ElMessageBox.confirm(
        `停用后 C 端不再展示「${row.name}」，历史数据保留。确定停用？`,
        '停用学科',
        { type: 'warning' },
      )
      await contentApi.subjects.disable(row.id)
    } else {
      await contentApi.subjects.enable(row.id)
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

onMounted(fetchList)
</script>

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="按学科名称搜索"
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
          新增学科
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="items"
      stripe
      empty-text="暂无学科"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="70"
      />
      <el-table-column
        prop="name"
        label="学科名称"
        min-width="140"
      />
      <el-table-column
        prop="description"
        label="简介"
        min-width="240"
        show-overflow-tooltip
      />
      <el-table-column
        prop="pathName"
        label="关联路线"
        min-width="160"
      >
        <template #default="{ row }">
          {{ row.pathName || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="sortNo"
        label="排序"
        width="80"
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
        width="150"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openEdit(row as SubjectItem)"
          >
            编辑
          </el-button>
          <el-button
            link
            :type="row.status === 'ENABLED' ? 'danger' : 'success'"
            @click="toggleStatus(row as SubjectItem)"
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
      :title="editingId === null ? '新增学科' : '编辑学科'"
      width="480px"
    >
      <el-form label-width="90px">
        <el-form-item label="学科名称">
          <el-input
            v-model="form.name"
            maxlength="50"
            show-word-limit
            placeholder="如 Java 后端"
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
        <el-form-item label="排序号">
          <el-input-number
            v-model="form.sortNo"
            :min="0"
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

.panel-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
