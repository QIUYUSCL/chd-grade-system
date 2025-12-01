<template>
  <div class="grade-view">
    <h2>成绩查询</h2>
    <el-card class="grade-card">
      <el-table
          :data="gradesData"
          style="width: 100%"
          stripe
          border
          v-loading="loading"
      >
        <el-table-column prop="course_name" label="课程名称" min-width="180" />
        <el-table-column prop="semester" label="学期" min-width="150" />
        <el-table-column prop="credit" label="学分" width="80" align="center" />
        <el-table-column prop="score" label="成绩" width="80" align="center" />
        <el-table-column prop="grade" label="等级" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" size="small">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空数据提示 -->
      <el-empty
          v-if="!loading && gradesData.length === 0"
          description="暂无成绩数据"
          style="margin-top: 20px;"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

// 成绩数据（初始为空，需从API获取）
const gradesData = ref([])
const loading = ref(false)

// 根据状态获取标签类型
const getStatusType = (status) => {
  const typeMap = {
    '已通过': 'success',
    '未通过': 'danger',
    '缓考': 'warning',
    '待录入': 'info'
  }
  return typeMap[status] || 'default'
}

// 获取成绩数据（预留API接口）
const fetchGrades = async () => {
  loading.value = true
  try {
    // TODO: 调用API获取成绩数据
    // const res = await getStudentGradesAPI()
    // gradesData.value = res.data
  } catch (error) {
    console.error('获取成绩失败:', error)
  } finally {
    loading.value = false
  }
}

// 组件挂载时加载数据
onMounted(() => {
  fetchGrades()
})
</script>

<style scoped>
.grade-view {
  padding: 0;
}

.grade-view h2 {
  margin-bottom: 20px;
  color: #303133;
  font-weight: 600;
}

.grade-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
</style>