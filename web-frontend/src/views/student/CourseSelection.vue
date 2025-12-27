<template>
  <div class="course-selection">
    <div class="page-header">
      <h2>网上选课</h2>
      <div class="filter-bar">
        <el-select v-model="semester" placeholder="选择学期" @change="fetchCourses" style="width: 200px">
          <el-option
              v-for="sem in semesters"
              :key="sem"
              :label="sem"
              :value="sem"
          />
        </el-select>
        <el-button type="primary" @click="fetchCourses" :icon="Refresh" style="margin-left: 10px">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="24">
        <el-alert title="选课规则" type="warning" :closable="false" show-icon>
          <template #default>
            <div>1. 只能选择本学期开设的课程。</div>
            <div>2. <strong>同名课程不能重复选择</strong>：选中某一门课后，其他同名课程将自动隐藏。</div>
            <div>3. <strong>历史重修限制</strong>：如果以前学期已修过某门课，该课将不再显示。</div>
          </template>
        </el-alert>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-table :data="visibleCourses" border stripe v-loading="loading" empty-text="暂无可选课程">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="course_name" label="课程名称" min-width="150">
          <template #default="scope">
            <span style="font-weight: bold; color: #303133">{{ scope.row.course_name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="teacher_name" label="任课教师" width="120" align="center">
          <template #default="scope">
            <el-tag effect="plain">{{ scope.row.teacher_name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="credit" label="学分" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.is_selected" type="success" effect="dark">已选</el-tag>
            <el-tag v-else type="info">未选</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <el-button v-if="!scope.row.is_selected" type="primary" size="small" @click="handleSelect(scope.row)">选课</el-button>
            <el-button v-else type="danger" size="small" plain @click="handleDrop(scope.row)">退课</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
// ✅ 引入 getSemesters
import { getCourseSelectionList, selectCourse, dropCourse, getSemesters } from '@/api/student'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'

const semester = ref('') // 初始为空，加载后设置默认值
const semesters = ref([]) // ✅ 存储学期列表
const allCourses = ref([])
const loading = ref(false)

const visibleCourses = computed(() => {
  const selectedNames = new Set(
      allCourses.value.filter(c => c.is_selected).map(c => c.course_name)
  )
  return allCourses.value.filter(c => {
    if (c.is_selected) return true;
    if (selectedNames.has(c.course_name)) return false;
    return true;
  })
})

// ✅ 初始化方法
const initData = async () => {
  try {
    // 1. 获取学期列表
    const res = await getSemesters()
    if (res.code === 200 && res.data && res.data.length > 0) {
      semesters.value = res.data
      // 默认选中第一个学期（最新的）
      semester.value = res.data[0]
      // 2. 获取课程
      fetchCourses()
    } else {
      ElMessage.warning('暂无学期数据')
    }
  } catch (e) {
    ElMessage.error('初始化失败: ' + e.message)
  }
}

const fetchCourses = async () => {
  if (!semester.value) return
  loading.value = true
  try {
    const res = await getCourseSelectionList(semester.value)
    allCourses.value = Array.isArray(res) ? res : (res.data || [])
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  } finally {
    loading.value = false
  }
}

const handleSelect = async (row) => {
  try {
    await selectCourse({ courseId: row.course_id, semester: semester.value })
    ElMessage.success(`已选择：${row.course_name}`)
    fetchCourses()
  } catch (error) {
    ElMessage.error(error.message || '选课失败')
  }
}

const handleDrop = (row) => {
  ElMessageBox.confirm(
      `确定要退选《${row.course_name}》吗？`,
      '退课确认',
      { confirmButtonText: '确定退课', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await dropCourse({ courseId: row.course_id, semester: semester.value })
      ElMessage.success('退课成功')
      fetchCourses()
    } catch (error) {
      ElMessage.error(error.message || '退课失败')
    }
  })
}

onMounted(() => {
  initData() // ✅ 替换原来的 fetchCourses()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; margin-bottom: 20px; }
</style>