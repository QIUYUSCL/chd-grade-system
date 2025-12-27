<template>
  <div class="grade-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>成绩录入管理</span>
          <el-button type="primary" @click="refreshData">刷新</el-button>
        </div>
      </template>

      <!-- 查询条件 -->
      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="学号">
          <el-input v-model="queryForm.studentId" placeholder="请输入学号" clearable />
        </el-form-item>
        <el-form-item label="课程编号">
          <el-input v-model="queryForm.courseId" placeholder="请输入课程编号" clearable />
        </el-form-item>
        <el-form-item label="教师编号">
          <el-input v-model="queryForm.teacherId" placeholder="请输入教师编号" clearable />
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="queryForm.semester" placeholder="请选择学期" clearable>
            <el-option label="2024-2025-1" value="2024-2025-1" />
            <el-option label="2024-2025-2" value="2024-2025-2" />
            <el-option label="2023-2024-1" value="2023-2024-1" />
            <el-option label="2023-2024-2" value="2023-2024-2" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试类型">
          <el-select v-model="queryForm.examType" placeholder="请选择考试类型" clearable>
            <el-option label="正考" value="正考" />
            <el-option label="补考" value="补考" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="请选择状态" clearable>
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已提交" value="SUBMITTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 成绩列表 -->
      <el-table :data="gradeList" v-loading="loading" border stripe>
        <el-table-column prop="record_id" label="记录ID" width="80" />
        <el-table-column prop="student_id" label="学号" width="120" />
        <el-table-column prop="student_name" label="学生姓名" width="100" />
        <el-table-column prop="class_name" label="班级" width="100" />
        <el-table-column prop="course_id" label="课程编号" width="100" />
        <el-table-column prop="course_name" label="课程名称" width="120" />
        <el-table-column prop="teacher_name" label="任课教师" width="100" />
        <el-table-column prop="semester" label="学期" width="100" />
        <el-table-column prop="exam_type" label="考试类型" width="80" />
        <el-table-column prop="total_score" label="总成绩" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUBMITTED' ? 'success' : 'warning'">
              {{ row.status === 'SUBMITTED' ? '已提交' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="160" />
        <el-table-column prop="updated_at" label="更新时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button 
              type="warning" 
              size="small" 
              @click="handleMinorRevoke(row)"
              :disabled="row.status !== 'SUBMITTED'"
            >
              小撤销
            </el-button>
            <el-button 
              type="danger" 
              size="small" 
              @click="handleMajorRevoke(row)"
            >
              大撤销
            </el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminQueryGrades, adminMinorRevoke, adminMajorRevoke } from '@/api/admin.js';

// 响应式数据
const loading = ref(false);
const gradeList = ref([]);

// 查询表单
const queryForm = reactive({
  studentId: '',
  courseId: '',
  teacherId: '',
  semester: '',
  examType: '',
  status: ''
});

// 分页信息
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
});

// 查询成绩数据
const fetchGrades = async () => {
  loading.value = true;
  try {
    const params = {
      ...queryForm,
      page: pagination.page,
      pageSize: pagination.pageSize
    };
    
    const response = await adminQueryGrades(params);
    if (response.code === 200) {
      gradeList.value = response.data.list;
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

// 处理查询
const handleQuery = () => {
  pagination.page = 1;
  fetchGrades();
};

// 重置查询条件
const resetQuery = () => {
  Object.keys(queryForm).forEach(key => {
    queryForm[key] = '';
  });
  pagination.page = 1;
  fetchGrades();
};

// 刷新数据
const refreshData = () => {
  fetchGrades();
};

// 处理分页大小变化
const handleSizeChange = (val) => {
  pagination.pageSize = val;
  pagination.page = 1;
  fetchGrades();
};

// 处理页码变化
const handleCurrentChange = (val) => {
  pagination.page = val;
  fetchGrades();
};

// 小撤销处理
const handleMinorRevoke = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要对学生 ${row.student_name}(${row.student_id}) 的 ${row.course_name} 成绩进行小撤销吗？\n小撤销将把成绩状态从"已提交"改为"暂存"，教师可重新修改。`,
      '小撤销确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );

    loading.value = true;
    const response = await adminMinorRevoke(row.record_id);
    
    if (response.code === 200) {
      ElMessage.success('小撤销成功');
      fetchGrades(); // 刷新列表
    } else {
      ElMessage.error(response.message || '小撤销失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('小撤销失败：' + error.message);
    }
  } finally {
    loading.value = false;
  }
};

// 大撤销处理
const handleMajorRevoke = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要对学生 ${row.student_name}(${row.student_id}) 的 ${row.course_name} 成绩进行大撤销吗？\n大撤销将完全删除成绩记录，教师需重新录入！`,
      '大撤销确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error',
        confirmButtonClass: 'el-button--danger'
      }
    );

    loading.value = true;
    const response = await adminMajorRevoke(row.record_id);
    
    if (response.code === 200) {
      ElMessage.success('大撤销成功');
      fetchGrades(); // 刷新列表
    } else {
      ElMessage.error(response.message || '大撤销失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('大撤销失败：' + error.message);
    }
  } finally {
    loading.value = false;
  }
};

// 组件挂载时获取数据
onMounted(() => {
  fetchGrades();
});
</script>

<style scoped>
.grade-management {
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
</style>