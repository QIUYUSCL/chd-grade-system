<template>
  <div class="score-entry">
    <div class="page-header">
      <h2>成绩录入</h2>
      <p class="sub-title">请设置各项成绩占比系数（不需要的项请设为0），系统将自动计算总评。</p>
    </div>

    <el-card shadow="hover">
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
              <el-select
                  v-model="form.courseId"
                  placeholder="选择课程"
                  :loading="courseLoading"
                  style="width: 100%"
                  :disabled="courseLoading || courses.length === 0">
                <el-option
                    v-for="course in courses"
                    :key="course.course_id"
                    :label="course.course_name"
                    :value="course.course_id">
                  <span style="float: left">{{ course.course_name }}</span>
                  <span style="float: right; color: #8492a6; font-size: 13px">{{ course.course_id }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="学生学号" prop="studentId">
              <el-input v-model="form.studentId" placeholder="输入学号" clearable />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="考试类型" prop="examType" class="exam-type-item">
          <el-radio-group v-model="form.examType" fill="#1890ff" @change="calculateTotal">
            <el-radio-button label="正考">正考录入</el-radio-button>
            <el-radio-button label="补考">补考录入</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left"><el-icon><EditPen /></el-icon> 成绩配置与录入</el-divider>

        <div class="score-input-area">

          <div v-if="form.examType === '正考'">
            <el-alert
                v-if="totalRatio !== 100"
                :title="`当前系数总和为 ${totalRatio}%，请调整至 100%`"
                type="warning"
                show-icon
                style="margin-bottom: 15px"
            />

            <el-row :gutter="20">
              <el-col :span="8">
                <div class="score-item-card" :class="{ disabled: form.attendanceRatio === 0 }">
                  <div class="label">考勤</div>
                  <div class="inputs">
                    <el-input-number v-model="form.attendanceRatio" :min="0" :max="100" size="small" @change="calculateTotal" placeholder="系数" />
                    <span class="symbol">%</span>
                    <el-input-number v-model="form.attendanceScore" :min="0" :max="100" :precision="1" :disabled="form.attendanceRatio === 0" placeholder="得分" @change="calculateTotal" />
                  </div>
                </div>
              </el-col>

              <el-col :span="8">
                <div class="score-item-card" :class="{ disabled: form.homeworkRatio === 0 }">
                  <div class="label">作业</div>
                  <div class="inputs">
                    <el-input-number v-model="form.homeworkRatio" :min="0" :max="100" size="small" @change="calculateTotal" />
                    <span class="symbol">%</span>
                    <el-input-number v-model="form.homeworkScore" :min="0" :max="100" :precision="1" :disabled="form.homeworkRatio === 0" @change="calculateTotal" />
                  </div>
                </div>
              </el-col>

              <el-col :span="8">
                <div class="score-item-card" :class="{ disabled: form.experimentRatio === 0 }">
                  <div class="label">实验</div>
                  <div class="inputs">
                    <el-input-number v-model="form.experimentRatio" :min="0" :max="100" size="small" @change="calculateTotal" />
                    <span class="symbol">%</span>
                    <el-input-number v-model="form.experimentScore" :min="0" :max="100" :precision="1" :disabled="form.experimentRatio === 0" @change="calculateTotal" />
                  </div>
                </div>
              </el-col>

              <el-col :span="8" style="margin-top: 15px">
                <div class="score-item-card" :class="{ disabled: form.midtermRatio === 0 }">
                  <div class="label">期中</div>
                  <div class="inputs">
                    <el-input-number v-model="form.midtermRatio" :min="0" :max="100" size="small" @change="calculateTotal" />
                    <span class="symbol">%</span>
                    <el-input-number v-model="form.midtermScore" :min="0" :max="100" :precision="1" :disabled="form.midtermRatio === 0" @change="calculateTotal" />
                  </div>
                </div>
              </el-col>

              <el-col :span="8" style="margin-top: 15px">
                <div class="score-item-card" :class="{ disabled: form.dailyRatio === 0 }">
                  <div class="label">其他平时</div>
                  <div class="inputs">
                    <el-input-number v-model="form.dailyRatio" :min="0" :max="100" size="small" @change="calculateTotal" />
                    <span class="symbol">%</span>
                    <el-input-number v-model="form.dailyScore" :min="0" :max="100" :precision="1" :disabled="form.dailyRatio === 0" @change="calculateTotal" />
                  </div>
                </div>
              </el-col>

              <el-col :span="8" style="margin-top: 15px">
                <div class="score-item-card highlight">
                  <div class="label">期末考试</div>
                  <div class="inputs">
                    <el-input-number v-model="form.finalRatio" :min="0" :max="100" size="small" @change="calculateTotal" />
                    <span class="symbol">%</span>
                    <el-input-number v-model="form.finalScore" :min="0" :max="100" :precision="1" @change="calculateTotal" />
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>

          <transition name="el-zoom-in-top">
            <div v-if="form.examType === '补考'" class="makeup-area">
              <el-alert title="补考模式说明：总成绩将直接取补考卷面成绩，不计算平时分。" type="info" :closable="false" show-icon style="margin-bottom: 20px" />
              <el-form-item label="补考卷面成绩" prop="makeupScore">
                <el-input-number v-model="form.makeupScore" :min="0" :max="100" :precision="1" style="width: 200px" @change="calculateTotal"/>
              </el-form-item>
            </div>
          </transition>
        </div>

        <div class="total-score-bar">
          <span class="label">自动计算总评：</span>
          <span class="value">{{ form.totalScore !== null ? form.totalScore : '--' }}</span>
          <span class="unit">分</span>
        </div>

        <div class="form-actions">
          <el-button @click="handleReset" :icon="Refresh">重置</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="loading" plain :icon="Document">暂存草稿</el-button>
          <el-button type="success" @click="handleSubmitAndCommit" :loading="loading" :icon="Select" :disabled="form.examType === '正考' && totalRatio !== 100">提交归档</el-button>
        </div>

      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
// 导入图标
import { EditPen, Refresh, Document, Select } from '@element-plus/icons-vue'
import request from '@/utils/request'

const formRef = ref()
const loading = ref(false)
const courseLoading = ref(false)
const courses = ref([])

const form = reactive({
  semester: '2024-2025-1',
  courseId: '',
  studentId: '',
  examType: '正考',

  attendanceRatio: 10, attendanceScore: null,
  homeworkRatio: 20,   homeworkScore: null,
  experimentRatio: 0,  experimentScore: null,
  midtermRatio: 0,     midtermScore: null,
  dailyRatio: 0,       dailyScore: null,
  finalRatio: 70,      finalScore: null,

  makeupScore: null,
  totalScore: null
})

// 计算当前系数总和
const totalRatio = computed(() => {
  if (form.examType === '补考') return 100
  return form.attendanceRatio + form.homeworkRatio + form.experimentRatio +
      form.midtermRatio + form.dailyRatio + form.finalRatio
})

const rules = {
  semester: [{ required: true, message: '请选择学期', trigger: 'change' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  examType: [{ required: true, message: '请选择考试类型', trigger: 'change' }]
}

// -------------------------------------------------------------
// 【关键修改】必须将 calculateTotal 定义在 watch 之前！
// -------------------------------------------------------------
const calculateTotal = () => {
  // 补考模式：总分 = 补考卷面分
  if (form.examType === '补考') {
    form.totalScore = form.makeupScore
    return
  }

  // 正考模式：加权计算
  let total = 0
  const add = (score, ratio) => {
    if (ratio > 0 && score !== null && !isNaN(score)) {
      total += score * (ratio / 100)
    }
  }

  add(form.attendanceScore, form.attendanceRatio)
  add(form.homeworkScore, form.homeworkRatio)
  add(form.experimentScore, form.experimentRatio)
  add(form.midtermScore, form.midtermRatio)
  add(form.dailyScore, form.dailyRatio)
  add(form.finalScore, form.finalRatio)

  form.totalScore = Math.round(total * 10) / 10
}

// -------------------------------------------------------------
// Watch 定义在 calculateTotal 之后
// -------------------------------------------------------------
watch(() => form.examType, (newVal) => {
  if (newVal === '补考') {
    rules.makeupScore = [{ required: true, message: '请输入补考成绩', trigger: 'blur' }]
    rules.finalScore = []
  } else {
    delete rules.makeupScore
    rules.finalScore = [{ required: true, message: '请输入期末成绩', trigger: 'blur' }]
    form.makeupScore = null
  }
  calculateTotal() // 这里调用就不会报错了
}, { immediate: true })

onMounted(async () => {
  await loadTeacherCourses()
})

const loadTeacherCourses = async () => {
  courseLoading.value = true
  try {
    const res = await request.get('/remote/client/teacher/courses')
    if (res.code === 200 && res.data) {
      courses.value = res.data
    }
  } catch (error) {
    ElMessage.error('获取课程失败')
  } finally {
    courseLoading.value = false
  }
}

const handleSubmit = async () => await submitGrade('DRAFT')
const handleSubmitAndCommit = async () => await submitGrade('SUBMITTED')

const submitGrade = async (status) => {
  if (form.examType === '正考' && totalRatio.value !== 100) {
    ElMessage.warning('各项成绩比例之和必须为 100%')
    return
  }

  try {
    await formRef.value.validate()
    loading.value = true

    // 构建提交数据
    const submitData = {
      studentId: form.studentId,
      courseId: form.courseId,
      semester: form.semester,
      examType: form.examType,
      status: status,
      totalScore: form.totalScore !== null ? form.totalScore.toString() : null
    }

    const addField = (key, val) => { if (val !== null) submitData[key] = val.toString() }

    if (form.examType === '补考') {
      addField('makeupScore', form.makeupScore)
    } else {
      addField('attendanceScore', form.attendanceScore)
      addField('homeworkScore', form.homeworkScore)
      addField('experimentScore', form.experimentScore)
      addField('midtermScore', form.midtermScore)
      addField('dailyScore', form.dailyScore)
      addField('finalScore', form.finalScore)
    }

    const res = await request.post('/remote/client/grade/entry', submitData)

    if (res.code === 200) {
      ElMessage.success(status === 'DRAFT' ? '暂存成功' : '提交成功')
      handleReset()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error('请求异常: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  formRef.value.resetFields()
  form.attendanceRatio = 10
  form.homeworkRatio = 20
  form.experimentRatio = 0
  form.midtermRatio = 0
  form.dailyRatio = 0
  form.finalRatio = 70
  form.examType = '正考'

  form.attendanceScore = null
  form.homeworkScore = null
  form.experimentScore = null
  form.midtermScore = null
  form.dailyScore = null
  form.finalScore = null
  form.makeupScore = null
  calculateTotal()
}
</script>

<style scoped>
/* 样式保持不变 */
.score-entry { max-width: 900px; margin: 0 auto; padding-bottom: 40px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 5px; color: #303133; }
.sub-title { color: #909399; font-size: 14px; margin: 0; }

.score-input-area {
  background-color: #f8faff;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.score-item-card {
  background: white;
  border-radius: 6px;
  padding: 10px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s;
}

.score-item-card.disabled {
  background: #f5f7fa;
  opacity: 0.7;
}

.score-item-card.highlight {
  border-color: #b3d8ff;
  background: #ecf5ff;
}

.score-item-card .label {
  font-weight: bold;
  color: #606266;
  margin-bottom: 8px;
  font-size: 13px;
}

.score-item-card .inputs {
  display: flex;
  align-items: center;
  gap: 5px;
}

.symbol { color: #909399; font-size: 12px; }

.makeup-area {
  padding: 10px;
  background-color: #fff;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}

.total-score-bar {
  background: linear-gradient(135deg, #1890ff 0%, #36cfc9 100%);
  color: white;
  padding: 15px 25px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 10px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

.total-score-bar .label { font-size: 16px; margin-right: 10px; }
.total-score-bar .value { font-size: 32px; font-weight: bold; }
.total-score-bar .unit { font-size: 14px; margin-left: 5px; opacity: 0.8; margin-top: 10px;}

.form-actions { display: flex; justify-content: flex-end; gap: 15px; margin-top: 30px; }
</style>