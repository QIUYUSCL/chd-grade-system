<template>
  <div class="grade-view">
    <div class="header-actions">
      <h2>成绩管理</h2>
    </div>

    <el-card class="query-card" shadow="never">
      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="学期">
          <el-input v-model="queryForm.semester" placeholder="如: 2024-2025-1" clearable :prefix-icon="Calendar" />
        </el-form-item>

        <el-form-item label="课程">
          <el-select
              v-model="queryForm.courseId"
              placeholder="请选择课程"
              clearable
              :loading="courseLoading"
              :disabled="courseLoading || courses.length === 0"
              style="width: 240px">
            <el-option
                v-for="course in courses"
                :key="course.course_id"
                :label="course.course_name"
                :value="course.course_id">
              <span style="float: left">{{ course.course_name }}</span>
              <span style="float: right; color: #ccc; font-size: 12px">{{ course.course_id }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleQuery" :loading="loading" icon="Search">查询</el-button>
          <el-button @click="resetQuery" icon="Refresh">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card" shadow="hover">
      <el-table :data="gradeList" stripe style="width: 100%" v-loading="loading" :header-cell-style="{background:'#f5f7fa'}">
        <el-table-column prop="student_id" label="学号" width="130" fixed />
        <el-table-column prop="student_name" label="姓名" width="120" />
        <el-table-column prop="course_name" label="课程名称" min-width="150" />

        <el-table-column prop="total_score" label="总成绩" width="120" align="center">
          <template #default="scope">
            <span :class="getScoreClass(scope.row.total_score)">
              {{ scope.row.total_score || '--' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="exam_type" label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.exam_type === '正考' ? '' : 'warning'" effect="plain" size="small">
              {{ scope.row.exam_type }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'SUBMITTED' ? 'success' : 'info'" effect="dark" size="small">
              {{ scope.row.status === 'DRAFT' ? '草稿' : '已归档' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="semester" label="学期" width="140" />

        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="scope">
            <el-button
                v-if="scope.row.status === 'DRAFT'"
                type="primary"
                link
                size="small"
                @click="handleEdit(scope.row)"
                icon="Edit">
              修改
            </el-button>
            <span v-else style="color: #c0c4cc; font-size: 12px">
              <el-icon><Lock /></el-icon> 锁定
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next, sizes"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            background
        />
      </div>
    </el-card>

    <el-dialog
        v-model="editDialogVisible"
        title="修改成绩记录"
        width="500px"
        @close="resetEditForm"
        class="custom-dialog">

      <el-descriptions :column="2" border size="small" class="mb-20">
        <el-descriptions-item label="姓名">{{ editForm.student_name }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ editForm.student_id }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ editForm.course_name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ editForm.exam_type }}</el-descriptions-item>
      </el-descriptions>

      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="100px" class="edit-form">
        <el-form-item label="平时成绩" prop="dailyScore">
          <el-input-number v-model="editForm.dailyScore" :min="0" :max="100" :precision="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="期末成绩" prop="finalScore">
          <el-input-number v-model="editForm.finalScore" :min="0" :max="100" :precision="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="补考成绩" prop="makeupScore" v-if="editForm.exam_type === '补考'">
          <el-input-number v-model="editForm.makeupScore" :min="0" :max="100" :precision="1" style="width: 100%" />
        </el-form-item>

        <el-form-item label="预估总分">
          <span style="font-weight: bold; color: #1890ff; font-size: 18px">{{ editForm.totalScore }}</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitEdit" :loading="editLoading">保存修改</el-button>
          <el-button type="success" @click="submitAndCommit" :loading="editLoading" :disabled="!isFormValid">提交并锁定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// ... (script 逻辑保持不变，确保引入了 Calendar, Search, Refresh, Edit, Lock 图标) ...
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { Calendar, Search, Refresh, Edit, Lock } from '@element-plus/icons-vue'

// (此处粘贴原来的 JS 逻辑，包括 loadTeacherCourses, handleQuery, handleEdit 等)
const queryForm = reactive({
  semester: '',
  courseId: ''
})

const courses = ref([])
const courseLoading = ref(false)
const gradeList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const editDialogVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref(null)

const editForm = reactive({
  record_id: '',
  student_id: '',
  student_name: '',
  course_id: '',
  course_name: '',
  exam_type: '',
  dailyScore: null,
  finalScore: null,
  makeupScore: null,
  totalScore: null
})

const isFormValid = computed(() => {
  return editForm.dailyScore != null &&
      editForm.finalScore != null &&
      editForm.totalScore != null &&
      !isNaN(editForm.dailyScore) &&
      !isNaN(editForm.finalScore) &&
      !isNaN(editForm.totalScore)
})

const editRules = {
  dailyScore: [{ required: true, message: '请输入平时成绩', trigger: 'blur' }],
  finalScore: [{ required: true, message: '请输入期末成绩', trigger: 'blur' }]
}

watch([() => editForm.dailyScore, () => editForm.finalScore], ([daily, final]) => {
  if (daily != null && final != null && !isNaN(daily) && !isNaN(final)) {
    editForm.totalScore = Math.round((daily * 0.4 + final * 0.6) * 10) / 10
  }
}, { immediate: true })

const loadTeacherCourses = async () => {
  courseLoading.value = true
  try {
    const res = await request.get('/remote/client/teacher/courses')
    if (res.code === 200 && res.data) {
      courses.value = res.data
      if (courses.value.length === 0) {
        ElMessage.warning('您暂未分配任课课程，请联系管理员')
      }
    } else {
      ElMessage.error('获取课程列表失败: ' + (res.message || '未知错误'))
      courses.value = []
    }
  } catch (error) {
    console.error('获取课程列表异常:', error)
    ElMessage.error('获取课程列表失败: ' + error.message)
    courses.value = []
  } finally {
    courseLoading.value = false
  }
}

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

const resetQuery = () => {
  queryForm.semester = ''
  queryForm.courseId = ''
  currentPage.value = 1
  handleQuery()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  handleQuery()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  handleQuery()
}

// 辅助方法：成绩样式类
const getScoreClass = (score) => {
  if (!score) return ''
  const num = parseFloat(score)
  if (num < 60) return 'score-danger'
  if (num >= 90) return 'score-success'
  return 'score-normal'
}

const resetEditForm = () => {
  if (editFormRef.value) {
    try {
      editFormRef.value.resetFields()
    } catch (e) {
      console.warn('重置表单字段时出错:', e)
    }
  }
  editForm.record_id = ''
  editForm.student_id = ''
  editForm.student_name = ''
  editForm.course_id = ''
  editForm.course_name = ''
  editForm.exam_type = ''
  editForm.dailyScore = null
  editForm.finalScore = null
  editForm.makeupScore = null
  editForm.totalScore = null
}

const handleEdit = (row) => {
  resetEditForm()
  editForm.record_id = row.record_id
  editForm.student_id = row.student_id
  editForm.student_name = row.student_name
  editForm.course_id = row.course_id
  editForm.course_name = row.course_name
  editForm.exam_type = row.exam_type
  editForm.dailyScore = parseFloat(row.daily_score || 0)
  editForm.finalScore = parseFloat(row.final_score || 0)
  editForm.makeupScore = row.makeup_score ? parseFloat(row.makeup_score) : null
  editForm.totalScore = parseFloat(row.total_score || 0)
  editDialogVisible.value = true
}

const submitEdit = async () => {
  try {
    await editFormRef.value.validate()
    if ([editForm.dailyScore, editForm.finalScore, editForm.totalScore].some(v => v == null || isNaN(v))) {
      ElMessage.error('所有成绩分数都必须填写且为有效数字')
      return
    }
    editLoading.value = true
    const updateData = {
      daily_score: editForm.dailyScore.toString(),
      final_score: editForm.finalScore.toString(),
      total_score: editForm.totalScore.toString(),
      status: 'DRAFT'
    }
    if (editForm.exam_type === '补考' && editForm.makeupScore != null) {
      updateData.makeup_score = editForm.makeupScore.toString()
    }
    const res = await request.post('/remote/client/grade/update', {
      recordId: String(editForm.record_id),
      data: updateData
    })
    if (res.code === 200) {
      ElMessage.success('成绩修改成功')
      editDialogVisible.value = false
      handleQuery()
    } else {
      ElMessage.error(res.message || '修改失败')
    }
  } catch (error) {
    if (error.response?.status === 403) {
      ElMessage.error('权限验证失败或数据格式错误')
    } else {
      ElMessage.error(error.message || '修改异常')
    }
  } finally {
    editLoading.value = false
  }
}

const submitAndCommit = async () => {
  try {
    await editFormRef.value.validate()
    await ElMessageBox.confirm(
        '提交后成绩将被锁定，无法再次修改，确定要提交吗？',
        '提交确认',
        {
          confirmButtonText: '确定提交',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )
    if ([editForm.dailyScore, editForm.finalScore, editForm.totalScore].some(v => v == null || isNaN(v))) {
      ElMessage.error('所有成绩分数都必须填写且为有效数字')
      return
    }
    editLoading.value = true
    const updateData = {
      daily_score: editForm.dailyScore.toString(),
      final_score: editForm.finalScore.toString(),
      total_score: editForm.totalScore.toString(),
      status: 'SUBMITTED'
    }
    if (editForm.exam_type === '补考' && editForm.makeupScore != null) {
      updateData.makeup_score = editForm.makeupScore.toString()
    }
    const res = await request.post('/remote/client/grade/update', {
      recordId: String(editForm.record_id),
      data: updateData
    })
    if (res.code === 200) {
      ElMessage.success('成绩提交成功！记录已锁定')
      editDialogVisible.value = false
      handleQuery()
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (error) {
    if (error === 'cancel') return
    if (error.response?.status === 403) {
      ElMessage.error('权限验证失败或数据格式错误')
    } else {
      ElMessage.error(error.message || '提交异常')
    }
  } finally {
    editLoading.value = false
  }
}

onMounted(() => {
  loadTeacherCourses()
  handleQuery()
})
</script>

<style scoped>
.grade-view { padding: 20px; }
.header-actions { margin-bottom: 20px; }
.header-actions h2 { margin: 0; color: #303133; font-size: 22px; }

.query-card { margin-bottom: 20px; border: none; }
.query-form { margin-bottom: -18px; }

.list-card { border: none; }

.score-danger { color: #f56c6c; font-weight: bold; }
.score-success { color: #67c23a; font-weight: bold; }
.score-normal { color: #606266; }

.pagination-wrapper { margin-top: 20px; display: flex; justify-content: flex-end; }

.mb-20 { margin-bottom: 20px; }
</style>