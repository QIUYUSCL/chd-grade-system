<template>
  <div class="score-entry">
    <div class="page-header">
      <h2>成绩录入</h2>
      <p class="sub-title">请选择录入模式，设置系数后即可录入。</p>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="entry-tabs">

      <el-tab-pane label="单条录入" name="single">
        <el-form :model="form" :rules="rules" ref="formRef" label-position="top" size="large">
          <el-row :gutter="24">
            <el-col :span="8">
              <el-form-item label="学期" prop="semester">
                <el-select v-model="form.semester" placeholder="选择学期" style="width: 100%">
                  <el-option label="2024-2025-1" value="2024-2025-1" />
                  <el-option label="2024-2025-2" value="2024-2025-2" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="课程" prop="courseId">
                <el-select v-model="form.courseId" placeholder="选择课程" style="width: 100%" :loading="courseLoading">
                  <el-option v-for="c in courses" :key="c.course_id" :label="c.course_name" :value="c.course_id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="学生学号" prop="studentId">
                <el-input
                    v-model="form.studentId"
                    placeholder="输入学号"
                    @blur="fetchStudentName"
                    clearable
                >
                  <template #suffix>
                    <span v-if="form.studentName" style="color: #67c23a; font-weight: bold; margin-right: 5px">
                      {{ form.studentName }}
                    </span>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="考试类型" prop="examType">
            <el-radio-group v-model="form.examType" fill="#1890ff" @change="calculateTotal">
              <el-radio-button label="正考">正考录入</el-radio-button>
              <el-radio-button label="补考">补考录入</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-divider content-position="left"><el-icon><EditPen /></el-icon> 成绩配置与录入</el-divider>

          <div class="score-input-area">
            <div v-if="form.examType === '正考'">
              <el-alert v-if="totalRatio !== 100" :title="`当前系数总和 ${totalRatio}%，需为100%`" type="warning" show-icon style="margin-bottom:15px"/>
              <el-row :gutter="20">
                <el-col :span="8" v-for="item in scoreConfigItems" :key="item.key">
                  <div class="score-item-card" :class="{ disabled: form[item.ratioKey] === 0, highlight: item.key === 'final' }">
                    <div class="label">{{ item.label }}</div>
                    <div class="inputs">
                      <el-input-number v-model="form[item.ratioKey]" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" placeholder="系数"/>
                      <span class="symbol">%</span>
                      <el-input-number v-model="form[item.scoreKey]" :min="0" :max="100" :precision="1" :controls="false" :disabled="form[item.ratioKey] === 0" @change="calculateTotal" placeholder="得分"/>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </div>
            <div v-else class="makeup-area">
              <el-alert title="补考总成绩直接取卷面分" type="info" :closable="false" show-icon />
              <el-form-item label="补考卷面成绩" prop="makeupScore" style="margin-top:15px">
                <el-input-number v-model="form.makeupScore" :min="0" :max="100" :precision="1" @change="calculateTotal"/>
              </el-form-item>
            </div>
          </div>

          <div class="total-score-bar">
            <span class="label">总评：</span>
            <span class="value">{{ form.totalScore !== null ? form.totalScore : '--' }}</span>
          </div>

          <div class="form-actions">
            <el-button @click="handleReset" :icon="Refresh">重置</el-button>
            <el-button type="primary" @click="handleSubmit('DRAFT')" :loading="loading" plain :icon="Document">暂存</el-button>
            <el-button type="success" @click="handleSubmit('SUBMITTED')" :loading="loading" :icon="Select" :disabled="form.examType === '正考' && totalRatio !== 100">提交</el-button>
          </div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="批量录入" name="batch">
        <div class="batch-header">
          <el-descriptions border :column="4" size="small" title="1. 录入配置">
            <el-descriptions-item label="学期">{{ form.semester }}</el-descriptions-item>
            <el-descriptions-item label="课程">
              <el-select v-model="form.courseId" placeholder="选择课程" size="small" @change="batchList = []">
                <el-option v-for="c in courses" :key="c.course_id" :label="c.course_name" :value="c.course_id" />
              </el-select>
            </el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-radio-group v-model="form.examType" size="small" @change="recalcAllBatchRows">
                <el-radio-button label="正考">正考</el-radio-button>
                <el-radio-button label="补考">补考</el-radio-button>
              </el-radio-group>
            </el-descriptions-item>
            <el-descriptions-item label="系数配置" v-if="form.examType === '正考'">
              <el-popover placement="bottom" title="全局系数设置" :width="300" trigger="click">
                <template #reference>
                  <el-button size="small" type="primary" link>配置 (当前:{{ totalRatio }}%)</el-button>
                </template>
                <div class="ratio-popover">
                  <div v-for="item in scoreConfigItems" :key="item.key" class="ratio-row">
                    <span>{{ item.label }}</span>
                    <el-input-number v-model="form[item.ratioKey]" size="small" :min="0" :max="100" @change="recalcAllBatchRows" />%
                  </div>
                </div>
              </el-popover>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="batch-loader" style="margin: 20px 0; display: flex; gap: 10px; align-items: center;">
          <el-button
              type="primary"
              @click="loadBatchStudents"
              :loading="batchLoading"
              :icon="Search"
              :disabled="!form.courseId">
            加载该课程选课学生
          </el-button>
          <span class="tip-text">注意：请先在"选课管理"中为学生分配课程，此处才能加载出学生列表。</span>
        </div>

        <el-table :data="batchList" border stripe height="500" style="width: 100%" v-if="batchList.length > 0">
          <el-table-column prop="student_id" label="学号" width="120" fixed />
          <el-table-column prop="name" label="姓名" width="100" fixed />

          <template v-if="form.examType === '正考'">
            <el-table-column v-for="item in activeScoreItems" :key="item.key" :label="`${item.label}(${form[item.ratioKey]}%)`" width="110" align="center">
              <template #default="{ row }">
                <el-input-number
                    v-model="row[item.scoreKey]"
                    size="small"
                    :min="0" :max="100" :precision="1" :controls="false"
                    style="width: 100%"
                    @change="calcRowTotal(row)"
                />
              </template>
            </el-table-column>
          </template>

          <template v-else>
            <el-table-column label="补考卷面" width="150" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.makeupScore" size="small" :min="0" :max="100" :precision="1" @change="calcRowTotal(row)" />
              </template>
            </el-table-column>
          </template>

          <el-table-column label="总评" width="100" fixed="right" align="center">
            <template #default="{ row }">
              <span :style="{ fontWeight: 'bold', color: row.totalScore < 60 ? '#f56c6c' : '#67c23a' }">
                {{ row.totalScore ?? '--' }}
              </span>
            </template>
          </el-table-column>
        </el-table>

        <div class="batch-footer" v-if="batchList.length > 0">
          <div class="count-info">共 {{ batchList.length }} 名学生</div>
          <div>
            <el-button @click="batchList = []">清空</el-button>
            <el-button type="primary" plain @click="submitBatch('DRAFT')" :loading="loading" :icon="Document">批量暂存</el-button>
            <el-button type="success" @click="submitBatch('SUBMITTED')" :loading="loading" :icon="Select">批量提交归档</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, Refresh, Document, Select, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'

