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
        <el-table-column prop="updated_at" label="最后更新" width="180" show-overflow-tooltip />

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
        width="650px"
        @close="resetEditForm"
        class="custom-dialog"
        destroy-on-close>

      <el-descriptions :column="2" border size="small" class="mb-20">
        <el-descriptions-item label="姓名">{{ editForm.student_name }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ editForm.student_id }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ editForm.course_name }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="editForm.exam_type === '正考' ? '' : 'warning'" size="small">{{ editForm.exam_type }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="editForm" ref="editFormRef" label-position="top" class="edit-form">

        <div v-if="editForm.exam_type === '正考'">
          <el-divider content-position="left">正考成绩构成调整</el-divider>

          <el-row :gutter="15">
            <el-col :span="8">
              <div class="score-mini-card">
                <div class="label">考勤</div>
                <div class="flex-input">
                  <el-input-number v-model="editForm.attendanceRatio" :min="0" :max="100" size="small" :controls="false" placeholder="系数%" @change="calculateTotal"/>
                  <span class="sep">%</span>
                  <el-input-number v-model="editForm.attendanceScore" :min="0" :max="100" size="small" :controls="false" placeholder="得分" @change="calculateTotal" :disabled="editForm.attendanceRatio===0"/>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="score-mini-card">
                <div class="label">作业</div>
                <div class="flex-input">
                  <el-input-number v-model="editForm.homeworkRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/>
                  <span class="sep">%</span>
                  <el-input-number v-model="editForm.homeworkScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.homeworkRatio===0"/>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="score-mini-card">
                <div class="label">实验</div>
                <div class="flex-input">
                  <el-input-number v-model="editForm.experimentRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/>
                  <span class="sep">%</span>
                  <el-input-number v-model="editForm.experimentScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.experimentRatio===0"/>
                </div>
              </div>
            </el-col>
          </el-row>

          <el-row :gutter="15" style="margin-top: 10px;">
            <el-col :span="8">
              <div class="score-mini-card">
                <div class="label">期中</div>
                <div class="flex-input">
                  <el-input-number v-model="editForm.midtermRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/>
                  <span class="sep">%</span>
                  <el-input-number v-model="editForm.midtermScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.midtermRatio===0"/>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="score-mini-card">
                <div class="label">其他平时</div>
                <div class="flex-input">
                  <el-input-number v-model="editForm.dailyRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/>
                  <span class="sep">%</span>
                  <el-input-number v-model="editForm.dailyScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.dailyRatio===0"/>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="score-mini-card highlight">
                <div class="label">期末考试</div>
                <div class="flex-input">
                  <el-input-number v-model="editForm.finalRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/>
                  <span class="sep">%</span>
                  <el-input-number v-model="editForm.finalScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/>
                </div>
              </div>
            </el-col>
          </el-row>

          <div class="ratio-warning" v-if="totalRatio !== 100">
            <el-icon><Warning /></el-icon> 当前系数总和: {{ totalRatio }}% (建议调整为100%)
          </div>
        </div>

        <div v-else class="makeup-mode">
          <el-divider content-position="left">补考成绩录入</el-divider>
          <el-alert title="注意：补考总成绩仅取决于补考卷面成绩，与其他平时分无关。" type="info" :closable="false" show-icon />

          <el-form-item label="补考卷面成绩" prop="makeupScore" style="margin-top: 20px">
            <el-input-number v-model="editForm.makeupScore" :min="0" :max="100" :precision="1" size="large" style="width: 100%" @change="calculateTotal" />
          </el-form-item>
        </div>

        <div class="total-bar">
          <span class="txt">计算总评：</span>
          <span class="num">{{ editForm.totalScore }}</span>
        </div>

      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitEdit" :loading="editLoading" :disabled="editForm.exam_type === '正考' && totalRatio !== 100">保存修改</el-button>
          <el-button type="success" @click="submitAndCommit" :loading="editLoading" :disabled="!isFormValid || (editForm.exam_type === '正考' && totalRatio !== 100)">提交并锁定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { Calendar, Search, Refresh, Edit, Lock, Warning } from '@element-plus/icons-vue'

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

  // 成绩与系数
  attendanceRatio: 10, attendanceScore: null,
  homeworkRatio: 20,   homeworkScore: null,
  experimentRatio: 0,  experimentScore: null,
  midtermRatio: 0,     midtermScore: null,
  dailyRatio: 0,       dailyScore: null,
  finalRatio: 70,      finalScore: null,

  makeupScore: null,
  totalScore: null
})

// 计算系数总和
const totalRatio = computed(() => {
  if (editForm.exam_type === '补考') return 100 // 补考不校验系数
  return (editForm.attendanceRatio || 0) + (editForm.homeworkRatio || 0) +
      (editForm.experimentRatio || 0) + (editForm.midtermRatio || 0) +
      (editForm.dailyRatio || 0) + (editForm.finalRatio || 0)
})

// 表单有效性校验
const isFormValid = computed(() => {
  if (editForm.exam_type === '补考') {
    return editForm.makeupScore != null
  } else {
    // 正考：必须有期末成绩，且总分已计算
    return editForm.finalScore != null && editForm.totalScore != null
  }
})

