<template>
  <div class="profile-view">
    <el-row :gutter="20" justify="center">
      <el-col :xs="24" :sm="18" :md="12" :lg="10">
        <el-card class="profile-card" :body-style="{ padding: '0px' }">
          <div class="profile-header">
            <div class="header-bg"></div>
            <div class="avatar-wrapper">
              <el-avatar :size="80" class="user-avatar">
                {{ userInfo.userId ? userInfo.userId.charAt(0).toUpperCase() : 'U' }}
              </el-avatar>
            </div>
          </div>

          <div class="profile-body">
            <h2 class="username">你好，{{ userInfo.userId }}</h2>
            <p class="role-tag">{{ roleText }}</p>

            <el-divider />

            <el-descriptions :column="1" border class="info-list">
              <el-descriptions-item label="账号ID">
                <el-icon><User /></el-icon> {{ userInfo.userId }}
              </el-descriptions-item>
              <el-descriptions-item label="所属角色">
                <el-tag size="small" :type="roleTagType">{{ roleText }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="账号状态">
                <el-tag size="small" type="success" effect="plain">正常启用</el-tag>
              </el-descriptions-item>
            </el-descriptions>

            <div class="action-footer">
              <el-button type="primary" round icon="Edit" @click="showChangePassword = true">
                修改登录密码
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

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
import { User, Edit } from '@element-plus/icons-vue' // 需确保引入图标
import ChangePassword from '@/components/ChangePassword.vue'

const userStore = useUserStore()
const showChangePassword = ref(false)

const userInfo = computed(() => userStore.userInfo)

const roleText = computed(() => {
  const roleMap = { 'STUDENT': '学生', 'TEACHER': '教师', 'ADMIN': '管理员' }
  return roleMap[userInfo.value.role] || '未知用户'
})

const roleTagType = computed(() => {
  const map = { 'STUDENT': '', 'TEACHER': 'warning', 'ADMIN': 'danger' }
  return map[userInfo.value.role] || 'info'
})

const handlePasswordChangeSuccess = () => {
  // 逻辑保持不变
}
</script>

<style scoped>
.profile-view {
  padding: 40px 20px;
}

.profile-card {
  border: none;
  overflow: visible; /* 让头像溢出 */
}

.header-bg {
  height: 120px;
  background: linear-gradient(135deg, #1890ff 0%, #36cfc9 100%);
  border-radius: 12px 12px 0 0;
}

.avatar-wrapper {
  position: relative;
  margin-top: -40px;
  text-align: center;
}

.user-avatar {
  background-color: #fff;
  color: #1890ff;
  border: 4px solid #fff;
  box-shadow: 0 4px 10px rgba(0,0,0,0.1);
  font-size: 32px;
  font-weight: bold;
}

.profile-body {
  padding: 20px 30px 40px;
  text-align: center;
}

.username {
  margin: 10px 0 5px;
  font-size: 22px;
  color: #303133;
}

.role-tag {
  color: #909399;
  font-size: 14px;
  margin-bottom: 20px;
}

.info-list {
  text-align: left;
  margin: 20px 0;
}

:deep(.el-descriptions__label) {
  width: 100px;
  font-weight: bold;
}

.action-footer {
  margin-top: 30px;
}
</style>