// 全局状态
const activeTab = ref('single')
const loading = ref(false)
const courseLoading = ref(false)
const courses = ref([])
const formRef = ref()

// 批量状态
const batchLoading = ref(false)
const batchList = ref([])

// 表单数据
const form = reactive({
  semester: '2024-2025-1',
  courseId: '',
  studentId: '',
  studentName: '',
  examType: '正考',

  dailyRatio: 10,       dailyScore: null,
  attendanceRatio: 0, attendanceScore: null,
  homeworkRatio: 20,   homeworkScore: null,
  experimentRatio: 0,  experimentScore: null,
  midtermRatio: 0,     midtermScore: null,
  finalRatio: 70,      finalScore: null,

  makeupScore: null,
  totalScore: null
})

// 配置项
const scoreConfigItems = [
  { key: 'daily',  label: '平时成绩', ratioKey: 'dailyRatio',      scoreKey: 'dailyScore' },
  { key: 'attend', label: '考勤', ratioKey: 'attendanceRatio', scoreKey: 'attendanceScore' },
  { key: 'hw',     label: '作业', ratioKey: 'homeworkRatio',   scoreKey: 'homeworkScore' },
  { key: 'exp',    label: '实验', ratioKey: 'experimentRatio', scoreKey: 'experimentScore' },
  { key: 'mid',    label: '期中', ratioKey: 'midtermRatio',    scoreKey: 'midtermScore' },
  { key: 'final',  label: '期末', ratioKey: 'finalRatio',      scoreKey: 'finalScore' },
]

