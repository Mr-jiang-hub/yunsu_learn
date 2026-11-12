<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  PREFIX_LABELS,
  formatBytes,
  systemApi,
  type OrphanFileReport,
} from '@/api/system'

const loading = ref(false)
const report = ref<OrphanFileReport | null>(null)

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback
}

async function load(): Promise<void> {
  loading.value = true
  try {
    report.value = await systemApi.orphanFiles()
  } catch (error) {
    ElMessage.error(errorText(error, '孤儿文件巡检失败'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="panel">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="只读巡检，不会删除任何对象"
      description="库里只有学科封面、课程封面、学员头像三列承接文件 key。上传后未绑定、或绑定后被覆盖/清空的对象会出现在下方候选里。判定是当前快照语义——刚上传还没来得及保存的 key 也会出现，请人工确认后再处理。"
      class="orphan-alert"
    />

    <div class="panel-toolbar">
      <el-button
        type="primary"
        :icon="Refresh"
        :loading="loading"
        @click="load"
      >
        重新巡检
      </el-button>
      <span
        v-if="report"
        class="scanned-at"
      >数据更新于 {{ report.scannedAt }}</span>
    </div>

    <el-descriptions
      v-if="report"
      :column="3"
      border
      class="summary"
    >
      <el-descriptions-item label="扫到对象">
        {{ report.scannedObjects }}
      </el-descriptions-item>
      <el-descriptions-item label="孤儿候选">
        {{ report.orphanCount }}
      </el-descriptions-item>
      <el-descriptions-item label="候选占用">
        {{ formatBytes(report.orphanBytes) }}
      </el-descriptions-item>
      <el-descriptions-item
        v-for="item in report.prefixes"
        :key="item.prefix"
        :label="PREFIX_LABELS[item.prefix] ?? item.prefix"
      >
        {{ item.orphanCount }} / {{ item.objectCount }}（{{ formatBytes(item.orphanBytes) }}）
      </el-descriptions-item>
    </el-descriptions>

    <el-alert
      v-if="report?.truncated"
      type="info"
      :closable="false"
      class="orphan-alert"
      :title="`明细仅展示前 ${report.items.length} 条，统计值是全量`"
    />

    <el-table
      v-loading="loading"
      :data="report?.items ?? []"
      stripe
      empty-text="没有孤儿候选"
    >
      <el-table-column
        label="业务前缀"
        width="110"
      >
        <template #default="{ row }">
          {{ PREFIX_LABELS[row.prefix] ?? row.prefix }}
        </template>
      </el-table-column>
      <el-table-column
        prop="key"
        label="对象 key"
        min-width="380"
        show-overflow-tooltip
      />
      <el-table-column
        label="大小"
        width="110"
      >
        <template #default="{ row }">
          {{ formatBytes(row.size) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="lastModified"
        label="最后修改"
        width="190"
      />
    </el-table>

    <p class="orphan-hint">
      候选是"当前未被引用"的对象，不等于一定可以删除：请先核对是否有刚上传待保存的封面/头像。
    </p>
  </div>
</template>

<style scoped>
.orphan-alert {
  margin-bottom: 16px;
}

.panel-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.scanned-at {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.summary {
  margin-bottom: 16px;
}

.orphan-hint {
  margin: 16px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
