<template>
  <div class="score-entry">
    <div class="page-header">
      <h2>成绩录入</h2>
      <p class="sub-title">请准确录入各项成绩，系统将自动计算总评。</p>
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
          <el-radio-group v-model="form.examType" fill="#1890ff">
            <el-radio-button label="正考">正考录入</el-radio-button>
            <el-radio-button label="补考">补考录入</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left"><el-icon><EditPen /></el-icon> 成绩分项录入</el-divider>

        <div class="score-input-area">
          <el-row :gutter="40">
            <el-col :span="12">
              <div class="score-group">
                <div class="group-title">平时成绩 ({{ form.dailyRatio }}%)</div>
                <el-row :gutter="10">
                  <el-col :span="12">
                    <el-form-item label="占比(%)" prop="dailyRatio">
                      <el-input-number v-model="form.dailyRatio" :min="0" :max="100" controls-position="right" @change="calculateTotal" style="width: 100%"/>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="得分" prop="dailyScore">
                      <el-input-number v-model="form.dailyScore" :min="0" :max="100" :precision="1" controls-position="right" @change="calculateTotal" style="width: 100%"/>
                    </el-form-item>
                  </el-col>
                </el-row>
              </div>
            </el-col>

            <el-col :span="12">
              <div class="score-group">
                <div class="group-title">期末成绩 ({{ form.finalRatio }}%)</div>
                <el-row :gutter="10">
                  <el-col :span="12">
                    <el-form-item label="占比(%)" prop="finalRatio">
                      <el-input-number v-model="form.finalRatio" :min="0" :max="100" controls-position="right" @change="calculateTotal" style="width: 100%"/>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="得分" prop="finalScore">
                      <el-input-number v-model="form.finalScore" :min="0" :max="100" :precision="1" controls-position="right" @change="calculateTotal" style="width: 100%"/>
                    </el-form-item>
                  </el-col>
                </el-row>
              </div>
            </el-col>
          </el-row>

          <transition name="el-zoom-in-top">
            <div v-if="form.examType === '补考'" class="makeup-area">
              <el-form-item label="补考卷面成绩" prop="makeupScore">
                <el-input-number v-model="form.makeupScore" :min="0" :max="100" :precision="1" style="width: 200px"/>
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
          <el-button @click="handleReset" icon="Refresh">重置</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="loading" plain icon="Document">暂存草稿</el-button>
          <el-button type="success" @click="handleSubmitAndCommit" :loading="loading" icon="Select">提交归档</el-button>
        </div>

      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { EditPen, Refresh, Document, Select } from '@element-plus/icons-vue' // 引入图标
import request from '@/utils/request'

// ... (逻辑代码保持不变，只需确保引入了图标) ...
// 复制原来的 script 内容到这里，保留所有逻辑
const formRef = ref()
const loading = ref(false)
const courseLoading = ref(false)
const courses = ref([])

const form = reactive({
  semester: '2024-2025-1',
  courseId: '',
  studentId: '',
  examType: '正考',
  dailyRatio: 40,
  dailyScore: null,
  finalRatio: 60,
  finalScore: null,
  makeupScore: null,
  totalScore: null
})

const rules = {
  semester: [{ required: true, message: '请选择学期', trigger: 'change' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  studentId: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  examType: [{ required: true, message: '请选择考试类型', trigger: 'change' }],
  dailyScore: [{ required: true, message: '请输入平时成绩', trigger: 'blur' }],
  finalScore: [{ required: true, message: '请输入期末成绩', trigger: 'blur' }]
}

onMounted(async () => {
  await loadTeacherCourses()
})

const loadTeacherCourses = async () => {
  courseLoading.value = true
  try {
    const res = await request.get('/remote/client/teacher/courses')
    if (res.code === 200 && res.data) {
      courses.value = res.data
      if (courses.value.length === 0) {
        ElMessage.warning('您暂未分配任课课程，请联系管理员')
      }
    } else {
      ElMessage.error('获取课程列表失败: ' + (res.message || '未知错误'))
      courses.value = []
    }
  } catch (error) {
    console.error('获取课程列表异常:', error)
    ElMessage.error('获取课程列表失败: ' + error.message)
    courses.value = []
  } finally {
    courseLoading.value = false
  }
}

watch(() => form.examType, (newVal) => {
  if (newVal === '补考') {
    rules.makeupScore = [{ required: true, message: '请输入补考成绩', trigger: 'blur' }]
  } else {
    delete rules.makeupScore
    form.makeupScore = null
  }
}, { immediate: true })

const calculateTotal = () => {
  if (form.dailyScore !== null && form.finalScore !== null) {
    const total = (form.dailyScore * form.dailyRatio / 100) +
        (form.finalScore * form.finalRatio / 100)
    form.totalScore = Math.round(total * 10) / 10
  }
}

const handleSubmit = async () => {
  await submitGrade('DRAFT')
}

const handleSubmitAndCommit = async () => {
  await submitGrade('SUBMITTED')
}

const submitGrade = async (status) => {
  try {
    await formRef.value.validate()
    loading.value = true

    const submitData = {
      studentId: form.studentId,
      courseId: form.courseId,
      semester: form.semester,
      examType: form.examType,
      dailyScore: form.dailyScore.toString(),
      finalScore: form.finalScore.toString(),
      totalScore: form.totalScore.toString(),
      status: status
    }

    if (form.examType === '补考' && form.makeupScore !== null) {
      submitData.makeupScore = form.makeupScore.toString()
    }

    const res = await request.post('/remote/client/grade/entry', submitData)

    if (res.code === 200) {
      ElMessage.success(status === 'DRAFT' ? '成绩暂存成功' : '成绩提交成功')
      handleReset()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    console.error('请求异常:', error)
    ElMessage.error('请求异常: ' + error.message)
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  formRef.value.resetFields()
  form.dailyRatio = 40
  form.finalRatio = 60
  form.examType = '正考'
  form.makeupScore = null
  calculateTotal()
}
</script>

<style scoped>
.score-entry {
  max-width: 900px;
  margin: 0 auto;
  padding-bottom: 40px;
}

.page-header {
  margin-bottom: 20px;
}
.page-header h2 { margin: 0 0 5px; color: #303133; }
.sub-title { color: #909399; font-size: 14px; margin: 0; }

.score-input-area {
  background-color: #f8faff;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.score-group {
  background: #fff;
  padding: 15px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
}

.group-title {
  font-weight: bold;
  color: #1890ff;
  margin-bottom: 15px;
  font-size: 14px;
}

.makeup-area {
  margin-top: 20px;
  padding-top: 15px;
  border-top: 1px dashed #dcdfe6;
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

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  margin-top: 30px;
}
</style>