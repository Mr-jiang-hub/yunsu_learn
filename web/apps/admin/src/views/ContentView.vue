<script setup lang="ts">
import { ref } from 'vue'
import SubjectPanel from './content/SubjectPanel.vue'
import CoursePanel from './content/CoursePanel.vue'
import QuestionPanel from './content/QuestionPanel.vue'

const activeTab = ref('subject')
</script>

<template>
  <div class="content-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane
        label="学科"
        name="subject"
      >
        <SubjectPanel v-if="activeTab === 'subject'" />
      </el-tab-pane>
      <el-tab-pane
        label="课程"
        name="course"
      >
        <CoursePanel v-if="activeTab === 'course'" />
      </el-tab-pane>
      <el-tab-pane
        label="题库"
        name="question"
      >
        <QuestionPanel v-if="activeTab === 'question'" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.content-page {
  padding: 4px 8px 16px;
}
</style>
