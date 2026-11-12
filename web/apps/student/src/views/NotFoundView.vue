<script setup lang="ts">
import { Button } from 'vant'
</script>

<template>
  <section class="page not-found">
    <p class="eyebrow">
      404
    </p>
    <h1>页面不存在</h1>
    <p class="muted">
      你访问的页面可能已移动或尚未开放。
    </p>
    <div class="actions">
      <Button
        type="primary"
        to="/home"
        replace
      >
        返回首页
      </Button>
    </div>
  </section>
</template>

<style scoped>
.not-found {
  padding-top: 72px;
  text-align: center;
}

.not-found h1 {
  margin: 0 0 12px;
  font-size: 24px;
  color: var(--ink);
}

.actions {
  margin-top: 28px;
  display: flex;
  justify-content: center;
}
</style>
