<template>
  <div class="management">
    <h2>系统管理</h2>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="management-card" @click="goToGradeManagement">
          <div class="card-content">
            <el-icon class="card-icon"><Document /></el-icon>
            <h3>成绩录入管理</h3>
            <p>管理教师提交的成绩，支持小撤销和大撤销操作</p>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="management-card" @click="goToUserManagement">
          <div class="card-content">
            <el-icon class="card-icon"><User /></el-icon>
            <h3>人员组织库维护</h3>
            <p>管理学生、教师和管理员信息，分配权限</p>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="management-card" @click="goToCourseManagement">
          <div class="card-content">
            <el-icon class="card-icon"><Reading /></el-icon>
            <h3>课程管理</h3>
            <p>管理课程信息，分配任课教师，维护选课关系</p>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card class="management-card" @click="goToSecurityManagement">
          <div class="card-content">
            <el-icon class="card-icon"><Lock /></el-icon>
            <h3>安全管理</h3>
            <p>数据完整性校验，安全审计日志查看</p>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card>
          <template #header>
            <span>系统概览</span>
          </template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="当前用户">管理员</el-descriptions-item>
            <el-descriptions-item label="登录时间">{{ new Date().toLocaleString() }}</el-descriptions-item>
            <el-descriptions-item label="系统版本">v1.0.0</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';
import { Document, User, Lock, Reading } from '@element-plus/icons-vue';

const router = useRouter();

// 跳转到成绩录入管理
const goToGradeManagement = () => {
  router.push('/admin/grade-management');
};

// 跳转到用户管理列表
const goToUserManagement = () => {
  router.push('/admin/user-management');
};

// 跳转到课程管理
const goToCourseManagement = () => {
  router.push('/admin/course-management');
};

// 跳转到安全管理
const goToSecurityManagement = () => {
  router.push('/admin/security');
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

.management-card {
  cursor: pointer;
  transition: all 0.3s ease;
  height: 200px;
}

.management-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.card-content {
  text-align: center;
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.card-icon {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 15px;
}

.card-content h3 {
  margin: 10px 0;
  color: #303133;
  font-size: 18px;
}

.card-content p {
  color: #606266;
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}

.el-descriptions {
  margin-top: 10px;
}
</style>