<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Button,
  Checkbox,
  CheckboxGroup,
  Field,
  NavBar,
  Radio,
  RadioGroup,
  showToast,
} from 'vant'
import {
  QUESTION_TYPE_LABELS,
  practiceApi,
  type JudgeView,
  type PracticeResultResponse,
  type QuestionView,
} from '@/api/practice'

/** `mode=retry` 时走错题重练入口，不需要 stageId */
const props = defineProps<{ stageId?: string; mode?: 'stage' | 'retry' }>()
const router = useRouter()

type Phase = 'loading' | 'answering' | 'judged' | 'finished' | 'error'

const phase = ref<Phase>('loading')
const errorText = ref('')
const practiceId = ref<number | null>(null)
const question = ref<QuestionView | null>(null)
const nextQuestion = ref<QuestionView | null>(null)
const judge = ref<JudgeView | null>(null)
const result = ref<PracticeResultResponse | null>(null)

const total = ref(0)
const answeredCount = ref(0)
const remaining = ref(0)
const selected = ref<number[]>([])
const singleSelected = ref<number | null>(null)
const answerText = ref('')
const submitting = ref(false)
const assessing = ref(false)

const navTitle = computed(() => (props.mode === 'retry' ? '错题重练' : '随堂练习'))

const questionTypeLabel = computed(() =>
  question.value ? QUESTION_TYPE_LABELS[question.value.type] : '',
)

const isMultiple = computed(() => question.value?.type === 'MULTIPLE_CHOICE')
const isShortAnswer = computed(() => question.value?.type === 'SHORT_ANSWER')

const canSubmitAnswer = computed(() => {
  if (!question.value) {
    return false
  }
  if (isShortAnswer.value) {
    return answerText.value.trim().length > 0
  }
  return isMultiple.value ? selected.value.length > 0 : singleSelected.value !== null
})

const inProgressText = computed(() => `第 ${answeredCount.value + 1} / ${total.value} 题`)

function toMessage(caught: unknown, fallback: string): string {
  return caught instanceof Error && caught.message ? caught.message : fallback
}

async function start(): Promise<void> {
  phase.value = 'loading'
  errorText.value = ''
  try {
    const response = props.mode === 'retry'
      ? await practiceApi.startWrongRetry()
      : await practiceApi.startStage(Number(props.stageId))
    practiceId.value = response.practiceId
    total.value = response.questionCount
    answeredCount.value = response.answeredCount
    remaining.value = response.questionCount - response.answeredCount
    question.value = response.firstQuestion
    judge.value = null
    nextQuestion.value = null
    selected.value = []
    singleSelected.value = null
    answerText.value = ''
    if (!response.firstQuestion) {
      await finish()
      return
    }
    phase.value = 'answering'
  } catch (caught) {
    errorText.value = toMessage(caught, '开始练习失败')
    phase.value = 'error'
  }
}

async function submitAnswer(): Promise<void> {
  if (!practiceId.value || !question.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    const response = await practiceApi.answer(practiceId.value, {
      questionId: question.value.questionId,
      selectedOptionIds: isShortAnswer.value
        ? undefined
        : (isMultiple.value ? selected.value : (singleSelected.value === null ? [] : [singleSelected.value])),
      answerText: isShortAnswer.value ? answerText.value.trim() : undefined,
    })
    judge.value = response.answer
    nextQuestion.value = response.nextQuestion
    answeredCount.value = response.progress.answeredCount
    remaining.value = response.progress.remainingCount
    phase.value = 'judged'
  } catch (caught) {
    showToast(toMessage(caught, '提交失败'))
  } finally {
    submitting.value = false
  }
}

async function selfAssess(isCorrect: boolean): Promise<void> {
  if (!practiceId.value || !question.value || assessing.value) {
    return
  }
  assessing.value = true
  try {
    judge.value = await practiceApi.selfAssess(practiceId.value, question.value.questionId, isCorrect)
    showToast('自评已记录')
  } catch (caught) {
    showToast(toMessage(caught, '自评失败'))
  } finally {
    assessing.value = false
  }
}

function goNext(): void {
  if (nextQuestion.value) {
    question.value = nextQuestion.value
    nextQuestion.value = null
    judge.value = null
    selected.value = []
    singleSelected.value = null
    answerText.value = ''
    phase.value = 'answering'
    return
  }
  void finish()
}

