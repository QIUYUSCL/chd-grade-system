<template>
  <div class="common-layout">
    <!-- 侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>{{ roleText }}管理</h3>
      </div>
      <div class="sidebar-menu">
        <el-menu
            :default-active="activeMenu"
            class="el-menu-vertical"
            router
            background-color="#001529"
            text-color="#fff"
            active-text-color="#1890ff"
        >
          <!-- 动态菜单项 -->
          <template v-for="menu in menuItems" :key="menu.index">
            <el-menu-item :index="menu.index">
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.label }}</span>
            </el-menu-item>
          </template>
          <el-menu-item index="/profile">
            <el-icon><User /></el-icon>
            <span>个人信息</span>
          </el-menu-item>
        </el-menu>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 顶部导航 -->
      <div class="header">
        <div class="header-left">
          <span class="system-name">长安大学成绩管理系统</span>
        </div>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              <el-avatar size="small" :src="userAvatar">{{ userInitial }}</el-avatar>
              <span>{{ userStore.userInfo.userId }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="showChangePassword = true">
                  <el-icon><Key /></el-icon>
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- 页面内容 -->
      <div class="content-wrapper">
        <router-view />
      </div>
    </div>

    <!-- 修改密码对话框 -->
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
import { User, Key, SwitchButton, View, Edit, Setting } from '@element-plus/icons-vue'
import ChangePassword from '@/components/ChangePassword.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 状态管理
const showChangePassword = ref(false)

// 计算属性
const activeMenu = computed(() => route.path)
const userInfo = computed(() => userStore.userInfo)
const role = computed(() => userInfo.value.role || '')

// 角色文本
const roleText = computed(() => {
  const roleMap = {
    'STUDENT': '学生',
    'TEACHER': '教师',
    'ADMIN': '管理员'
  }
  return roleMap[role.value] || '未知'
})

// 获取用户头像
const userAvatar = ref('')

// 获取用户首字母
const userInitial = computed(() => {
  const userId = userStore.userInfo.userId
  return userId ? userId.charAt(0).toUpperCase() : 'U'
})

// 根据角色动态生成菜单项
const menuItems = computed(() => {
  const roleMenus = {
    'STUDENT': [
      { index: '/student/grades', label: '成绩查询', icon: View }
    ],
    'TEACHER': [
      { index: '/teacher/entry', label: '成绩录入', icon: Edit },
      { index: '/teacher/grade-view', label: '成绩查询', icon: View }
    ],
    'ADMIN': [
      { index: '/admin/management', label: '系统管理', icon: Setting }
    ]
  }
  return roleMenus[role.value] || []
})

// 处理退出登录
const handleLogout = () => {
  userStore.clearUser()
  ElMessage.success('退出登录成功')
  router.push('/login')
}

// 处理密码修改成功
const handlePasswordChangeSuccess = () => {
  userStore.clearUser()
  router.push('/login')
}

// 检查登录状态
onMounted(() => {
  if (!userStore.isAuthenticated()) {
    router.push('/login')
  }
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

/* 侧边栏样式 */
.sidebar {
  width: 240px;
  background-color: #001529;
  color: white;
  height: 100vh;
  flex-shrink: 0;
  overflow-y: auto;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #1890ff;
}

.sidebar-header h3 {
  margin: 0;
  color: white;
  font-size: 18px;
}

.sidebar-menu {
  padding-top: 10px;
}

/* 主内容区样式 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 64px;
  background-color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  flex-shrink: 0;
}

.system-name {
  font-size: 18px;
  font-weight: bold;
  color: #1890ff;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: #f5f5f5;
}

.content-wrapper {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background-color: #f0f2f5;
}
</style>