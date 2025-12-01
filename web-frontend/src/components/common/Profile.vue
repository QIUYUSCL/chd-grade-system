<template>
  <div class="profile-view">
    <h2>个人信息</h2>
    <el-card>
      <el-descriptions border :column="1">
        <el-descriptions-item label="账号">{{ userInfo.userId }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ roleText }}</el-descriptions-item>
        <el-descriptions-item label="操作">
          <el-button type="primary" @click="showChangePassword = true">修改密码</el-button>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <ChangePassword
        :visible="showChangePassword"
        @update:visible="showChangePassword = $event"
        @success="handlePasswordChangeSuccess"
    />

  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useUserStore } from '@/store/user'
import ChangePassword from '@/components/ChangePassword.vue'

const userStore = useUserStore()
const showChangePassword = ref(false)

// 用户信息
const userInfo = computed(() => userStore.userInfo)

// 角色文本
const roleText = computed(() => {
  const roleMap = {
    'STUDENT': '学生',
    'TEACHER': '教师',
    'ADMIN': '管理员'
  }
  return roleMap[userInfo.value.role] || '未知'
})
</script>

<style scoped>
.profile-view {
  h2 {
    margin-bottom: 20px;
    color: #303133;
  }
}
</style>