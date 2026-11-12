<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { Tabbar, TabbarItem } from 'vant'

const route = useRoute()
const showTabbar = computed(() => route.meta.hideTabbar !== true)
</script>

<template>
  <div class="app-main">
    <RouterView />
  </div>
  <Tabbar
    v-if="showTabbar"
    route
    fixed
    placeholder
    safe-area-inset-bottom
  >
    <TabbarItem
      replace
      to="/home"
      icon="home-o"
    >
      首页
    </TabbarItem>
    <TabbarItem
      replace
      to="/routes"
      icon="apps-o"
    >
      路线
    </TabbarItem>
    <TabbarItem
      replace
      to="/wrongbook"
      icon="records-o"
    >
      错题本
    </TabbarItem>
    <TabbarItem
      replace
      to="/interview"
      icon="chat-o"
    >
      面试
    </TabbarItem>
    <TabbarItem
      replace
      to="/profile"
      icon="user-o"
    >
      我的
    </TabbarItem>
  </Tabbar>
</template>
