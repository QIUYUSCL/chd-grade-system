<template>
  <div class="common-layout">
    <div class="sidebar">
      <div class="sidebar-header">
        <img src="@/assets/logo.svg" class="sidebar-logo" v-if="false" /> <h3 class="app-title">CHD Score</h3>
      </div>
      <div class="sidebar-menu">
        <el-menu
            :default-active="activeMenu"
            class="el-menu-vertical"
            router
            background-color="#001529"
            text-color="rgba(255, 255, 255, 0.65)"
            active-text-color="#fff"
        >
          <template v-for="menu in menuItems" :key="menu.index">
            <el-menu-item :index="menu.index">
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.label }}</span>
            </el-menu-item>
          </template>
          <el-menu-item index="/profile">
            <el-icon><User /></el-icon>
            <span>个人中心</span>
          </el-menu-item>
        </el-menu>
      </div>
    </div>

    <div class="main-content">
      <div class="header">
        <div class="header-left">
          <span class="role-badge">{{ roleText }}端</span>
          <span class="system-name">长安大学成绩管理系统</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <div class="user-info-card">
              <el-avatar class="user-avatar" :size="32" :src="userAvatar" style="background: #1890ff">
                {{ userInitial }}
              </el-avatar>
              <span class="user-name">{{ userStore.userInfo.userId }}</span>
              <el-icon><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu class="user-dropdown">
                <el-dropdown-item @click="showChangePassword = true">
                  <el-icon><Key /></el-icon>修改密码
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout" style="color: #ff4d4f">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div class="content-wrapper">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>

    <ChangePassword
        :visible="showChangePassword"
        @update:visible="showChangePassword = $event"
        @success="handlePasswordChangeSuccess"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import { User, Key, SwitchButton, View, Edit, Setting, CaretBottom } from '@element-plus/icons-vue'
import ChangePassword from '@/components/ChangePassword.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const showChangePassword = ref(false)

const activeMenu = computed(() => route.path)
const userInfo = computed(() => userStore.userInfo)
const role = computed(() => userInfo.value.role || '')

const roleText = computed(() => {
  const roleMap = { 'STUDENT': '学生', 'TEACHER': '教师', 'ADMIN': '管理' }
  return roleMap[role.value] || '未知'
})

const userAvatar = ref('')
const userInitial = computed(() => {
  const userId = userStore.userInfo.userId
  return userId ? userId.charAt(0).toUpperCase() : 'U'
})

const menuItems = computed(() => {
  const roleMenus = {
    'STUDENT': [{ index: '/student/grades', label: '我的成绩', icon: View }],
    'TEACHER': [
      { index: '/teacher/entry', label: '成绩录入', icon: Edit },
      { index: '/teacher/grade-view', label: '成绩管理', icon: View }
    ],
    'ADMIN': [{ index: '/admin/management', label: '系统概览', icon: Setting }, { index: '/admin/users', label: '用户管理', icon: User }]
  }
  return roleMenus[role.value] || []
})

const handleLogout = () => {
  userStore.clearUser()
  ElMessage.success('已安全退出')
  router.push('/login')
}

const handlePasswordChangeSuccess = () => {
  userStore.clearUser()
  router.push('/login')
}

onMounted(() => {
  if (!userStore.isAuthenticated()) router.push('/login')
})
</script>

<style scoped>
.common-layout {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: #f0f2f5;
  overflow: hidden;
}

/* 侧边栏优化 */
.sidebar {
  width: 220px;
  background: #001529;
  box-shadow: 2px 0 6px rgba(0, 21, 41, 0.35);
  display: flex;
  flex-direction: column;
  z-index: 10;
}

.sidebar-header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #002140;
}

.app-title {
  color: white;
  font-size: 20px;
  font-weight: bold;
  letter-spacing: 1px;
}

.el-menu-vertical {
  border-right: none;
}

/* 选中菜单项的高亮样式 */
:deep(.el-menu-item.is-active) {
  background-color: #1890ff !important;
  color: #fff !important;
}

/* 头部优化 */
.header {
  height: 64px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  z-index: 9;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-badge {
  background: #e6f7ff;
  color: #1890ff;
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  border: 1px solid #91d5ff;
}

.system-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.user-info-card {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 20px;
  transition: all 0.3s;
}
.user-info-card:hover {
  background: #f5f5f5;
}

.user-name {
  font-weight: 500;
  color: #555;
}

/* 内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: #f0f2f5;
}

.content-wrapper {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

/* 路由切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>