// 核心：计算总分逻辑（完全重写）
const calculateTotal = () => {
  if (editForm.exam_type === '补考') {
    // 逻辑：补考成绩只看补考卷面分，忽略其他
    editForm.totalScore = editForm.makeupScore
  } else {
    // 逻辑：正考 = 各项加权求和
    let total = 0
    const add = (score, ratio) => {
      if ((ratio || 0) > 0 && score !== null && !isNaN(score)) {
        total += score * (ratio / 100)
      }
    }

    add(editForm.attendanceScore, editForm.attendanceRatio)
    add(editForm.homeworkScore, editForm.homeworkRatio)
    add(editForm.experimentScore, editForm.experimentRatio)
    add(editForm.midtermScore, editForm.midtermRatio)
    add(editForm.dailyScore, editForm.dailyRatio)
    add(editForm.finalScore, editForm.finalRatio)

    editForm.totalScore = Math.round(total * 10) / 10
  }
}

const loadTeacherCourses = async () => {
  courseLoading.value = true
  try {
    const res = await request.get('/remote/client/teacher/courses')
    if (res.code === 200 && res.data) {
      courses.value = res.data
    }
  } catch (error) {
    ElMessage.error('获取课程列表失败')
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

const getScoreClass = (score) => {
  if (!score) return ''
  const num = parseFloat(score)
  if (num < 60) return 'score-danger'
  if (num >= 90) return 'score-success'
  return 'score-normal'
}

const resetEditForm = () => {
  // 重置数据
  Object.assign(editForm, {
    record_id: '', student_id: '', student_name: '', course_id: '', course_name: '', exam_type: '',
    attendanceRatio: 10, attendanceScore: null,
    homeworkRatio: 20, homeworkScore: null,
    experimentRatio: 0, experimentScore: null,
    midtermRatio: 0, midtermScore: null,
    dailyRatio: 0, dailyScore: null,
    finalRatio: 70, finalScore: null,
    makeupScore: null, totalScore: null
  })
}

// 打开编辑框并回填数据
const handleEdit = (row) => {
  resetEditForm()

  // 基础信息回填
  editForm.record_id = row.record_id
  editForm.student_id = row.student_id
  editForm.student_name = row.student_name
  editForm.course_id = row.course_id
  editForm.course_name = row.course_name
  editForm.exam_type = row.exam_type
  editForm.status = row.status

  // 回填分数 (注意：这里假设列表接口返回了所有分项，如果是null则显示为空)
  // 如果后端列表没有返回 attendance_score 等字段，需要修改后端 RemoteClientService.viewGrades
  const parse = (val) => val ? parseFloat(val) : null

  editForm.attendanceScore = parse(row.attendance_score)
  editForm.homeworkScore = parse(row.homework_score)
  editForm.experimentScore = parse(row.experiment_score)
  editForm.midtermScore = parse(row.midterm_score)
  editForm.dailyScore = parse(row.daily_score)
  editForm.finalScore = parse(row.final_score)
  editForm.makeupScore = parse(row.makeup_score)
  editForm.totalScore = parse(row.total_score)

  // ⚠️ 注意：由于数据库没存系数，这里我们根据“有分数的项”简单反推或维持默认值
  // 为了体验更好，如果某项有分，我们尽量保留系数；如果没有，维持默认。
  // 教师可以在弹窗里重新调整系数。

  editDialogVisible.value = true
}

const submitEdit = async () => {
  await doSubmit('DRAFT')
}

const submitAndCommit = async () => {
  await ElMessageBox.confirm('提交后将无法再次修改，确认提交吗？', '提示', { type: 'warning' })
  await doSubmit('SUBMITTED')
}

const doSubmit = async (status) => {
  if (editForm.exam_type === '正考' && totalRatio.value !== 100) {
    ElMessage.warning('系数总和必须为100%')
    return
  }

  editLoading.value = true
  try {
    const updateData = {
      status: status,
      total_score: editForm.totalScore.toString(),
      // 提交所有可能的分项
      attendance_score: editForm.attendanceScore,
      homework_score: editForm.homeworkScore,
      experiment_score: editForm.experimentScore,
      midterm_score: editForm.midtermScore,
      daily_score: editForm.dailyScore,
      final_score: editForm.finalScore,
      makeup_score: editForm.makeupScore
    }

    // 清理 null 值
    Object.keys(updateData).forEach(key => {
      if (updateData[key] === null || updateData[key] === undefined) delete updateData[key]
      else updateData[key] = updateData[key].toString()
    })

    const res = await request.post('/remote/client/grade/update', {
      recordId: String(editForm.record_id),
      data: updateData
    })

    if (res.code === 200) {
      ElMessage.success('操作成功')
      editDialogVisible.value = false
      handleQuery()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '系统异常')
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

/* 编辑弹窗样式 */
.score-mini-card {
  background: #f9fafc;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px;
  text-align: center;
  transition: all 0.3s;
}
.score-mini-card.highlight {
  background: #ecf5ff;
  border-color: #b3d8ff;
}
.score-mini-card .label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}
.flex-input {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
.sep { font-size: 12px; color: #c0c4cc; width: 15px;}

.ratio-warning {
  margin-top: 10px;
  font-size: 12px;
  color: #e6a23c;
  display: flex;
  align-items: center;
  gap: 5px;
}

.total-bar {
  margin-top: 20px;
  background: #f0f9eb;
  color: #67c23a;
  padding: 10px;
  border-radius: 4px;
  text-align: right;
  font-weight: bold;
}
.total-bar .num { font-size: 20px; }

.makeup-mode {
  padding: 20px;
  background: #fdf6ec;
  border-radius: 4px;
}
</style>