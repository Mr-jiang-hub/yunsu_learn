<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    module: string
    description: string
    endpoints?: string[]
  }>(),
  { endpoints: () => [] },
)
</script>

<template>
  <div class="admin-page">
    <header class="admin-page-head">
      <p class="eyebrow">
        {{ module }}
      </p>
      <h1>{{ title }}</h1>
      <p class="desc">
        {{ description }}
      </p>
    </header>

    <div class="admin-card">
      <p class="admin-card-title">
        工程骨架状态
      </p>
      <p class="admin-muted">
        统一响应、错误码、双认证链与请求封装已接入。列表、表单与真实接口联调在后续迭代完成。
      </p>
      <ul
        v-if="endpoints.length"
        class="admin-endpoints"
      >
        <li
          v-for="item in endpoints"
          :key="item"
        >
          {{ item }}
        </li>
      </ul>
    </div>
  </div>
</template>
