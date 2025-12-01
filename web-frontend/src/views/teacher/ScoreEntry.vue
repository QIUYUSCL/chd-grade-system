<template>
  <div class="score-entry">
    <h2>成绩录入</h2>
    <el-card>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="140px">

        <!-- 学期选择 -->
        <el-form-item label="学期" prop="semester">
          <el-select v-model="form.semester" placeholder="请选择学期">
            <el-option label="2024-2025-1" value="2024-2025-1" />
            <el-option label="2024-2025-2" value="2024-2025-2" />
          </el-select>
        </el-form-item>

        <!-- 课程选择（动态加载） -->
        <el-form-item label="课程" prop="courseId">
          <el-select
              v-model="form.courseId"
              placeholder="请选择课程"
              :loading="courseLoading"
              :disabled="courseLoading || courses.length === 0">
            <el-option
                v-for="course in courses"
                :key="course.course_id"
                :label="course.course_name + ' (' + course.course_id + ')'"
                :value="course.course_id" />
          </el-select>
          <span v-if="courses.length === 0 && !courseLoading" style="color: #909399; font-size: 12px;">
            暂无任课课程
          </span>
        </el-form-item>

        <!-- 学生学号 -->
        <el-form-item label="学生学号" prop="studentId">
          <el-input v-model="form.studentId" placeholder="请输入学号" />
        </el-form-item>

        <!-- 考试类型 -->
        <el-form-item label="考试类型" prop="examType">
          <el-radio-group v-model="form.examType">
            <el-radio label="正考">正考</el-radio>
            <el-radio label="补考">补考</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 成绩分项录入 -->
        <el-divider content-position="left">成绩分项（根据系数自动计算总分）</el-divider>

        <el-form-item label="平时成绩占比(%)" prop="dailyRatio">
          <el-input-number v-model="form.dailyRatio" :min="0" :max="100" @change="calculateTotal" />
        </el-form-item>

        <el-form-item label="平时成绩" prop="dailyScore">
          <el-input-number v-model="form.dailyScore" :min="0" :max="100" :precision="1" @change="calculateTotal" />
        </el-form-item>

        <el-form-item label="期末成绩占比(%)" prop="finalRatio">
          <el-input-number v-model="form.finalRatio" :min="0" :max="100" @change="calculateTotal" />
        </el-form-item>

        <el-form-item label="期末成绩" prop="finalScore">
          <el-input-number v-model="form.finalScore" :min="0" :max="100" :precision="1" @change="calculateTotal" />
        </el-form-item>

        <!-- 补考成绩（仅补考时显示） -->
        <el-form-item label="补考成绩" prop="makeupScore" v-if="form.examType === '补考'">
          <el-input-number v-model="form.makeupScore" :min="0" :max="100" :precision="1" />
        </el-form-item>

        <!-- 总成绩（自动计算，只读） -->
        <el-form-item label="总成绩" prop="totalScore">
          <el-input-number v-model="form.totalScore" :min="0" :max="100" :precision="1" :disabled="true" />
          <span style="color: #909399; margin-left: 10px;">根据成绩系数自动计算</span>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="loading">暂存</el-button>
          <el-button type="success" @click="handleSubmitAndCommit" :loading="loading">提交</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>

      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
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

// 页面加载时获取课程列表
onMounted(async () => {
  await loadTeacherCourses()
})

// 加载教师课程列表
const loadTeacherCourses = async () => {
  courseLoading.value = true
  try {
    const res = await request.get('/remote/client/teacher/courses')
    if (res.code === 200 && res.data) {
      courses.value = res.data
      if (courses.value.length === 0) {
        ElMessage.warning('您暂未分配任课课程，请联系管理员')
      }
      console.log('加载到课程列表:', courses.value)
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

// 监听考试类型变化，动态调整校验规则
watch(() => form.examType, (newVal) => {
  if (newVal === '补考') {
    rules.makeupScore = [{ required: true, message: '请输入补考成绩', trigger: 'blur' }]
  } else {
    delete rules.makeupScore
    form.makeupScore = null
  }
}, { immediate: true })

// 计算总成绩
const calculateTotal = () => {
  if (form.dailyScore !== null && form.finalScore !== null) {
    const total = (form.dailyScore * form.dailyRatio / 100) +
        (form.finalScore * form.finalRatio / 100)
    form.totalScore = Math.round(total * 10) / 10 // 保留1位小数
  }
}

// 暂存（DRAFT状态）
const handleSubmit = async () => {
  await submitGrade('DRAFT')
}

// 提交（SUBMITTED状态）
const handleSubmitAndCommit = async () => {
  await submitGrade('SUBMITTED')
}

const submitGrade = async (status) => {
  try {
    await formRef.value.validate()
    loading.value = true

    // 构建提交数据
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

    // 如果是补考，添加补考成绩
    if (form.examType === '补考' && form.makeupScore !== null) {
      submitData.makeupScore = form.makeupScore.toString()
    }

    console.log('提交的数据:', submitData)

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
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

:deep(.el-divider__text) {
  font-weight: bold;
  color: #409eff;
}

/* 加载状态样式 */
:deep(.el-select__loading) {
  color: #409eff;
}
</style>