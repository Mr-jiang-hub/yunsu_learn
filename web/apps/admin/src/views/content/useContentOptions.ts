import { ref } from 'vue'
import { contentApi, type StageBrief, type SubjectItem } from '@/api/content'

/**
 * 学科与阶段的下拉数据源。
 * 阶段挂在学习路线下（学科 1:1 路线），因此按学科先取路线再取阶段。
 */
export function useContentOptions() {
  const subjects = ref<SubjectItem[]>([])
  const stages = ref<StageBrief[]>([])
  const loadingSubjects = ref(false)
  const loadingStages = ref(false)

  async function loadSubjects(): Promise<void> {
    if (subjects.value.length > 0 || loadingSubjects.value) {
      return
    }
    loadingSubjects.value = true
    try {
      const result = await contentApi.subjects.list({ page: 1, size: 100 })
      subjects.value = result.items
    } finally {
      loadingSubjects.value = false
    }
  }

  async function loadStages(subjectId?: number): Promise<void> {
    if (!subjectId) {
      stages.value = []
      return
    }
    loadingStages.value = true
    try {
      const paths = await contentApi.learningPaths.list({ page: 1, size: 10, subjectId })
      const first = paths.items[0]
      if (!first) {
        stages.value = []
        return
      }
      const detail = await contentApi.learningPaths.detail(first.id)
      stages.value = detail.stages
    } finally {
      loadingStages.value = false
    }
  }

  return { subjects, stages, loadingSubjects, loadingStages, loadSubjects, loadStages }
}
