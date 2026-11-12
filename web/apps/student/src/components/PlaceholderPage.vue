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
  <section class="page">
    <header class="page-head">
      <p class="eyebrow">
        {{ module }}
      </p>
      <h1>{{ title }}</h1>
      <p class="desc">
        {{ description }}
      </p>
    </header>

    <div class="card">
      <p class="card-title">
        工程骨架状态
      </p>
      <p class="muted">
        路由、布局、请求封装、统一响应与错误码已接入。页面业务逻辑与真实接口联调在后续迭代完成。
      </p>
      <ul
        v-if="endpoints.length"
        class="endpoints"
      >
        <li
          v-for="item in endpoints"
          :key="item"
        >
          <code>{{ item }}</code>
        </li>
      </ul>
    </div>
  </section>
</template>
