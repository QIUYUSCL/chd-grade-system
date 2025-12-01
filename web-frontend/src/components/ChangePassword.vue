<template>
  <el-dialog
      v-model="dialogVisible"
      title="修改密码"
      width="400px"
      @close="handleClose"
  >
    <el-form
        :model="form"
        :rules="rules"
        ref="passwordForm"
        label-width="100px"
    >
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input
            v-model="form.oldPassword"
            type="password"
            placeholder="请输入旧密码"
            show-password
        />
      </el-form-item>

      <el-form-item label="新密码" prop="newPassword">
        <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
            :validate-event="false"
        />
      </el-form-item>

      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
            :validate-event="false"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading">确认修改</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { changePassword } from '@/api/user.js'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user.js'  // 新增：直接操作store
import { useRouter } from 'vue-router'          // 新增：直接跳转

// 引用 Store 和 Router
const userStore = useUserStore()
const router = useRouter()

// Props & Emits
const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})
const emit = defineEmits(['update:visible'])

// 响应式数据
const loading = ref(false)
const passwordForm = ref()

// 计算属性：对话框显隐
const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// 表单数据
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 表单验证规则
const rules = {
  oldPassword: [
    { required: true, message: '请输入旧密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为6位', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value && form.oldPassword && value === form.oldPassword) {
          callback(new Error('新密码不能与旧密码相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value && value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 重置表单
const resetForm = () => {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  if (passwordForm.value) {
    passwordForm.value.clearValidate()
  }
}

// 关闭对话框
const handleClose = () => {
  resetForm()
  emit('update:visible', false)
}

// 提交表单（核心修复）
const handleSubmit = async () => {
  try {
    await passwordForm.value.validate()
    loading.value = true

    // 调用修改密码API
    await changePassword(form.oldPassword, form.newPassword)

    ElMessage.success('密码修改成功，请重新登录')

    // ===== 核心修复：直接在组件内处理登出，不依赖父组件事件 =====
    userStore.clearUser()          // 1. 清除本地存储和状态
    await router.push('/login')    // 2. 强制跳转到登录页（await确保完成）

    // 可选：通知父组件关闭对话框（防止内存泄漏）
    emit('update:visible', false)

  } catch (error) {
    ElMessage.error(error.message || '密码修改失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>