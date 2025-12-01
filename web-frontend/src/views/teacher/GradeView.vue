<template>
  <div class="grade-view">
    <h2>成绩查看</h2>

    <!-- 查询条件卡片 -->
    <el-card class="query-card">
      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="学期" prop="semester">
          <el-input v-model="queryForm.semester" placeholder="如: 2024-2025-1" clearable />
        </el-form-item>
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="queryForm.courseId" placeholder="请选择课程" clearable>
            <el-option label="数据结构" value="CS202" />
            <el-option label="操作系统" value="CS303" />
            <el-option label="计算机网络" value="CS404" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery" :loading="loading">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 成绩列表卡片 -->
    <el-card class="list-card">
      <el-table :data="gradeList" stripe style="width: 100%" v-loading="loading" border>
        <el-table-column prop="student_id" label="学号" width="120" fixed />
        <el-table-column prop="student_name" label="姓名" width="100" />
        <el-table-column prop="course_name" label="课程名称" width="150" />
        <el-table-column prop="total_score" label="总成绩" width="110">
          <template #default="scope">
            <el-tag :type="getScoreTagType(scope.row.total_score)" size="small">
              {{ scope.row.total_score || '--' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="exam_type" label="考试类型" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.exam_type === '正考' ? 'success' : 'warning'" size="small">
              {{ scope.row.exam_type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'SUBMITTED' ? 'success' : 'info'" size="small">
              {{ scope.row.status === 'DRAFT' ? '暂存' : '已提交' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="semester" label="学期" width="120" />
        <el-table-column prop="created_at" label="录入时间" width="180" />
      </el-table>

      <!-- 分页 -->
      <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

// 查询表单
const queryForm = reactive({
  semester: '',
  courseId: ''
})

// 表格数据
const gradeList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 查询方法
const handleQuery = async () => {
  loading.value = true
  try {
    const params = {
      semester: queryForm.semester || undefined,
      courseId: queryForm.courseId || undefined,
      page: currentPage.value,
      pageSize: pageSize.value
    }

    const res = await request.get('/remote/client/grade/view', { params })

    if (res.code === 200) {
      gradeList.value = res.data.list || []
      total.value = res.data.total || 0
      ElMessage.success(`查询成功，共${total.value}条记录`)
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (error) {
    ElMessage.error('查询异常: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 重置查询
const resetQuery = () => {
  queryForm.semester = ''
  queryForm.courseId = ''
  currentPage.value = 1
  handleQuery()
}

// 分页变化
const handleSizeChange = (val) => {
  pageSize.value = val
  handleQuery()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  handleQuery()
}

// 成绩标签样式
const getScoreTagType = (score) => {
  if (!score) return 'info'
  const num = parseFloat(score)
  if (num >= 90) return 'success'
  if (num >= 60) return 'warning'
  return 'danger'
}

// 页面加载时自动查询
onMounted(() => {
  handleQuery()
})
</script>

<style scoped>
.grade-view {
  padding: 20px;
  min-height: 100%;
}

.query-card, .list-card {
  margin-bottom: 20px;
  background: #fff;
}

.query-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-start;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

h2 {
  color: #303133;
  margin-bottom: 20px;
  font-size: 24px;
}
</style>