const activeScoreItems = computed(() => scoreConfigItems.filter(item => form[item.ratioKey] > 0))

const totalRatio = computed(() => {
  if (form.examType === '补考') return 100
  return scoreConfigItems.reduce((sum, item) => sum + (form[item.ratioKey] || 0), 0)
})

const rules = {
  semester: [{ required: true, message: '必填', trigger: 'change' }],
  courseId: [{ required: true, message: '必填', trigger: 'change' }],
  studentId: [{ required: true, message: '必填', trigger: 'blur' }]
}

// ---------------- 逻辑部分 ----------------

// 将 calcScoreLogic 移动到最前面，确保被调用前已定义
const calcScoreLogic = (dataContext, type) => {
  if (type === '补考') {
    return dataContext.makeupScore
  } else {
    let total = 0
    scoreConfigItems.forEach(item => {
      const score = dataContext[item.scoreKey]
      // 始终取全局系数 form[item.ratioKey]
      const ratio = form[item.ratioKey]
      if (ratio > 0 && score != null) {
        total += score * (ratio / 100)
      }
    })
    return Math.round(total * 10) / 10
  }
}

// 计算总分 (依赖 calcScoreLogic)
const calculateTotal = () => {
  form.totalScore = calcScoreLogic(form, form.examType)
}

// 行计算 (依赖 calcScoreLogic)
const calcRowTotal = (row) => {
  row.totalScore = calcScoreLogic(row, form.examType)
}

// 批量重算 (依赖 calcRowTotal)
const recalcAllBatchRows = () => {
  batchList.value.forEach(row => calcRowTotal(row))
  calculateTotal() // 同时也重算单条录入的总分
}

// Watchers (依赖 calculateTotal)
watch(() => form.examType, () => calculateTotal(), { immediate: true })

onMounted(async () => {
  await loadTeacherCourses()
})

const loadTeacherCourses = async () => {
  courseLoading.value = true
  try {
    const res = await request.get('/remote/client/teacher/courses')
    if (res.code === 200 && res.data) courses.value = res.data
  } finally {
    courseLoading.value = false
  }
}

const fetchStudentName = async () => {
  if (!form.studentId) return
  try {
    const res = await request.get('/remote/client/student/name', { params: { studentId: form.studentId } })
    if (res.code === 200) {
      form.studentName = res.data
    } else {
      form.studentName = '未找到'
    }
  } catch (e) {
    form.studentName = '查询失败'
  }
}

