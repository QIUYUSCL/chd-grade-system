<template>
  <div class="grade-view">
    <h2>成绩查看</h2>

    <!-- 查询条件卡片 -->
    <el-card class="query-card">
      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="学期" prop="semester">
          <el-input v-model="queryForm.semester" placeholder="如: 2024-2025-1" clearable />
        </el-form-item>

        <!-- 课程选择（动态加载） -->
        <el-form-item label="课程" prop="courseId">
          <el-select
              v-model="queryForm.courseId"
              placeholder="请选择课程"
              clearable
              :loading="courseLoading"
              :disabled="courseLoading || courses.length === 0">
            <el-option
                v-for="course in courses"
                :key="course.course_id"
                :label="course.course_name + ' (' + course.course_id + ')'"
                :value="course.course_id" />
          </el-select>
          <span v-if="courses.length === 0 && !courseLoading" style="color: #909399; font-size: 12px;">
            暂无任课课程
          </span>
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

        <!-- 操作列 -->
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button
                v-if="scope.row.status === 'DRAFT'"
                type="primary"
                size="small"
                @click="handleEdit(scope.row)"
                plain>
              修改
            </el-button>
            <el-button
                v-else
                type="info"
                size="small"
                disabled
                plain>
              已锁定
            </el-button>
          </template>
        </el-table-column>
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

    <!-- 编辑对话框 -->
    <el-dialog
        v-model="editDialogVisible"
        title="修改成绩"
        width="600px"
        @close="resetEditForm">
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="140px">

        <el-form-item label="学号">
          <el-input v-model="editForm.student_id" disabled />
        </el-form-item>

        <el-form-item label="学生姓名">
          <el-input v-model="editForm.student_name" disabled />
        </el-form-item>

        <el-form-item label="课程">
          <el-input :value="editForm.course_name + ' (' + editForm.course_id + ')'" disabled />
        </el-form-item>

        <el-form-item label="考试类型">
          <el-tag :type="editForm.exam_type === '正考' ? 'success' : 'warning'" size="small">
            {{ editForm.exam_type }}
          </el-tag>
        </el-form-item>

        <!-- 成绩分项修改 -->
        <el-divider content-position="left">修改成绩</el-divider>

        <el-form-item label="平时成绩" prop="dailyScore">
          <el-input-number v-model="editForm.dailyScore" :min="0" :max="100" :precision="1" />
        </el-form-item>

        <el-form-item label="期末成绩" prop="finalScore">
          <el-input-number v-model="editForm.finalScore" :min="0" :max="100" :precision="1" />
        </el-form-item>

        <el-form-item label="补考成绩" prop="makeupScore" v-if="editForm.exam_type === '补考'">
          <el-input-number v-model="editForm.makeupScore" :min="0" :max="100" :precision="1" />
        </el-form-item>

        <el-form-item label="总成绩" prop="totalScore">
          <el-input-number v-model="editForm.totalScore" :min="0" :max="100" :precision="1" disabled />
          <span style="color: #909399; margin-left: 10px;">保存时自动重新计算加密</span>
        </el-form-item>

      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit" :loading="editLoading">
          保存修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, reactive, onMounted, nextTick, watch} from 'vue'  // ✅ 导入 nextTick
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

// 查询表单
const queryForm = reactive({
  semester: '',
  courseId: ''
})

// 课程列表
const courses = ref([])
const courseLoading = ref(false)

// 表格数据
const gradeList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 编辑对话框状态
const editDialogVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref(null)  // ✅ 使用 null 初始化


// 编辑表单数据
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

const editRules = {
  dailyScore: [{ required: true, message: '请输入平时成绩', trigger: 'blur' }],
  finalScore: [{ required: true, message: '请输入期末成绩', trigger: 'blur' }]
}


// 添加计算总成绩的监听
watch([() => editForm.dailyScore, () => editForm.finalScore], ([daily, final]) => {
  if (daily != null && final != null && !isNaN(daily) && !isNaN(final)) {
    // 根据业务规则计算总成绩（示例：平时*0.4 + 期末*0.6）
    editForm.totalScore = Math.round((daily * 0.4 + final * 0.6) * 10) / 10
  }
}, { immediate: true })

// 加载教师课程列表
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

// 重置编辑表单（修复版）
const resetEditForm = () => {
  // 直接在同步代码中重置
  if (editFormRef.value) {
    try {
      editFormRef.value.resetFields()
    } catch (e) {
      console.warn('重置表单字段时出错:', e)
    }
  }

  // 手动重置所有字段
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


// 打开编辑对话框
const handleEdit = (row) => {
  console.log('编辑行数据:', JSON.stringify(row, null, 2))

  // 1. 先同步重置
  resetEditForm()

  // 2. 再填充数据
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

  // 3. 最后显示对话框
  editDialogVisible.value = true
}


// 提交修改
const submitEdit = async () => {
  try {
    await editFormRef.value.validate()

    if ([editForm.dailyScore, editForm.finalScore, editForm.totalScore].some(v => v == null || isNaN(v))) {
      ElMessage.error('所有成绩分数都必须填写且为有效数字')
      return
    }

    editLoading.value = true

    // ✅ 发送明文成绩字段，让后端统一加密
    const updateData = {
      daily_score: editForm.dailyScore.toString(),
      final_score: editForm.finalScore.toString(),
      total_score: editForm.totalScore.toString(),
      status: 'DRAFT'
    }

    // 如果是补考，添加补考成绩
    if (editForm.exam_type === '补考' && editForm.makeupScore != null) {
      updateData.makeup_score = editForm.makeupScore.toString()
    }

    // ✅ 关键修复：recordId 转换为字符串
    const res = await request.post('/remote/client/grade/update', {
      recordId: String(editForm.record_id),
      data: updateData  // ✅ 明文数据
    })

    if (res.code === 200) {
      ElMessage.success('成绩修改成功')
      editDialogVisible.value = false
      handleQuery() // 刷新列表
    } else {
      ElMessage.error(res.message || '修改失败')
    }
  } catch (error) {
    if (error.response?.status === 403) {
      ElMessage.error('权限验证失败或数据格式错误，请检查后端日志')
    } else {
      ElMessage.error(error.message || '修改异常')
    }
  } finally {
    editLoading.value = false
  }
}

const encryptScore = async (score) => {
  // 处理 null、undefined、NaN 等情况
  if (score == null || isNaN(score)) {
    ElMessage.warning('存在无效的成绩分数，请检查输入')
    throw new Error(`成绩分数无效: ${score}`)
  }
  return score.toString()
}

// 页面加载
onMounted(() => {
  loadTeacherCourses()
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