<script setup lang="ts">
import {
  Avatar,
  Bell,
  ChatDotRound,
  Collection,
  DataLine,
  Document,
  Expand,
  Files,
  Fold,
  Message,
  Notebook,
  User,
} from '@element-plus/icons-vue'
import { ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useSessionStore } from '@/stores/session'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const collapsed = ref(false)

async function onCommand(command: string): Promise<void> {
  if (command === 'logout') {
    await session.logout()
    await router.push({ name: 'login' })
  }
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside
      :width="collapsed ? '64px' : '224px'"
      class="admin-aside"
    >
      <div class="admin-brand">
        <span class="admin-brand-mark">云</span>
        <span v-if="!collapsed">云枢智学</span>
      </div>
      <el-menu
        class="admin-menu"
        :collapse="collapsed"
        :default-active="route.path"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <template #title>
            数据看板
          </template>
        </el-menu-item>
        <el-menu-item index="/content">
          <el-icon><Notebook /></el-icon>
          <template #title>
            内容管理
          </template>
        </el-menu-item>
        <el-menu-item index="/notices">
          <el-icon><Bell /></el-icon>
          <template #title>
            公告管理
          </template>
        </el-menu-item>
        <el-menu-item index="/messages">
          <el-icon><Message /></el-icon>
          <template #title>
            消息推送
          </template>
        </el-menu-item>
        <el-menu-item index="/paths">
          <el-icon><Collection /></el-icon>
          <template #title>
            学习路线
          </template>
        </el-menu-item>
        <el-menu-item index="/interview">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>
            面试设置
          </template>
        </el-menu-item>
        <el-menu-item index="/students">
          <el-icon><User /></el-icon>
          <template #title>
            学生管理
          </template>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><Avatar /></el-icon>
          <template #title>
            管理员与权限
          </template>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Document /></el-icon>
          <template #title>
            操作日志
          </template>
        </el-menu-item>
        <el-menu-item
          v-if="session.modules.includes('operation_log')"
          index="/orphan-files"
        >
          <el-icon><Files /></el-icon>
          <template #title>
            孤儿文件巡检
          </template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="admin-header-left">
          <el-button
            text
            :icon="collapsed ? Expand : Fold"
            aria-label="切换侧边栏"
            @click="collapsed = !collapsed"
          />
          <h2 class="admin-header-title">
            {{ route.meta.title ?? '管理后台' }}
          </h2>
        </div>
        <div class="admin-header-right">
          <el-tag
            size="small"
            effect="plain"
          >
            基础工程
          </el-tag>
          <el-dropdown @command="onCommand">
            <span class="admin-user">
              <el-icon><User /></el-icon>
              {{ session.admin?.displayName || session.admin?.username || '管理员' }}
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="admin-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>
