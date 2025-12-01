<template>
  <div class="login-container">
    <el-form :model="form" :rules="rules" ref="loginForm" class="login-form">
      <h3 class="title">长安大学成绩管理系统</h3>

      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="学号/工号" />
      </el-form-item>

      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="密码" show-password />
      </el-form-item>

      <el-form-item prop="role">
        <el-select v-model="form.role" placeholder="选择角色" style="width: 100%">
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
      </el-form-item>

      <el-button type="primary" style="width: 100%" :loading="loading" @click="handleLogin">
        登录
      </el-button>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { loginApi } from '@/api/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loginForm = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  role: ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const handleLogin = async () => {
  await loginForm.value.validate()
  loading.value = true

  try {
    // 根据角色动态构建 conditions
    const conditions = {}
    if (form.role === 'STUDENT') {
      conditions.student_id = form.username
    } else if (form.role === 'TEACHER') {
      conditions.teacher_id = form.username
    } else if (form.role === 'ADMIN') {
      conditions.admin_id = form.username
    }

    const loginDTO = {
      operation: 'SELECT',
      table: form.role === 'STUDENT' ? 'students' : form.role === 'TEACHER' ? 'teachers' : 'admins',
      conditions: conditions,
      data: {
        password: form.password,
        role: form.role
      },
      role: form.role
    }

    const { data: token } = await loginApi(loginDTO)

    userStore.setToken(token)
    userStore.setUserInfo({
      userId: form.username,
      role: form.role
    })

    const routes = {
      STUDENT: '/student/grades',
      TEACHER: '/teacher/entry',
      ADMIN: '/admin/management'
    }
    router.push(routes[form.role])

  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-form {
  width: 400px;
  max-width: 90%;
  padding: 40px 30px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.title {
  text-align: center;
  margin-bottom: 30px;
  color: #303133;
  font-size: 24px;
  font-weight: bold;
}
</style>