<template>
  <div class="grade-view" id="grade-list-page">
    <div class="header-actions no-print">
      <h2>成绩管理</h2>
    </div>

    <el-card class="query-card no-print" shadow="never">
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
          <el-button type="success" @click="printGradeList" icon="Printer">打印成绩单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="print-title show-in-print">
      <h1>课程成绩单</h1>
      <p>课程：{{ getCourseName(queryForm.courseId) || '所有课程' }} &nbsp;&nbsp; 学期：{{ queryForm.semester || '全部' }}</p>
    </div>

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
            <el-tag :type="scope.row.exam_type === '正考' ? '' : 'warning'" effect="plain" size="small" class="no-print">
              {{ scope.row.exam_type }}
            </el-tag>
            <span class="show-in-print">{{ scope.row.exam_type }}</span> </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'SUBMITTED' ? 'success' : 'info'" effect="dark" size="small" class="no-print">
              {{ scope.row.status === 'DRAFT' ? '草稿' : '已归档' }}
            </el-tag>
            <span class="show-in-print">{{ scope.row.status === 'DRAFT' ? '草稿' : '已归档' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="semester" label="学期" width="140" />

        <el-table-column label="操作" width="150" fixed="right" align="center" class-name="no-print-col">
          <template #default="scope">
            <div v-if="scope.row.status === 'DRAFT'">
              <el-button type="primary" link size="small" @click="handleEdit(scope.row)" icon="Edit">修改</el-button>
              <el-popconfirm title="确定要撤销这条暂存成绩吗？" @confirm="handleRevoke(scope.row)" confirm-button-text="确认" cancel-button-text="取消">
                <template #reference>
                  <el-button type="danger" link size="small" icon="Delete">撤销</el-button>
                </template>
              </el-popconfirm>
            </div>
            <span v-else style="color: #c0c4cc; font-size: 12px"><el-icon><Lock /></el-icon> 已归档</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper no-print">
        <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, prev, pager, next, sizes"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            background
        />
      </div>
    </el-card>

    <el-dialog v-model="editDialogVisible" title="修改成绩记录" width="650px" @close="resetEditForm" class="custom-dialog no-print">
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
                  <el-input-number v-model="editForm.attendanceRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/>
                  <span class="sep">%</span>
                  <el-input-number v-model="editForm.attendanceScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.attendanceRatio===0"/>
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
        </div>
        <div v-else class="makeup-mode">
          <el-form-item label="补考卷面成绩" prop="makeupScore">
            <el-input-number v-model="editForm.makeupScore" :min="0" :max="100" :precision="1" size="large" style="width: 100%" @change="calculateTotal" />
          </el-form-item>
        </div>
        <div class="total-bar">
          <span class="txt">计算总评：</span>
          <span class="num" :class="getScoreClass(editForm.totalScore)">{{ editForm.totalScore }}</span>
        </div>
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
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { Calendar, Search, Refresh, Edit, Lock, Delete, Printer } from '@element-plus/icons-vue' // 引入图标

const queryForm = reactive({ semester: '', courseId: '' })
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
  record_id: '', student_id: '', student_name: '', course_id: '', course_name: '', exam_type: '',
  attendanceRatio: 10, attendanceScore: null,
  homeworkRatio: 20, homeworkScore: null,
  experimentRatio: 0, experimentScore: null,
  midtermRatio: 0, midtermScore: null,
  dailyRatio: 0, dailyScore: null,
  finalRatio: 70, finalScore: null,
  makeupScore: null, totalScore: null
})

// [新增] 打印方法
const printGradeList = () => {
  window.print()
}

const getCourseName = (id) => {
  const c = courses.value.find(i => i.course_id === id)
  return c ? c.course_name : id
}

// ... (以下逻辑代码与之前完全一致：loadTeacherCourses, handleQuery, handleEdit, submitEdit 等) ...
// 请保留您现有的所有 JS 逻辑，只需添加 printGradeList 和 getCourseName 即可
const isFormValid = computed(() => {
  if (editForm.exam_type === '补考') return editForm.makeupScore != null
  return editForm.finalScore != null && editForm.totalScore != null
})

const calculateTotal = () => {
  if (editForm.exam_type === '补考') {
    editForm.totalScore = editForm.makeupScore
  } else {
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
    if (res.code === 200 && res.data) courses.value = res.data
  } finally { courseLoading.value = false }
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
    } else { ElMessage.error(res.message || '查询失败') }
  } catch (error) { ElMessage.error('查询异常: ' + error.message) }
  finally { loading.value = false }
}

const resetQuery = () => { queryForm.semester = ''; queryForm.courseId = ''; currentPage.value = 1; handleQuery() }
const handleSizeChange = (val) => { pageSize.value = val; handleQuery() }
const handleCurrentChange = (val) => { currentPage.value = val; handleQuery() }

