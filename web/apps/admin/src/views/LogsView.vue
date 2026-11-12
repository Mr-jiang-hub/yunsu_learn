<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ACTION_LABELS,
  MODULE_LABELS,
  RESULT_LABELS,
  logApi,
  type OperationLogItem,
} from '@/api/logs'

const loading = ref(false)
const items = ref<OperationLogItem[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const query = reactive({
  moduleCode: '',
  action: '',
  targetType: '',
  result: '',
  limit: 20,
})
const timeRange = ref<[string, string] | null>(null)

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function load(reset: boolean): Promise<void> {
  loading.value = true
  try {
    const result = await logApi.list({
      cursor: reset ? undefined : nextCursor.value ?? undefined,
      limit: query.limit,
      moduleCode: query.moduleCode,
      action: query.action,
      targetType: query.targetType,
      result: query.result,
      startAt: timeRange.value?.[0],
      endAt: timeRange.value?.[1],
    })
    items.value = reset ? result.items : [...items.value, ...result.items]
    nextCursor.value = result.nextCursor
    hasMore.value = result.hasMore
    if (reset && result.items.length === 0) {
      ElMessage.info('当前条件没有日志记录')
    }
  } catch (error) {
    ElMessage.error(errorText(error, '加载操作日志失败'))
  } finally {
    loading.value = false
  }
}

function onSearch(): void {
  void load(true)
}

function onReset(): void {
  query.moduleCode = ''
  query.action = ''
  query.targetType = ''
  query.result = ''
  timeRange.value = null
  void load(true)
}

function resultTagType(result: string): 'success' | 'danger' | 'warning' {
  if (result === 'SUCCESS') {
    return 'success'
  }
  return result === 'DENIED' ? 'warning' : 'danger'
}

onMounted(() => {
  void load(true)
})
</script>

<template>
  <div class="panel">
    <div class="panel-toolbar">
      <el-select
        v-model="query.moduleCode"
        placeholder="全部模块"
        clearable
        style="width: 150px"
        @change="onSearch"
      >
        <el-option
          v-for="(label, code) in MODULE_LABELS"
          :key="code"
          :label="label"
          :value="code"
        />
      </el-select>
      <el-select
        v-model="query.action"
        placeholder="全部动作"
        clearable
        style="width: 130px"
        @change="onSearch"
      >
        <el-option
          v-for="(label, code) in ACTION_LABELS"
          :key="code"
          :label="label"
          :value="code"
        />
      </el-select>
      <el-select
        v-model="query.result"
        placeholder="全部结果"
        clearable
        style="width: 130px"
        @change="onSearch"
      >
        <el-option
          v-for="(label, code) in RESULT_LABELS"
          :key="code"
          :label="label"
          :value="code"
        />
      </el-select>
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ssZ"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        style="width: 360px"
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
      empty-text="暂无日志"
    >
      <el-table-column
        prop="id"
        label="ID"
        width="80"
      />
      <el-table-column
        prop="createdAt"
        label="时间"
        width="180"
      />
      <el-table-column
        label="操作人"
        min-width="150"
      >
        <template #default="{ row }">
          {{ row.operatorName || '系统' }}
          <span class="role-name">{{ row.roleName }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="模块"
        width="110"
      >
        <template #default="{ row }">
          {{ MODULE_LABELS[row.moduleCode] ?? row.moduleCode }}
        </template>
      </el-table-column>
      <el-table-column
        label="动作"
        width="100"
      >
        <template #default="{ row }">
          {{ ACTION_LABELS[row.action] ?? row.action }}
        </template>
      </el-table-column>
      <el-table-column
        label="对象"
        width="140"
      >
        <template #default="{ row }">
          {{ row.targetType }}{{ row.targetId === null ? '' : `#${row.targetId}` }}
        </template>
      </el-table-column>
      <el-table-column
        prop="detail"
        label="摘要"
        min-width="260"
        show-overflow-tooltip
      />
      <el-table-column
        label="结果"
        width="100"
      >
        <template #default="{ row }">
          <el-tag
            :type="resultTagType(row.result)"
            size="small"
          >
            {{ RESULT_LABELS[row.result] ?? row.result }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="ip"
        label="IP"
        width="140"
      />
      <el-table-column
        prop="traceId"
        label="traceId"
        width="180"
        show-overflow-tooltip
      />
    </el-table>

    <div class="log-footer">
      <span class="log-hint">日志只追加、不提供编辑与导出；按游标分页加载。</span>
      <el-button
        v-if="hasMore"
        :loading="loading"
        @click="load(false)"
      >
        加载更多
      </el-button>
      <span
        v-else
        class="log-hint"
      >已到最后一条</span>
    </div>
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

.role-name {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.log-footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 16px;
}

.log-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
