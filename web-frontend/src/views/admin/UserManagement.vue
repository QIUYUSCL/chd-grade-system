<template>
  <div class="user-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>人员组织库维护</span>
          <el-button type="primary" @click="showAddDialog">添加用户</el-button>
        </div>
      </template>

      <!-- 查询条件 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="用户类型">
          <el-select v-model="queryForm.userType" placeholder="请选择用户类型" @change="handleUserTypeChange">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model="queryForm.userId" placeholder="请输入用户ID" clearable />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="queryForm.name" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="goToCourseManagement">课程管理</el-button>
        </el-form-item>
      </el-form>

      <!-- 用户列表 -->
      <el-table :data="userList" v-loading="loading" border stripe>
        <el-table-column :prop="userIdField" :label="userIdLabel" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="email" label="邮箱" width="200" />
        
        <!-- 学生特有字段 -->
        <template v-if="queryForm.userType === 'STUDENT'">
          <el-table-column prop="class_name" label="班级" width="100" />
          <el-table-column prop="major" label="专业" width="120" />
        </template>
        
        <!-- 教师特有字段 -->
        <template v-if="queryForm.userType === 'TEACHER'">
          <el-table-column prop="department" label="院系" width="150" />
          <el-table-column prop="title" label="职称" width="100" />
        </template>
        
        <!-- 管理员特有字段 -->
        <template v-if="queryForm.userType === 'ADMIN'">
          <el-table-column prop="department" label="管理院系" width="150" />
        </template>
        
        <el-table-column prop="created_at" label="创建时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" size="small" @click="handleResetPassword(row)">重置密码</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        class="pagination"
      />
    </el-card>

    <!-- 添加/编辑用户对话框 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="600px"
      @close="resetForm"
    >
      <el-form :model="userForm" :rules="formRules" ref="userFormRef" label-width="100px">
        <el-form-item label="用户类型" prop="userType">
          <el-select v-model="userForm.userType" placeholder="请选择用户类型" :disabled="isEdit">
            <el-option label="学生" value="STUDENT" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        
        <el-form-item :label="userIdLabel" prop="userId">
          <el-input v-model="userForm.userId" :placeholder="`请输入${userIdLabel}`" :disabled="isEdit" />
        </el-form-item>
        
        <el-form-item label="姓名" prop="name">
          <el-input v-model="userForm.name" placeholder="请输入姓名" />
        </el-form-item>
        
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="userForm.password" type="password" placeholder="请输入密码" />
        </el-form-item>
        
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        
        <!-- 学生特有字段 -->
        <template v-if="userForm.userType === 'STUDENT'">
          <el-form-item label="班级" prop="className">
            <el-input v-model="userForm.className" placeholder="请输入班级" />
          </el-form-item>
          <el-form-item label="专业" prop="major">
            <el-input v-model="userForm.major" placeholder="请输入专业" />
          </el-form-item>
        </template>
        
        <!-- 教师特有字段 -->
        <template v-if="userForm.userType === 'TEACHER'">
          <el-form-item label="院系" prop="department">
            <el-input v-model="userForm.department" placeholder="请输入院系" />
          </el-form-item>
          <el-form-item label="职称" prop="title">
            <el-select v-model="userForm.title" placeholder="请选择职称">
              <el-option label="教授" value="教授" />
              <el-option label="副教授" value="副教授" />
              <el-option label="讲师" value="讲师" />
              <el-option label="助教" value="助教" />
            </el-select>
          </el-form-item>
        </template>
        
        <!-- 管理员特有字段 -->
        <template v-if="userForm.userType === 'ADMIN'">
          <el-form-item label="管理院系" prop="department">
            <el-input v-model="userForm.department" placeholder="请输入管理院系" />
          </el-form-item>
        </template>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog
      title="重置密码"
      v-model="passwordDialogVisible"
      width="400px"
    >
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="80px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handlePasswordSubmit">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { 
  adminQueryUsers, 
  adminAddUser, 
  adminUpdateUser, 
  adminDeleteUser,
  adminResetPassword 
} from '@/api/admin.js';

const router = useRouter();

// 响应式数据
const loading = ref(false);
const userList = ref([]);
const dialogVisible = ref(false);
const passwordDialogVisible = ref(false);
const isEdit = ref(false);
const userFormRef = ref();
const passwordFormRef = ref();
const currentUser = ref(null);

// 查询表单
const queryForm = reactive({
  userType: 'STUDENT',
  userId: '',
  name: ''
});

// 分页信息
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
});

// 用户表单
const userForm = reactive({
  userType: 'STUDENT',
  userId: '',
  name: '',
  password: '',
  email: '',
  className: '',
  major: '',
  department: '',
  title: ''
});

// 密码表单
const passwordForm = reactive({
  newPassword: '',
  confirmPassword: ''
});

// 计算属性
const dialogTitle = computed(() => isEdit.value ? '编辑用户' : '添加用户');

const userIdField = computed(() => {
  switch (queryForm.userType) {
    case 'STUDENT': return 'student_id';
    case 'TEACHER': return 'teacher_id';
    case 'ADMIN': return 'admin_id';
    default: return 'user_id';
  }
});

