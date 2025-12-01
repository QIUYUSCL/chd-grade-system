<template>
  <div class="user-list" v-if="userStore.isAdmin">
    <h2>用户管理 - 用户列表</h2>

    <!-- 查询条件 -->
    <el-form :inline="true" :model="queryForm" class="query-form">
      <el-form-item label="角色">
        <el-select v-model="queryForm.role" placeholder="请选择角色" clearable>
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model="queryForm.userId" placeholder="输入用户ID搜索" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch" :loading="loading">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 用户表格 -->
    <el-table :data="userList" border stripe v-loading="loading" class="user-table">
      <el-table-column prop="student_id" label="用户ID" width="120" />
      <el-table-column prop="name" label="姓名" width="150" />
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column prop="class_name" label="班级/部门" width="150" />
      <el-table-column label="操作" width="150" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
              type="primary"
              size="small"
              @click="handleModifyPassword(row)"
              icon="Lock"
          >
            修改密码
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
        v-if="total > 0"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
        class="pagination"
    />
  </div>

  <div v-else>
    <el-alert type="error" title="权限不足：仅管理员可访问此页面" show-icon />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/store/user.js';
import { ElMessage } from 'element-plus';
import { getUserList } from '@/api/admin.js';

const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
const userList = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);

const queryForm = reactive({
  role: '',
  userId: ''
});

// 查询用户列表
const handleSearch = async () => {
  loading.value = true;
  try {
    const response = await getUserList(
        queryForm.role || 'STUDENT',
        currentPage.value,
        pageSize.value,
        queryForm.userId
    );

    userList.value = response.data || [];
    total.value = response.total || 0;
  } catch (error) {
    ElMessage.error('查询失败：' + (error.message || '未知错误'));
  } finally {
    loading.value = false;
  }
};

// 重置查询条件
const handleReset = () => {
  queryForm.role = '';
  queryForm.userId = '';
  currentPage.value = 1;
  handleSearch();
};

// 跳转到密码修改页
const handleModifyPassword = (row) => {
  const userId = row.student_id || row.teacher_id;
  const role = row.role || (userId.startsWith('T') ? 'TEACHER' : 'STUDENT');

  router.push({
    path: '/admin/password-reset',
    query: {
      userId: userId,
      name: row.name,
      role: role
    }
  });
};

// 页面加载时查询
onMounted(() => {
  handleSearch();
});
</script>

<style scoped>
.user-list {
  padding: 20px;
}

.query-form {
  margin-bottom: 20px;
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.user-table {
  margin-bottom: 20px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

h2 {
  margin-bottom: 20px;
  color: #303133;
}
</style>