const getScoreClass = (score) => {
  if (!score) return ''
  const num = parseFloat(score)
  if (num < 60) return 'score-danger'
  return 'score-success'
}

const resetEditForm = () => {
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

const handleEdit = (row) => {
  resetEditForm()
  editForm.record_id = row.record_id
  editForm.student_id = row.student_id
  editForm.student_name = row.student_name
  editForm.course_id = row.course_id
  editForm.course_name = row.course_name
  editForm.exam_type = row.exam_type
  editForm.status = row.status
  const parse = (val) => val ? parseFloat(val) : null
  editForm.attendanceScore = parse(row.attendance_score)
  editForm.homeworkScore = parse(row.homework_score)
  editForm.experimentScore = parse(row.experiment_score)
  editForm.midtermScore = parse(row.midterm_score)
  editForm.dailyScore = parse(row.daily_score)
  editForm.finalScore = parse(row.final_score)
  editForm.makeupScore = parse(row.makeup_score)
  editForm.totalScore = parse(row.total_score)
  editDialogVisible.value = true
}

const handleRevoke = async (row) => {
  try {
    loading.value = true
    const res = await request.post('/remote/client/grade/revoke', { recordId: row.record_id })
    if (res.code === 200) { ElMessage.success('撤销成功'); handleQuery() }
    else { ElMessage.error(res.message || '撤销失败') }
  } catch (error) { ElMessage.error(error.message || '操作异常') }
  finally { loading.value = false }
}

const submitEdit = async () => { await doSubmit('DRAFT') }
const submitAndCommit = async () => { await ElMessageBox.confirm('提交后锁定，确认提交吗？', '提示', { type: 'warning' }); await doSubmit('SUBMITTED') }

const doSubmit = async (status) => {
  editLoading.value = true
  try {
    const updateData = {
      status: status,
      total_score: editForm.totalScore.toString(),
      attendance_score: editForm.attendanceScore,
      homework_score: editForm.homeworkScore,
      experiment_score: editForm.experimentScore,
      midterm_score: editForm.midtermScore,
      daily_score: editForm.dailyScore,
      final_score: editForm.finalScore,
      makeup_score: editForm.makeupScore
    }
    Object.keys(updateData).forEach(key => { if (updateData[key] === null) delete updateData[key]; else updateData[key] = updateData[key].toString() })
    const res = await request.post('/remote/client/grade/update', { recordId: String(editForm.record_id), data: updateData })
    if (res.code === 200) { ElMessage.success('操作成功'); editDialogVisible.value = false; handleQuery() }
    else { ElMessage.error(res.message || '操作失败') }
  } catch (e) { if(e!=='cancel') ElMessage.error(e.message) }
  finally { editLoading.value = false }
}

onMounted(() => { loadTeacherCourses(); handleQuery() })
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

/* 弹窗样式 */
.score-mini-card { background: #f9fafc; border: 1px solid #e4e7ed; border-radius: 4px; padding: 8px; text-align: center; }
.score-mini-card.highlight { background: #ecf5ff; border-color: #b3d8ff; }
.score-mini-card .label { font-size: 12px; color: #909399; margin-bottom: 5px; }
.flex-input { display: flex; align-items: center; justify-content: center; gap: 2px; }
.sep { font-size: 12px; color: #c0c4cc; width: 15px;}
.total-bar { margin-top: 20px; background: #f0f9eb; color: #67c23a; padding: 10px; border-radius: 4px; text-align: right; font-weight: bold; }
.total-bar .num { font-size: 20px; }
.makeup-mode { padding: 20px; background: #fdf6ec; border-radius: 4px; }

/* ================== [核心] 打印专用样式 ================== */
@media print {
  /* 1. 隐藏多余元素：侧边栏、查询卡片、分页、操作列、按钮 */
  .no-print, .query-card, .pagination-wrapper, .header-actions, .el-dialog {
    display: none !important;
  }

  /* 隐藏表格的操作列 (最后一列) */
  .el-table__fixed-right { display: none !important; }
  .el-table th:last-child, .el-table td:last-child { display: none !important; }

  /* 2. 显示打印专用元素 */
  .show-in-print { display: block !important; }

  /* 3. 布局调整：全屏显示表格 */
  .grade-view {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    margin: 0;
    padding: 20px;
    background: white;
    z-index: 9999;
  }

  /* 打印标题样式 */
  .print-title { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #333; padding-bottom: 10px; }
  .print-title h1 { font-size: 24px; margin: 0 0 10px 0; }
  .print-title p { font-size: 14px; margin: 0; }

  /* 表格样式优化 */
  .el-card { box-shadow: none !important; border: none !important; }
  .el-table { border: 1px solid #000 !important; font-size: 12px; }
  .el-table th, .el-table td { border-bottom: 1px solid #000 !important; border-right: 1px solid #000 !important; padding: 5px 0 !important; color: #000 !important; }
}

/* 默认隐藏打印标题 */
.show-in-print { display: none; }
</style>