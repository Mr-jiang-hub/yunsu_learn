<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goDashboard(): void {
  void router.replace('/dashboard')
}
</script>

<template>
  <div class="not-found">
    <p class="eyebrow">
      404
    </p>
    <h1>页面不存在</h1>
    <p class="admin-muted">
      你访问的管理页面可能已移动或尚未开放。
    </p>
    <el-button
      type="primary"
      @click="goDashboard"
    >
      返回数据看板
    </el-button>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  background: var(--bg);
  text-align: center;
}

.not-found h1 {
  margin: 0;
  font-size: 26px;
  color: var(--ink);
}

.not-found .eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--brand);
}

.not-found .admin-muted {
  margin-bottom: 8px;
}
</style>
