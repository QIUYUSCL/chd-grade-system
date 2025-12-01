<template>
  <div class="management">
    <h2>系统管理</h2>
    <el-card>
      <p>欢迎使用管理员功能</p>
      <el-button type="primary" @click="goToUserManagement">用户管理</el-button>
      <el-button type="primary">课程管理</el-button>
      <el-button type="primary">数据统计</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';

const router = useRouter();

// 跳转到用户管理列表
const goToUserManagement = () => {
  router.push('/admin/users');
};
</script>

<style scoped>
.management {
  padding: 20px;
}

h2 {
  margin-bottom: 20px;
  color: #303133;
}

.el-button {
  margin-right: 10px;
  margin-top: 10px;
}
</style>