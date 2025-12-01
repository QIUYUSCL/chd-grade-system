<template>
  <div class="password-reset" v-if="userStore.isAdmin">
    <h2>用户密码重置</h2>

    <el-card class="reset-card">
      <!-- 目标用户信息 -->
      <div class="user-info">
        <h3>目标用户信息</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户ID">{{ targetUser.userId }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ targetUser.name }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag :type="targetUser.role === 'STUDENT' ? 'success' : 'warning'">
              {{ targetUser.role === 'STUDENT' ? '学生' : '教师' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 密码重置表单 -->
      <el-form
          :model="form"
          :rules="rules"
          ref="formRef"
          label-width="100px"
          class="reset-form"
          style="margin-top: 30px"
      >
        <el-alert
            title="警告：此操作将重置用户密码，请谨慎操作！"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 20px"
        />

        <el-form-item label="新密码" prop="newPassword">
          <el-input
              v-model="form.newPassword"
              type="password"
              show-password
              placeholder="请输入新密码（至少6位）"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>

      <!-- 操作按钮 -->
      <div class="form-actions">
        <el-button @click="handleCancel">返回列表</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确认重置
        </el-button>
      </div>
    </el-card>
  </div>

  <div v-else>
    <el-alert type="error" title="权限不足：仅管理员可访问此页面" show-icon />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/store/user.js';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminResetPassword } from '@/api/admin.js';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const formRef = ref();
const submitting = ref(false);

// 目标用户信息（从路由参数获取）
const targetUser = reactive({
  userId: '',
  name: '',
  role: ''
});

// 表单数据
const form = reactive({
  newPassword: '',
  confirmPassword: ''
});

// 验证规则
const rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ]
};

// 从路由参数加载目标用户信息
const loadTargetUser = () => {
  targetUser.userId = route.query.userId;
  targetUser.name = route.query.name;
  targetUser.role = route.query.role;

  // 验证参数完整性
  if (!targetUser.userId || !targetUser.name || !targetUser.role) {
    ElMessage.error('参数错误，请从用户列表选择用户');
    router.push('/admin/users');
  }
};

// 提交重置密码
const handleSubmit = async () => {
  if (!formRef.value) return;

  await formRef.value.validate(async (valid) => {
    if (!valid) return;

    // 二次确认
    try {
      await ElMessageBox.confirm(
          `确定要重置用户 ${targetUser.name}(${targetUser.userId}) 的密码吗？`,
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
          targetUser.userId,
          targetUser.role,
          form.newPassword
      );

      ElMessage.success({
        message: response.message || '密码重置成功',
        duration: 2000
      });

      // 操作成功后返回列表
      setTimeout(() => {
        handleCancel();
      }, 1500);
    } catch (error) {
      ElMessage.error('操作失败：' + (error.message || '未知错误'));
    } finally {
      submitting.value = false;
    }
  });
};

// 返回用户列表
const handleCancel = () => {
  router.push('/admin/users');
};

// 页面加载时初始化
onMounted(() => {
  loadTargetUser();
});
</script>

<style scoped>
.password-reset {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.reset-card {
  margin-top: 20px;
  padding: 20px;
}

.user-info {
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 20px;
}

.reset-form {
  max-width: 500px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

h2 {
  margin-bottom: 20px;
  color: #303133;
}

h3 {
  margin-bottom: 15px;
  color: #606266;
}
</style>