async function finish(): Promise<void> {
  if (!practiceId.value) {
    return
  }
  try {
    await practiceApi.submit(practiceId.value)
    result.value = await practiceApi.result(practiceId.value)
    phase.value = 'finished'
  } catch (caught) {
    if (remaining.value > 0) {
      showToast(toMessage(caught, '还有题目未作答'))
      return
    }
    errorText.value = toMessage(caught, '提交练习失败')
    phase.value = 'error'
  }
}

function optionLabel(optionId: number): string {
  if (!question.value) {
    return ''
  }
  const hit = judge.value?.correctOptionIds.includes(optionId)
  return hit ? '（正确答案）' : ''
}

function backToStage(): void {
  if (props.mode === 'retry') {
    void router.replace({ name: 'wrongbook' })
    return
  }
  void router.replace({ name: 'stage', params: { stageId: props.stageId } })
}

async function restart(): Promise<void> {
  await start()
}

onMounted(start)
</script>

<template>
  <div class="quiz-page">
    <NavBar
      :title="navTitle"
      left-arrow
      @click-left="backToStage"
    />

    <div class="page">
      <p
        v-if="phase === 'loading'"
        class="muted"
      >
        正在准备题目…
      </p>

      <div
        v-else-if="phase === 'error'"
        class="card"
      >
        <p class="muted">
          {{ errorText }}
        </p>
        <Button
          block
          round
          type="primary"
          class="mt"
          @click="start"
        >
          重新开始
        </Button>
      </div>

      <template v-else-if="phase === 'finished' && result">
        <section class="card result-card">
          <p class="eyebrow">
            {{ result.type === 'WRONG_RETRY' ? '错题重练' : '阶段练习' }}
          </p>
          <h1>{{ result.correctRate }} 分</h1>
          <p class="muted">
            答对 {{ result.correctCount }} / {{ result.questionCount }} 题 ·
            用时 {{ Math.max(1, Math.round(result.durationSeconds / 60)) }} 分钟
          </p>
          <p
            v-if="result.masteryEligible"
            class="muted"
          >
            本次成绩已计入阶段掌握度。
          </p>
          <p
            v-else
            class="muted"
          >
            错题重练不计入阶段掌握度。
          </p>
        </section>

        <h2 class="section-title">
          错题解析
        </h2>
        <p
          v-if="result.wrongQuestions.length === 0"
          class="card muted"
        >
          全部答对，继续保持。
        </p>
        <article
          v-for="item in result.wrongQuestions"
          v-else
          :key="item.questionId"
          class="card"
        >
          <p class="q-type">
            {{ QUESTION_TYPE_LABELS[item.type] }}
          </p>
          <p class="q-content">
            {{ item.content }}
          </p>
          <p class="muted">
            我的答案：{{ item.myAnswer.answerText || (item.myAnswer.selectedOptionIds.length ? '已选选项' : '未作答') }}
          </p>
          <p
            v-if="item.referenceAnswer"
            class="muted"
          >
            参考答案：{{ item.referenceAnswer }}
          </p>
          <p class="muted">
            解析：{{ item.analysis }}
          </p>
        </article>

        <div class="result-actions">
          <Button
            block
            round
            plain
            type="primary"
            @click="backToStage"
          >
            {{ props.mode === 'retry' ? '返回错题本' : '返回阶段' }}
          </Button>
          <Button
            block
            round
            type="primary"
            class="mt"
            @click="restart"
          >
            再练一次
          </Button>
        </div>
      </template>

      <template v-else-if="question">
        <p class="progress-text">
          {{ inProgressText }}
        </p>

        <section class="card">
          <p class="q-type">
            {{ questionTypeLabel }}
          </p>
          <p class="q-content">
            {{ question.content }}
          </p>

          <template v-if="!isShortAnswer">
            <RadioGroup
              v-if="!isMultiple"
              v-model="singleSelected"
              :disabled="phase === 'judged'"
              class="options"
            >
              <Radio
                v-for="option in question.options"
                :key="option.optionId"
                :name="option.optionId"
                class="option"
              >
                {{ option.content }}{{ optionLabel(option.optionId) }}
              </Radio>
            </RadioGroup>
            <CheckboxGroup
              v-else
              v-model="selected"
              :disabled="phase === 'judged'"
              class="options"
            >
              <Checkbox
                v-for="option in question.options"
                :key="option.optionId"
                :name="option.optionId"
                class="option"
              >
                {{ option.content }}{{ optionLabel(option.optionId) }}
              </Checkbox>
            </CheckboxGroup>
          </template>

          <Field
            v-else
            v-model="answerText"
            type="textarea"
            rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="写下你的理解，尽量覆盖关键点"
            :disabled="phase === 'judged'"
            class="short-answer"
          />

          <Button
            v-if="phase === 'answering'"
            block
            round
            type="primary"
            :disabled="!canSubmitAnswer"
            :loading="submitting"
            class="mt"
            @click="submitAnswer"
          >
            提交答案
          </Button>
        </section>

        <section
          v-if="phase === 'judged' && judge"
          class="card judge-card"
          :class="{
            correct: judge.isCorrect === true,
            wrong: judge.isCorrect === false,
            pending: judge.judgeStatus === 'PENDING_SELF_ASSESSMENT',
          }"
        >
          <p class="judge-title">
            <template v-if="judge.judgeStatus === 'PENDING_SELF_ASSESSMENT'">
              自动判分暂不可用，请自评
            </template>
            <template v-else-if="judge.isCorrect === true">
              回答正确
            </template>
            <template v-else>
              回答错误
            </template>
          </p>

          <p
            v-if="judge.score !== null"
            class="muted"
          >
            得分 {{ judge.score }} 分
          </p>

          <template v-if="judge.judgeStatus === 'PENDING_SELF_ASSESSMENT'">
            <p class="muted">
              参考答案：{{ judge.referenceAnswer || '暂无参考答案' }}
            </p>
            <div class="self-assess">
              <Button
                size="small"
                round
                type="success"
                :loading="assessing"
                @click="selfAssess(true)"
              >
                我答对了
              </Button>
              <Button
                size="small"
                round
                plain
                type="danger"
                :loading="assessing"
                @click="selfAssess(false)"
              >
                我答错了
              </Button>
            </div>
          </template>

          <template v-else>
            <ul
              v-if="judge.hitPoints.length > 0"
              class="hit-points"
            >
              <li
                v-for="point in judge.hitPoints"
                :key="point.point"
                :class="{ hit: point.hit }"
              >
                {{ point.hit ? '✓' : '✗' }} {{ point.point }}
              </li>
            </ul>
            <p
              v-if="judge.referenceAnswer"
              class="muted"
            >
              参考答案：{{ judge.referenceAnswer }}
            </p>
            <p class="muted">
              解析：{{ judge.analysis }}
            </p>
          </template>

          <Button
            block
            round
            type="primary"
            class="mt"
            :disabled="judge.judgeStatus === 'PENDING_SELF_ASSESSMENT'"
            @click="goNext"
          >
            {{ remaining > 0 ? '下一题' : '查看结果' }}
          </Button>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.quiz-page {
  min-height: 100vh;
  background: var(--bg);
}

