<template>
  <div class="user-management" v-if="userStore.isAdmin">
    <h2>用户管理 - 密码重置</h2>

    <!-- 用户查询 -->
    <el-form :inline="true" :model="queryForm">
      <el-form-item label="角色">
        <el-select v-model="queryForm.role">
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户ID">
        <el-input v-model="queryForm.userId" placeholder="输入用户ID搜索" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <!-- 用户列表 -->
    <el-table :data="userList" border v-loading="loading">
      <el-table-column prop="student_id" label="用户ID" width="120" />
      <el-table-column prop="name" label="姓名" width="150" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
              type="danger"
              size="small"
              @click="handleResetPassword(row)"
          >
            重置密码
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 密码重置对话框 -->
    <el-dialog
        v-model="dialogVisible"
        title="重置用户密码"
        width="400px"
        @close="resetForm"
    >
      <el-form :model="resetForm" :rules="resetRules" ref="resetFormRef">
        <el-alert
            title="警告：此操作将重置用户密码，请谨慎操作！"
            type="warning"
            :closable="false"
            show-icon
        />
        <el-form-item label="目标用户" style="margin-top: 20px">
          <el-tag>{{ resetForm.targetUserId }} - {{ resetForm.targetUserName }}</el-tag>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
              v-model="resetForm.newPassword"
              type="password"
              show-password
              placeholder="请输入新密码"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
              v-model="resetForm.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReset" :loading="submitting">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>

  <div v-else>
    <el-alert type="error" title="权限不足：仅管理员可访问此页面" />
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/store/user.js';
import { adminResetPassword, getUserList } from '@/api/admin.js';

const userStore = useUserStore();
const loading = ref(false);
const dialogVisible = ref(false);
const submitting = ref(false);

// 查询表单
const queryForm = reactive({
  role: 'STUDENT',
  userId: ''
});

// 用户列表
const userList = ref([]);

// 重置密码表单
const resetFormRef = ref();
const resetForm = reactive({
  targetUserId: '',
  targetUserName: '',
  targetRole: '',
  newPassword: '',
  confirmPassword: ''
});

const resetRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== resetForm.newPassword) {
          callback(new Error('两次输入的密码不一致'));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ]
};

// 查询用户
const handleSearch = async () => {
  loading.value = true;
  try {
    const response = await getUserList(queryForm.role, 1, 50);
    userList.value = response.data || [];
  } catch (error) {
    ElMessage.error('查询失败：' + error.message);
  } finally {
    loading.value = false;
  }
};

// 打开重置密码对话框
const handleResetPassword = (row) => {
  resetForm.targetUserId = row.student_id || row.teacher_id;
  resetForm.targetUserName = row.name;
  resetForm.targetRole = queryForm.role;
  resetForm.newPassword = '';
  resetForm.confirmPassword = '';
  dialogVisible.value = true;
};

// 提交重置
const submitReset = async () => {
  if (!resetFormRef.value) return;

  await resetFormRef.value.validate(async (valid) => {
    if (!valid) return;

    // 二次确认
    try {
      await ElMessageBox.confirm(
          `确定要重置用户 ${resetForm.targetUserId} 的密码吗？`,
          '危险操作',
          {
            confirmButtonText: '确认',
            cancelButtonText: '取消',
            type: 'warning'
          }
      );
    } catch {
      return; // 用户取消
    }

    submitting.value = true;
    try {
      const response = await adminResetPassword(
          resetForm.targetUserId,
          resetForm.targetRole,
          resetForm.newPassword
      );

      ElMessage.success(response.message || '密码重置成功');
      dialogVisible.value = false;
    } catch (error) {
      ElMessage.error('重置失败：' + error.message);
    } finally {
      submitting.value = false;
    }
  });
};

const resetForm = () => {
  if (resetFormRef.value) {
    resetFormRef.value.resetFields();
  }
};

// 页面加载时自动查询
handleSearch();
</script>

<style scoped>
.user-management {
  padding: 20px;
}
</style>