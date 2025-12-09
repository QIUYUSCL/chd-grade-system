<template>
  <el-dialog
      v-model="dialogVisible"
      title="安全中心 - 修改密码"
      width="420px"
      @close="handleClose"
      center
      destroy-on-close
  >
    <el-form
        :model="form"
        :rules="rules"
        ref="passwordForm"
        label-position="top"
        size="large"
    >
      <el-form-item label="当前密码" prop="oldPassword">
        <el-input
            v-model="form.oldPassword"
            type="password"
            placeholder="验证身份，请输入旧密码"
            show-password
            :prefix-icon="Lock"
        />
      </el-form-item>

      <el-form-item label="新密码" prop="newPassword">
        <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="设置新密码（至少6位）"
            show-password
            :prefix-icon="Key"
        />
      </el-form-item>

      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入以确认"
            show-password
            :prefix-icon="Check"
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
// 需要引入图标
import { Lock, Key, Check } from '@element-plus/icons-vue'
import { ref, reactive, computed } from 'vue'
import { changePassword } from '@/api/user.js'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user.js'
import { useRouter } from 'vue-router'

// (逻辑代码保持不变)
const userStore = useUserStore()
const router = useRouter()
const props = defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['update:visible', 'success'])
const loading = ref(false)
const passwordForm = ref()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为6位', trigger: 'blur' },
    { validator: (rule, value, callback) => {
        if (value && form.oldPassword && value === form.oldPassword) callback(new Error('新密码不能与旧密码相同'))
        else callback()
      }, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: (rule, value, callback) => {
        if (value && value !== form.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      }, trigger: 'blur' }
  ]
}

const resetForm = () => {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  if (passwordForm.value) passwordForm.value.clearValidate()
}

const handleClose = () => {
  resetForm()
  emit('update:visible', false)
}

const handleSubmit = async () => {
  try {
    await passwordForm.value.validate()
    loading.value = true
    await changePassword(form.oldPassword, form.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    userStore.clearUser()
    await router.push('/login')
    emit('update:visible', false)
  } catch (error) {
    ElMessage.error(error.message || '密码修改失败')
  } finally {
    loading.value = false
  }
}
</script>