const loadBatchStudents = async () => {
  if (!form.courseId) return ElMessage.warning('请先选择课程')
  batchLoading.value = true
  try {
    const res = await request.get('/remote/client/course/students', {
      params: { courseId: form.courseId, semester: form.semester }
    })
    if (res.code === 200) {
      batchList.value = res.data.map(stu => ({
        ...stu,
        // 初始化所有分项
        attendanceScore: null, homeworkScore: null, experimentScore: null,
        midtermScore: null, dailyScore: null, finalScore: null,
        makeupScore: null, totalScore: null
      }))
      if (batchList.value.length === 0) ElMessage.warning('该课程暂无学生选课')
      else ElMessage.success(`加载了 ${batchList.value.length} 名学生`)
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    batchLoading.value = false
  }
}

const handleSubmit = async (status) => {
  await submitSingle(status)
}

const submitSingle = async (status) => {
  if (form.examType === '正考' && totalRatio.value !== 100) return ElMessage.warning('系数和非100%')

  await formRef.value.validate()
  loading.value = true
  try {
    const payload = buildPayload(form, status)
    const res = await request.post('/remote/client/grade/entry', payload)
    if (res.code === 200) {
      ElMessage.success('成功')
      handleReset()
    } else {
      ElMessage.error(res.message)
    }
  } catch(e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

const submitBatch = async (status) => {
  if (!form.courseId) return ElMessage.warning('请选择课程')
  if (form.examType === '正考' && totalRatio.value !== 100) return ElMessage.warning('系数和非100%')
  if (batchList.value.length === 0) return ElMessage.warning('列表为空')

  // 1. 过滤出有分数的行
  const validRows = batchList.value.filter(row => row.totalScore != null)
  if (validRows.length === 0) return ElMessage.warning('没有检测到有效分数的记录')

  // 2. 根据状态显示不同的提示文案
  const actionName = status === 'DRAFT' ? '暂存' : '归档'
  const tips = status === 'SUBMITTED' ? '归档后将无法修改！' : '暂存后可随时修改。'

  await ElMessageBox.confirm(
      `确定要${actionName}这 ${validRows.length} 条成绩吗？${tips}`,
      `批量${actionName}`,
      { type: status === 'SUBMITTED' ? 'warning' : 'info' }
  )

  loading.value = true
  try {
    // 3. 构建批量列表，传入对应的 status
    const grades = validRows.map(row => buildPayload(row, status))

    const res = await request.post('/remote/client/grade/batch-entry', { grades })
    if (res.code === 200) {
      ElMessage.success(`批量${actionName}成功`)
      // 如果是归档，通常清空列表；如果是暂存，可以保留或也清空，视需求而定，这里为了方便继续操作不清空
      if (status === 'SUBMITTED') batchList.value = []
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {
    if(e !== 'cancel') ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const buildPayload = (source, status) => {
  const data = {
    studentId: source.student_id || source.studentId,
    courseId: form.courseId,
    semester: form.semester,
    examType: form.examType,
    status: status,
    totalScore: source.totalScore?.toString()
  }

  if (form.examType === '补考') {
    if (source.makeupScore != null) data.makeupScore = source.makeupScore.toString()
  } else {
    scoreConfigItems.forEach(item => {
      if (source[item.scoreKey] != null) data[item.scoreKey] = source[item.scoreKey].toString()
    })
  }
  return data
}

const handleReset = () => {
  formRef.value.resetFields()
  form.studentName = ''
  form.totalScore = null
}
</script>

<style scoped>
.score-entry { max-width: 1000px; margin: 0 auto; padding-bottom: 40px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 5px; color: #303133; }
.sub-title { color: #909399; font-size: 14px; margin: 0; }

.score-input-area { background-color: #f8faff; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
.score-item-card { background: white; border-radius: 6px; padding: 10px; border: 1px solid #e4e7ed; }
.score-item-card.disabled { background: #f5f7fa; opacity: 0.7; }
.score-item-card.highlight { border-color: #b3d8ff; background: #ecf5ff; }
.score-item-card .label { font-weight: bold; color: #606266; margin-bottom: 8px; font-size: 13px; }
.score-item-card .inputs { display: flex; align-items: center; gap: 5px; }
.symbol { color: #909399; font-size: 12px; }

.total-score-bar {
  background: linear-gradient(135deg, #1890ff 0%, #36cfc9 100%);
  color: white; padding: 15px 25px; border-radius: 8px;
  display: flex; align-items: center; justify-content: flex-end; margin-top: 10px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
  font-size: 18px; font-weight: bold;
}

.form-actions { display: flex; justify-content: flex-end; gap: 15px; margin-top: 30px; }

.batch-header { margin-bottom: 20px; }
.batch-footer { margin-top: 20px; display: flex; justify-content: space-between; align-items: center; }
.ratio-row { display: flex; align-items: center; gap: 10px; margin-bottom: 5px; justify-content: space-between;}
.tip-text { font-size: 12px; color: #909399; }
</style>