.progress-text {
  margin: 0 4px 10px;
  font-size: 13px;
  color: var(--muted);
}

.q-type {
  display: inline-block;
  margin: 0 0 10px;
  padding: 2px 8px;
  border-radius: 6px;
  background: #eef0ff;
  color: var(--brand);
  font-size: 12px;
  font-weight: 600;
}

.q-content {
  margin: 0;
  font-size: 16px;
  line-height: 1.7;
  color: var(--ink);
}

.options {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.option {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--card);
}

.short-answer {
  margin-top: 12px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
}

.judge-card {
  margin-top: 12px;
  border-left: 4px solid var(--line);
}

.judge-card.correct {
  border-left-color: #22a06b;
}

.judge-card.wrong {
  border-left-color: #e34d59;
}

.judge-card.pending {
  border-left-color: #ed7b2f;
}

.judge-title {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
}

.hit-points {
  margin: 8px 0 10px;
  padding-left: 4px;
  list-style: none;
  font-size: 13px;
  color: var(--muted);
}

.hit-points .hit {
  color: #22a06b;
}

.self-assess {
  display: flex;
  gap: 10px;
  margin: 14px 0;
}

.result-card {
  text-align: center;
}

.result-card h1 {
  margin: 6px 0 8px;
  font-size: 40px;
  color: var(--brand);
}

.section-title {
  margin: 22px 4px 12px;
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-2);
}

.result-actions {
  margin-top: 18px;
}

.mt {
  margin-top: 14px;
}
</style>
