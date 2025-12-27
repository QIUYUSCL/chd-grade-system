<template>
  <div class="security-management">
    <el-row :gutter="20">
      <!-- 数据完整性校验 -->
      <el-col :span="24">
        <el-card class="integrity-card">
          <template #header>
            <div class="card-header">
              <span>数据完整性校验</span>
              <el-button type="primary" @click="handleVerifyIntegrity" :loading="verifyLoading">
                执行校验
              </el-button>
            </div>
          </template>
          
          <div v-if="integrityResult">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="总记录数">
                {{ integrityResult.total_records }}
              </el-descriptions-item>
              <el-descriptions-item label="已验证记录">
                {{ integrityResult.verified_records }}
              </el-descriptions-item>
              <el-descriptions-item label="异常记录数">
                <el-tag :type="integrityResult.tampered_records > 0 ? 'danger' : 'success'">
                  {{ integrityResult.tampered_records }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="完整性状态">
                <el-tag :type="integrityResult.integrity_status === 'INTACT' ? 'success' : 'danger'">
                  {{ integrityResult.integrity_status === 'INTACT' ? '完整' : '已被篡改' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
            
            <!-- 异常记录详情 -->
            <div v-if="integrityResult.tampered_records > 0" class="tampered-section">
              <h4>异常记录详情</h4>
              <el-table :data="integrityResult.tampered_list" border stripe>
                <el-table-column prop="record_id" label="记录ID" width="100" />
                <el-table-column prop="student_id" label="学号" width="120" />
                <el-table-column prop="course_id" label="课程编号" width="120" />
                <el-table-column prop="exam_type" label="考试类型" width="100" />
                <el-table-column prop="stored_hash" label="存储哈希" width="200" show-overflow-tooltip />
                <el-table-column prop="computed_hash" label="计算哈希" width="200" show-overflow-tooltip />
              </el-table>
            </div>
          </div>
          
          <div v-else class="no-data">
            <el-empty description="暂无校验结果，请点击执行校验" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 安全日志查询 -->
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>安全审计日志</span>
              <el-button type="primary" @click="refreshLogs">刷新</el-button>
            </div>
          </template>

          <!-- 查询条件 -->
          <el-form :model="logQueryForm" inline class="query-form">
            <el-form-item label="操作类型">
              <el-select v-model="logQueryForm.operationType" placeholder="请选择操作类型" clearable>
                <el-option label="登录" value="LOGIN" />
                <el-option label="成绩录入" value="GRADE_ENTRY" />
                <el-option label="成绩修改" value="GRADE_UPDATE" />
                <el-option label="成绩查看" value="GRADE_VIEW" />
                <el-option label="成绩撤销" value="GRADE_REVOKE" />
                <el-option label="密码修改" value="PASSWORD_CHANGE" />
                <el-option label="密码重置" value="PASSWORD_RESET" />
                <el-option label="管理员小撤销" value="ADMIN_MINOR_REVOKE" />
                <el-option label="管理员大撤销" value="ADMIN_MAJOR_REVOKE" />
                <el-option label="用户管理" value="ADMIN_USER_ADD" />
                <el-option label="数据校验" value="ADMIN_DATA_VERIFY" />
              </el-select>
            </el-form-item>
            <el-form-item label="表名">
              <el-select v-model="logQueryForm.tableName" placeholder="请选择表名" clearable>
                <el-option label="成绩记录" value="grade_records" />
                <el-option label="学生" value="students" />
                <el-option label="教师" value="teachers" />
                <el-option label="管理员" value="admins" />
                <el-option label="课程" value="courses" />
                <el-option label="用户" value="users" />
              </el-select>
            </el-form-item>
            <el-form-item label="操作人ID">
              <el-input v-model="logQueryForm.operatorId" placeholder="请输入操作人ID" clearable />
            </el-form-item>
            <el-form-item label="操作人类型">
              <el-select v-model="logQueryForm.operatorType" placeholder="请选择操作人类型" clearable>
                <el-option label="学生" value="STUDENT" />
                <el-option label="教师" value="TEACHER" />
                <el-option label="管理员" value="ADMIN" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleLogQuery">查询</el-button>
              <el-button @click="resetLogQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <!-- 日志列表 -->
          <el-table :data="logList" v-loading="logLoading" border stripe>
            <el-table-column prop="log_id" label="日志ID" width="80" />
            <el-table-column prop="operation_type" label="操作类型" width="150" />
            <el-table-column prop="table_name" label="表名" width="120" />
            <el-table-column prop="record_id" label="记录ID" width="200" show-overflow-tooltip />
            <el-table-column prop="operator_id" label="操作人ID" width="120" />
            <el-table-column prop="operator_type" label="操作人类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getOperatorTypeTag(row.operator_type)">
                  {{ getOperatorTypeText(row.operator_type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="client_ip" label="客户端IP" width="150" />
            <el-table-column prop="operation_time" label="操作时间" width="160" />
          </el-table>

          <!-- 分页 -->
          <el-pagination
            v-model:current-page="logPagination.page"
            v-model:page-size="logPagination.pageSize"
            :page-sizes="[20, 50, 100, 200]"
            :total="logPagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleLogSizeChange"
            @current-change="handleLogCurrentChange"
            class="pagination"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { adminVerifyDataIntegrity, adminQuerySecurityLog } from '@/api/admin.js';

// 响应式数据
const verifyLoading = ref(false);
const logLoading = ref(false);
const integrityResult = ref(null);
const logList = ref([]);

// 日志查询表单
const logQueryForm = reactive({
  operationType: '',
  tableName: '',
  operatorId: '',
  operatorType: ''
});

// 日志分页信息
const logPagination = reactive({
  page: 1,
  pageSize: 50,
  total: 0
});

// 执行数据完整性校验
const handleVerifyIntegrity = async () => {
  verifyLoading.value = true;
  try {
    const response = await adminVerifyDataIntegrity();
    if (response.code === 200) {
      integrityResult.value = response.data;
      if (response.data.integrity_status === 'INTACT') {
        ElMessage.success('数据完整性校验通过，所有数据完整无篡改');
      } else {
        ElMessage.warning(`发现 ${response.data.tampered_records} 条异常记录，请检查详情`);
      }
    } else {
      ElMessage.error(response.message || '校验失败');
    }
  } catch (error) {
    ElMessage.error('校验失败：' + error.message);
  } finally {
    verifyLoading.value = false;
  }
};

// 查询安全日志
const fetchLogs = async () => {
  logLoading.value = true;
  try {
    const params = {
      ...logQueryForm,
      page: logPagination.page,
      pageSize: logPagination.pageSize
    };
    
    const response = await adminQuerySecurityLog(params);
    if (response.code === 200) {
      logList.value = response.data.list;
      logPagination.total = response.data.total;
    } else {
      ElMessage.error(response.message || '查询失败');
    }
  } catch (error) {
    ElMessage.error('查询失败：' + error.message);
  } finally {
    logLoading.value = false;
  }
};

// 处理日志查询
const handleLogQuery = () => {
  logPagination.page = 1;
  fetchLogs();
};

// 重置日志查询条件
const resetLogQuery = () => {
  Object.keys(logQueryForm).forEach(key => {
    logQueryForm[key] = '';
  });
  logPagination.page = 1;
  fetchLogs();
};

// 刷新日志
const refreshLogs = () => {
  fetchLogs();
};

// 处理日志分页大小变化
const handleLogSizeChange = (val) => {
  logPagination.pageSize = val;
  logPagination.page = 1;
  fetchLogs();
};

// 处理日志页码变化
const handleLogCurrentChange = (val) => {
  logPagination.page = val;
  fetchLogs();
};

// 获取操作人类型标签样式
const getOperatorTypeTag = (type) => {
  switch (type) {
    case 'ADMIN': return 'danger';
    case 'TEACHER': return 'warning';
    case 'STUDENT': return 'info';
    default: return '';
  }
};

// 获取操作人类型文本
const getOperatorTypeText = (type) => {
  switch (type) {
    case 'ADMIN': return '管理员';
    case 'TEACHER': return '教师';
    case 'STUDENT': return '学生';
    default: return type;
  }
};

// 组件挂载时获取数据
onMounted(() => {
  fetchLogs();
});
</script>

<style scoped>
.security-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.integrity-card {
  margin-bottom: 20px;
}

.tampered-section {
  margin-top: 20px;
}

.tampered-section h4 {
  color: #e6a23c;
  margin-bottom: 10px;
}

.no-data {
  text-align: center;
  padding: 40px 0;
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

.el-descriptions {
  margin-bottom: 20px;
}
</style>