const userIdLabel = computed(() => {
  switch (queryForm.userType) {
    case 'STUDENT': return '学号';
    case 'TEACHER': return '教师编号';
    case 'ADMIN': return '管理员编号';
    default: return '用户ID';
  }
});

// 表单验证规则
const formRules = {
  userType: [{ required: true, message: '请选择用户类型', trigger: 'change' }],
  userId: [{ required: true, message: '请输入用户ID', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
};

const passwordRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'));
        } else {
          callback();
        }
      },
      trigger: 'blur'
    }
  ]
};

// 查询用户数据
const fetchUsers = async () => {
  if (!queryForm.userType) {
    ElMessage.warning('请先选择用户类型');
    return;
  }

  loading.value = true;
  try {
    const params = {
      ...queryForm,
      page: pagination.page,
      pageSize: pagination.pageSize
    };
    
    const response = await adminQueryUsers(params);
    if (response.code === 200) {
      userList.value = response.data.list;
      pagination.total = response.data.total;
    } else {
      ElMessage.error(response.message || '查询失败');
    }
  } catch (error) {
    ElMessage.error('查询失败：' + error.message);
  } finally {
    loading.value = false;
  }
};

// 处理用户类型变化
const handleUserTypeChange = () => {
  pagination.page = 1;
  fetchUsers();
};

// 处理查询
const handleQuery = () => {
  pagination.page = 1;
  fetchUsers();
};

// 重置查询条件
const resetQuery = () => {
  queryForm.userId = '';
  queryForm.name = '';
  pagination.page = 1;
  fetchUsers();
};

// 处理分页大小变化
const handleSizeChange = (val) => {
  pagination.pageSize = val;
  pagination.page = 1;
  fetchUsers();
};

// 处理页码变化
const handleCurrentChange = (val) => {
  pagination.page = val;
  fetchUsers();
};

// 显示添加对话框
const showAddDialog = () => {
  isEdit.value = false;
  dialogVisible.value = true;
  resetForm();
};

// 处理编辑
const handleEdit = (row) => {
  isEdit.value = true;
  currentUser.value = row;
  
  // 填充表单数据
  userForm.userType = queryForm.userType;
  userForm.userId = row[userIdField.value];
  userForm.name = row.name;
  userForm.email = row.email;
  
  if (queryForm.userType === 'STUDENT') {
    userForm.className = row.class_name;
    userForm.major = row.major;
  } else if (queryForm.userType === 'TEACHER') {
    userForm.department = row.department;
    userForm.title = row.title;
  } else if (queryForm.userType === 'ADMIN') {
    userForm.department = row.department;
  }
  
  dialogVisible.value = true;
};

// 处理重置密码
const handleResetPassword = (row) => {
  currentUser.value = row;
  passwordForm.newPassword = '';
  passwordForm.confirmPassword = '';
  passwordDialogVisible.value = true;
};

// 处理删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 ${row.name}(${row[userIdField.value]}) 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );

    loading.value = true;
    const response = await adminDeleteUser(queryForm.userType, row[userIdField.value]);
    
    if (response.code === 200) {
      ElMessage.success('删除成功');
      fetchUsers();
    } else {
      ElMessage.error(response.message || '删除失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + error.message);
    }
  } finally {
    loading.value = false;
  }
};

// 处理表单提交
const handleSubmit = async () => {
  try {
    await userFormRef.value.validate();
    
    loading.value = true;
    let response;
    
    if (isEdit.value) {
      response = await adminUpdateUser(userForm);
    } else {
      response = await adminAddUser(userForm);
    }
    
    if (response.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '添加成功');
      dialogVisible.value = false;
      fetchUsers();
    } else {
      ElMessage.error(response.message || '操作失败');
    }
  } catch (error) {
    ElMessage.error('操作失败：' + error.message);
  } finally {
    loading.value = false;
  }
};

// 处理密码重置提交
const handlePasswordSubmit = async () => {
  try {
    await passwordFormRef.value.validate();
    
    loading.value = true;
    const targetRole = queryForm.userType;
    const targetUserId = currentUser.value[userIdField.value];
    
    const response = await adminResetPassword(targetUserId, targetRole, passwordForm.newPassword);
    
    if (response.code === 200) {
      ElMessage.success('密码重置成功');
      passwordDialogVisible.value = false;
    } else {
      ElMessage.error(response.message || '密码重置失败');
    }
  } catch (error) {
    ElMessage.error('密码重置失败：' + error.message);
  } finally {
    loading.value = false;
  }
};

// 重置表单
const resetForm = () => {
  if (userFormRef.value) {
    userFormRef.value.resetFields();
  }
  
  Object.keys(userForm).forEach(key => {
    if (key === 'userType') {
      userForm[key] = queryForm.userType;
    } else {
      userForm[key] = '';
    }
  });
};

// 组件挂载时获取数据
onMounted(() => {
  fetchUsers();
});

// 跳转到课程管理
const goToCourseManagement = () => {
  router.push('/admin/course-management');
};
</script>

<style scoped>
.user-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.query-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

.el-table {
  margin-bottom: 20px;
}

.dialog-footer {
  text-align: right;
}
</style>