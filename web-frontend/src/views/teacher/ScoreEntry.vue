<template>
  <div class="score-entry">
    <h2>成绩录入</h2>
    <el-card>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="form.courseName" placeholder="请输入课程名称" />
        </el-form-item>
        <el-form-item label="学生学号" prop="studentId">
          <el-input v-model="form.studentId" placeholder="请输入学生学号" />
        </el-form-item>
        <el-form-item label="成绩" prop="score">
          <el-input v-model.number="form.score" type="number" placeholder="请输入成绩" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">提交</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'

const formRef = ref()
const form = reactive({
  courseName: '',
  studentId: '',
  score: ''
})

const rules = {
  courseName: [
    { required: true, message: '请输入课程名称', trigger: 'blur' }
  ],
  studentId: [
    { required: true, message: '请输入学生学号', trigger: 'blur' }
  ],
  score: [
    { required: true, message: '请输入成绩', trigger: 'blur' },
    { type: 'number', min: 0, max: 100, message: '成绩必须在0-100之间', trigger: 'blur' }
  ]
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
    // 这里可以调用API提交成绩
    ElMessage.success('成绩录入成功')
    handleReset()
  } catch (error) {
    ElMessage.error('请检查输入信息')
  }
}

const handleReset = () => {
  formRef.value.resetFields()
}
</script>

<style scoped>
.score-entry {
  h2 {
    margin-bottom: 20px;
    color: #303133;
  }
}
</style>
