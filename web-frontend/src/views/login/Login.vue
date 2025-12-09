<template>
  <div class="login-container">
    <div class="circle circle-1"></div>
    <div class="circle circle-2"></div>

    <div class="login-content">
      <div class="login-header">
        <img src="@/assets/logo.svg" alt="Logo" class="logo" />
        <h2 class="title">长安大学成绩管理系统</h2>
        <p class="subtitle">CHD Grade Management System</p>
      </div>

      <el-card class="login-card">
        <el-form :model="form" :rules="rules" ref="loginForm" class="login-form" size="large">
          <el-form-item prop="username">
            <el-input
                v-model="form.username"
                placeholder="请输入学号/工号"
                :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                show-password
                :prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item prop="role">
            <el-select v-model="form.role" placeholder="请选择您的身份" style="width: 100%">
              <el-option label="👨‍🎓 学生" value="STUDENT" />
              <el-option label="👩‍🏫 教师" value="TEACHER" />
              <el-option label="🔧 管理员" value="ADMIN" />
            </el-select>
          </el-form-item>

          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            立即登录
          </el-button>
        </el-form>
      </el-card>

      <div class="login-footer">
        © 2025 Chang'an University | Excellence in Education
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { loginApi } from '@/api/user'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue' // 确保你安装了图标库

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
    const conditions = {}
    if (form.role === 'STUDENT') conditions.student_id = form.username
    else if (form.role === 'TEACHER') conditions.teacher_id = form.username
    else if (form.role === 'ADMIN') conditions.admin_id = form.username

    const loginDTO = {
      operation: 'SELECT',
      table: form.role === 'STUDENT' ? 'students' : form.role === 'TEACHER' ? 'teachers' : 'admins',
      conditions: conditions,
      data: { password: form.password, role: form.role },
      role: form.role
    }

    const { data: token } = await loginApi(loginDTO)
    userStore.setToken(token)

    const routes = {
      STUDENT: '/student/grades',
      TEACHER: '/teacher/entry',
      ADMIN: '/admin/management'
    }
    ElMessage.success('登录成功，欢迎回来！')
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
  position: relative;
  width: 100vw;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  /* 更高级的渐变色 */
  background: linear-gradient(120deg, #e0c3fc 0%, #8ec5fc 100%);
  overflow: hidden;
}

/* 装饰背景圆 */
.circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  z-index: 1;
  animation: float 6s ease-in-out infinite;
}
.circle-1 { width: 300px; height: 300px; top: -50px; left: -50px; background: linear-gradient(#1890ff, #764ba2); opacity: 0.4; }
.circle-2 { width: 400px; height: 400px; bottom: -100px; right: -100px; background: linear-gradient(#4facfe, #00f2fe); opacity: 0.4; animation-delay: -3s; }

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(20px); }
}

.login-content {
  position: relative;
  z-index: 2;
  text-align: center;
  width: 420px;
}

.login-header {
  margin-bottom: 30px;
  animation: slideDown 0.8s ease-out;
}

.logo { width: 60px; height: 60px; margin-bottom: 10px; }
.title { color: #fff; font-size: 28px; font-weight: 700; text-shadow: 0 2px 4px rgba(0,0,0,0.1); margin: 0; }
.subtitle { color: rgba(255,255,255,0.9); font-size: 14px; margin-top: 5px; letter-spacing: 1px; }

.login-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  padding: 20px 10px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  animation: fadeInUp 0.8s ease-out;
}

.login-btn {
  width: 100%;
  padding: 20px 0;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
  margin-top: 10px;
  background: linear-gradient(to right, #4facfe 0%, #00f2fe 100%);
  border: none;
  transition: all 0.3s;
}

.login-btn:hover {
  transform: scale(1.02);
  box-shadow: 0 4px 15px rgba(0, 242, 254, 0.4);
}

.login-footer {
  margin-top: 20px;
  color: #fff;
  font-size: 12px;
  opacity: 0.7;
}

/* 动画定义 */
@keyframes slideDown {
  from { transform: translateY(-30px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
@keyframes fadeInUp {
  from { transform: translateY(30px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
</style>