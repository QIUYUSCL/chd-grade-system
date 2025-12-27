<template>
  <div class="grade-view">
    <div class="header-actions no-print">
      <h2>成绩管理</h2>
    </div>

    <el-card class="query-card no-print" shadow="never">
      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="学期">
          <el-select v-model="queryForm.semester" placeholder="全部学期" clearable style="width: 160px" @change="handleSemesterChange">
            <el-option v-for="sem in uniqueSemesters" :key="sem" :label="sem" :value="sem" />
          </el-select>
        </el-form-item>

        <el-form-item label="课程">
          <el-select
              v-model="queryForm.courseId"
              placeholder="请选择课程"
              clearable
              filterable
              :loading="courseLoading"
              :disabled="courseLoading || courses.length === 0"
              style="width: 200px"
              @change="handleCourseChange"
          >
            <el-option
                v-for="course in filteredCourses"
                :key="course.course_id"
                :label="course.course_name"
                :value="course.course_id">
              <span style="float: left">{{ course.course_name }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="班级">
          <el-select
              v-model="queryForm.className"
              placeholder="全部班级"
              clearable
              :disabled="!queryForm.courseId"
              style="width: 140px"
          >
            <el-option v-for="cls in classOptions" :key="cls" :label="cls" :value="cls" />
          </el-select>
        </el-form-item>

        <el-form-item label="类型">
          <el-select v-model="queryForm.examType" placeholder="全部" clearable style="width: 100px">
            <el-option label="正考" value="正考" />
            <el-option label="补考" value="补考" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="queryForm.status" placeholder="全部" clearable style="width: 100px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已归档" value="SUBMITTED" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleQuery" :loading="loading" icon="Search">查询</el-button>
          <el-button @click="resetQuery" icon="Refresh">重置</el-button>
          <el-button type="success" @click="printGradeList" icon="Printer">打印成绩单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card no-print" shadow="hover">
      <el-table
          :data="gradeList"
          stripe
          style="width: 100%"
          v-loading="loading"
          :header-cell-style="{background:'#f5f7fa', color: '#000', fontWeight: 'bold'}"
      >
        <el-table-column prop="student_id" label="学号" width="110" fixed sortable />
        <el-table-column prop="student_name" label="姓名" width="90" fixed />
        <el-table-column prop="class_name" label="班级" width="120" sortable show-overflow-tooltip />
        <el-table-column prop="course_name" label="课程名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="credit" label="学分" width="60" align="center"><template #default="scope">{{ scope.row.credit || '3.0' }}</template></el-table-column>

        <el-table-column label="平时" width="60" align="center"><template #default="scope"><span class="sub-score">{{ formatScore(scope.row.daily_score) }}</span></template></el-table-column>
        <el-table-column label="期末" width="60" align="center"><template #default="scope"><span class="sub-score" style="font-weight: bold">{{ formatScore(scope.row.final_score) }}</span></template></el-table-column>

        <el-table-column prop="total_score" label="总成绩" width="80" align="center" sortable>
          <template #default="scope"><span :class="getScoreClass(scope.row.total_score)" class="main-score">{{ scope.row.total_score || '--' }}</span></template>
        </el-table-column>

        <el-table-column label="绩点" width="60" align="center">
          <template #default="scope"><span class="gpa-text">{{ calculateGPA(scope.row.total_score) }}</span></template>
        </el-table-column>

        <el-table-column prop="exam_type" label="类型" width="70" align="center">
          <template #default="scope"><el-tag :type="scope.row.exam_type === '正考' ? '' : 'warning'" effect="plain" size="small">{{ scope.row.exam_type }}</el-tag></template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="70" align="center">
          <template #default="scope"><el-tag :type="scope.row.status === 'SUBMITTED' ? 'success' : 'info'" effect="dark" size="small">{{ scope.row.status === 'DRAFT' ? '草稿' : '归档' }}</el-tag></template>
        </el-table-column>

        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="scope">
            <div v-if="scope.row.status === 'DRAFT'">
              <el-button type="primary" link size="small" @click="handleEdit(scope.row)" icon="Edit">修改</el-button>
              <el-popconfirm title="确定要撤销这条暂存成绩吗？" @confirm="handleRevoke(scope.row)" confirm-button-text="确认" cancel-button-text="取消">
                <template #reference><el-button type="danger" link size="small" icon="Delete">撤销</el-button></template>
              </el-popconfirm>
            </div>
            <span v-else style="color: #c0c4cc; font-size: 12px"><el-icon><Lock /></el-icon> 锁定</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper no-print">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, prev, pager, next, sizes" @size-change="handleSizeChange" @current-change="handleCurrentChange" background />
      </div>
    </el-card>

    <div id="print-section" class="print-layout">
      <div class="print-container">
        <h2 class="print-title">长安大学({{ queryForm.semester || '____-____' }}学期) 课程成绩登记表</h2>

        <div class="print-header-info">
          <table class="info-table">
            <tr>
              <td class="info-label">课程名称:</td>
              <td class="info-val" style="width: 35%">{{ printMeta.courseName }}</td>
              <td class="info-label">课程代码:</td>
              <td class="info-val">{{ printMeta.courseId }}</td>

              <td class="info-label">课程类别:</td>
              <td class="info-val">{{ printMeta.courseType }}</td>

              <td class="info-label">教师:</td>
              <td class="info-val">{{ printMeta.teacherName }}</td>
            </tr>
            <tr>
              <td class="info-label">班级名称:</td>
              <td class="info-val">{{ printMeta.classNamesStr }}</td>

              <td class="info-label">课程序号:</td>
              <td class="info-val">{{ printMeta.courseId ? printMeta.courseId + '.01' : '' }}</td>

              <td class="info-label">人数:</td>
              <td class="info-val">{{ printRows.length }}</td>

              <td class="info-label"></td>
              <td class="info-val"></td>
            </tr>
            <tr>
              <td class="info-label">院系:</td>
              <td class="info-val" colspan="7">{{ printMeta.department }}</td>

            </tr>
          </table>
        </div>

        <table class="print-table-grid">
          <thead>
          <tr>
            <th width="40">序号</th>
            <th width="90">学号</th>
            <th width="65">姓名</th>

            <th width="40">平时<br>成绩</th>
            <th width="40">考勤</th>
            <th width="40">作业</th>
            <th width="40">实验</th>
            <th width="40">期中</th>

            <th width="50">期末<br>成绩</th>
            <th width="50">毕业<br>前补<br>考</th>
            <th width="50">总评<br>成绩</th>
            <th>备注</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(row, index) in printRows" :key="index">
            <td class="center">{{ index + 1 }}</td>
            <td class="center" style="font-size: 10pt;">{{ row.student_id }}</td>
            <td class="center">{{ row.student_name }}</td>

            <td class="center">{{ formatPrintScore(row.daily_score) }}</td>
            <td class="center">{{ formatPrintScore(row.attendance_score) }}</td>
            <td class="center">{{ formatPrintScore(row.homework_score) }}</td>
            <td class="center">{{ formatPrintScore(row.experiment_score) }}</td>
            <td class="center">{{ formatPrintScore(row.midterm_score) }}</td>

            <td class="center">{{ formatPrintScore(row.final_score) }}</td>
            <td class="center">{{ row.exam_type === '补考' ? row.total_score : '' }}</td>
            <td class="center bold">{{ row.total_score }}</td>
            <td></td>
          </tr>
          </tbody>
        </table>

        <div class="print-footer-info">
          <span>统计人数: {{ printRows.length }}</span>
          <span style="margin-left: 200px">教师签名: ________________</span>
          <span style="float: right">成绩录入日期: {{ currentDate }}</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="editDialogVisible" title="修改成绩记录" width="650px" @close="resetEditForm" class="custom-dialog no-print" destroy-on-close>
      <el-descriptions :column="2" border size="small" class="mb-20">
        <el-descriptions-item label="姓名">{{ editForm.student_name }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ editForm.student_id }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ editForm.course_name }}</el-descriptions-item>
        <el-descriptions-item label="类型"><el-tag :type="editForm.exam_type === '正考' ? '' : 'warning'" size="small">{{ editForm.exam_type }}</el-tag></el-descriptions-item>
      </el-descriptions>

      <el-form :model="editForm" ref="editFormRef" label-position="top" class="edit-form">
        <div v-if="editForm.exam_type === '正考'">
          <el-divider content-position="left">正考成绩构成调整</el-divider>
          <el-row :gutter="15">
            <el-col :span="8"><div class="score-mini-card"><div class="label">平时成绩</div><div class="flex-input"><el-input-number v-model="editForm.dailyRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/><span class="sep">%</span><el-input-number v-model="editForm.dailyScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.dailyRatio===0"/></div></div></el-col>
            <el-col :span="8"><div class="score-mini-card"><div class="label">考勤</div><div class="flex-input"><el-input-number v-model="editForm.attendanceRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/><span class="sep">%</span><el-input-number v-model="editForm.attendanceScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.attendanceRatio===0"/></div></div></el-col>
            <el-col :span="8"><div class="score-mini-card"><div class="label">作业</div><div class="flex-input"><el-input-number v-model="editForm.homeworkRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/><span class="sep">%</span><el-input-number v-model="editForm.homeworkScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.homeworkRatio===0"/></div></div></el-col>
          </el-row>
          <el-row :gutter="15" style="margin-top: 10px;">
            <el-col :span="8"><div class="score-mini-card"><div class="label">实验</div><div class="flex-input"><el-input-number v-model="editForm.experimentRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/><span class="sep">%</span><el-input-number v-model="editForm.experimentScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.experimentRatio===0"/></div></div></el-col>
            <el-col :span="8"><div class="score-mini-card"><div class="label">期中</div><div class="flex-input"><el-input-number v-model="editForm.midtermRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/><span class="sep">%</span><el-input-number v-model="editForm.midtermScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" :disabled="editForm.midtermRatio===0"/></div></div></el-col>
            <el-col :span="8"><div class="score-mini-card highlight"><div class="label">期末考试</div><div class="flex-input"><el-input-number v-model="editForm.finalRatio" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/><span class="sep">%</span><el-input-number v-model="editForm.finalScore" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal"/></div></div></el-col>
          </el-row>
        </div>
        <div v-else class="makeup-mode">
          <el-form-item label="补考卷面成绩" prop="makeupScore">
            <el-input-number v-model="editForm.makeupScore" :min="0" :max="100" :precision="1" size="large" style="width: 100%" @change="calculateTotal" />
          </el-form-item>
        </div>
        <div class="result-panel">
          <div class="total-bar"><span class="txt">计算总评：</span><span class="num">{{ editForm.totalScore || 0 }}</span></div>
          <div class="gpa-bar"><span class="txt">预估绩点 (5分制)：</span><span class="num gpa-num" :class="getGPAClass(editForm.gpa)">{{ editForm.gpa || '0.0' }}</span></div>
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
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Edit, Lock, Delete, Printer } from '@element-plus/icons-vue'
import { getTeacherCourses, queryGrades, updateGrade, revokeGrade, getCourseStudents } from '@/api/teacher'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const queryForm = reactive({ semester: '', courseId: '', className: '', examType: '', status: '' })
const courses = ref([])
const courseLoading = ref(false)
const gradeList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const editDialogVisible = ref(false)
const editLoading = ref(false)
const classOptions = ref([])

// 打印专用数据
const printRows = ref([])
const printMeta = reactive({
  courseName: '',
  courseId: '',
  teacherName: '',
  courseType: '',
  department: '',
  classNamesStr: ''
})
const currentDate = new Date().toISOString().split('T')[0]

const editForm = reactive({ record_id: '', student_name: '', totalScore: null, finalScore: null, dailyRatio: 10, attendanceRatio: 0, homeworkRatio: 0, experimentRatio: 0, midtermRatio: 20, finalRatio: 70, makeupScore: null, dailyScore: null, attendanceScore: null, homeworkScore: null, experimentScore: null, midtermScore: null, gpa: '0.0' })
const editFormRef = ref()

const uniqueSemesters = computed(() => {
  if (!courses.value || courses.value.length === 0) return []
  const all = courses.value.map(c => c.semester).filter(s => s)
  return Array.from(new Set(all)).sort().reverse()
})
const filteredCourses = computed(() => {
  if (!queryForm.semester) return courses.value
  return courses.value.filter(c => c.semester === queryForm.semester)
})
const handleSemesterChange = () => { queryForm.courseId = ''; queryForm.className = ''; classOptions.value = [] }
const handleCourseChange = async (val) => {
  queryForm.className = ''; classOptions.value = []
  if (!val || !queryForm.semester) return
  try {
    const res = await getCourseStudents(val, queryForm.semester)
    if (res.code === 200 && res.data) {
      const classes = res.data.map(s => s.class_name).filter(c => c)
      classOptions.value = [...new Set(classes)].sort()
    }
  } catch (e) { console.error(e) }
}

const formatScore = (val) => (val === null || val === undefined || val === '--' || String(val) === 'NaN') ? '' : val
const formatPrintScore = (val) => formatScore(val) // 打印用

// ✅ 修改：计算绩点逻辑 (低于60分直接归0，不出现负数)
const calculateGPA = (score) => {
  const val = parseFloat(score)
  if (!score || isNaN(val) || val < 60) {
    return '0.0'
  }
  return ((val - 50) / 10).toFixed(1)
}

const getScoreClass = (s) => {
  if (s === null || s === undefined || s === '--') return ''
  const val = parseFloat(s)
  if (isNaN(val)) return ''

  if (val >= 90) return 'score-success' // 大于等于90分优秀
  if (val < 60) return 'score-danger'   // 小于60分警告
  return ''
}

const getGPAClass = (g) => (!g || parseFloat(g)>0) ? '' : 'score-danger'

// 打印数据准备
const printGradeList = async () => {
  try {
    loading.value = true
    const params = {
      semester: queryForm.semester,
      courseId: queryForm.courseId,
      className: queryForm.className,
      examType: queryForm.examType,
      status: queryForm.status,
      page: 1,
      pageSize: 10000 // 查全部
    }
    const res = await queryGrades(params)
    if (res.code === 200) {
      printRows.value = res.data.list || []
    }
  } catch(e) {
    ElMessage.error('获取打印数据失败')
    return
  } finally {
    loading.value = false
  }

  const currentCourse = courses.value.find(c => c.course_id === queryForm.courseId)

  // 1. 设置基本信息
  printMeta.courseName = currentCourse ? currentCourse.course_name : '未命名课程'
  printMeta.courseId = queryForm.courseId || '______'
  printMeta.teacherName = userStore.userInfo.name || '教师'

  // 2. 从数据库字段读取
  printMeta.courseType = (currentCourse && currentCourse.course_type) ? currentCourse.course_type : '专业发展课程'
  printMeta.department = (currentCourse && currentCourse.department) ? currentCourse.department : '信息工程学院'

  // 3. 设置班级名称
  if (queryForm.className) {
    printMeta.classNamesStr = queryForm.className
  } else {
    // 提取所有学生的班级并去重
    const allClasses = [...new Set(printRows.value.map(row => row.class_name).filter(c => c))]
    printMeta.classNamesStr = allClasses.join(' ') || '多班级合班'
  }

  nextTick(() => {
    window.print()
  })
}

// 常规查询
const handleQuery = async () => {
  loading.value = true
  try {
    const params = {
      semester: queryForm.semester, courseId: queryForm.courseId, className: queryForm.className,
      examType: queryForm.examType, status: queryForm.status, page: currentPage.value, pageSize: pageSize.value
    }
    const res = await queryGrades(params)
    if (res.code === 200) {
      gradeList.value = res.data.list || []
      total.value = res.data.total || 0
      ElMessage.success(`查询成功，共${total.value}条`)
    } else { ElMessage.error(res.message) }
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

const resetQuery = () => { queryForm.semester = ''; handleSemesterChange(); currentPage.value = 1; handleQuery(); }
const handleSizeChange = (val) => { pageSize.value = val; handleQuery() }
const handleCurrentChange = (val) => { currentPage.value = val; handleQuery() }

// 编辑逻辑
const resetEditForm = () => {
  Object.assign(editForm, { record_id: '', student_name: '', totalScore: null, finalScore: null, dailyRatio: 10, attendanceRatio: 0, homeworkRatio: 0, experimentRatio: 0, midtermRatio: 20, finalRatio: 70, makeupScore: null, gpa: '0.0' })
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

  const parse = (val) => (val !== null && val !== undefined && val !== '') ? parseFloat(val) : null

  editForm.attendanceScore = parse(row.attendance_score)
  editForm.homeworkScore = parse(row.homework_score)
  editForm.experimentScore = parse(row.experiment_score)
  editForm.midtermScore = parse(row.midterm_score)
  editForm.dailyScore = parse(row.daily_score)
  editForm.finalScore = parse(row.final_score)
  editForm.makeupScore = parse(row.makeup_score)
  editForm.totalScore = parse(row.total_score)
  editForm.gpa = calculateGPA(editForm.totalScore)
  editDialogVisible.value = true
}
const calculateTotal = () => {
  let scoreVal = 0
  if (editForm.exam_type === '补考') {
    scoreVal = editForm.makeupScore || 0
  } else {
    let total = 0
    const add = (score, ratio) => { if ((ratio || 0) > 0 && score != null) total += score * (ratio / 100) }
    add(editForm.attendanceScore, editForm.attendanceRatio)
    add(editForm.homeworkScore, editForm.homeworkRatio)
    add(editForm.experimentScore, editForm.experimentRatio)
    add(editForm.midtermScore, editForm.midtermRatio)
    add(editForm.dailyScore, editForm.dailyRatio)
    add(editForm.finalScore, editForm.finalRatio)
    scoreVal = Math.round(total * 10) / 10
  }
  editForm.totalScore = scoreVal
  editForm.gpa = calculateGPA(scoreVal)
}
const isFormValid = computed(() => {
  if (editForm.exam_type === '补考') return editForm.makeupScore != null
  return editForm.finalScore != null && editForm.totalScore != null
})
const submitEdit = async () => { await doSubmit('DRAFT') }
const submitAndCommit = async () => { await ElMessageBox.confirm('提交后锁定，确认提交吗？', '提示', { type: 'warning' }); await doSubmit('SUBMITTED') }
const doSubmit = async (status) => {
  editLoading.value = true
  try {
    const updateData = { status: status, total_score: editForm.totalScore.toString(), attendance_score: editForm.attendanceScore, homework_score: editForm.homeworkScore, experiment_score: editForm.experimentScore, midterm_score: editForm.midtermScore, daily_score: editForm.dailyScore, final_score: editForm.finalScore, makeup_score: editForm.makeupScore }
    Object.keys(updateData).forEach(key => { if (updateData[key] == null) delete updateData[key]; else updateData[key] = updateData[key].toString() })
    const res = await updateGrade(String(editForm.record_id), updateData)
    if (res.code === 200) { ElMessage.success('操作成功'); editDialogVisible.value = false; handleQuery() }
    else { ElMessage.error(res.message) }
  } catch (e) { if(e!=='cancel') ElMessage.error(e.message) }
  finally { editLoading.value = false }
}
const handleRevoke = async (row) => {
  try {
    loading.value = true
    const res = await revokeGrade(row.record_id)
    if (res.code === 200) { ElMessage.success('撤销成功'); handleQuery() }
    else { ElMessage.error(res.message) }
  } catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}

onMounted(() => { loadTeacherCourses(); handleQuery() })
const loadTeacherCourses = async () => {
  const res = await getTeacherCourses()
  if(res.code===200) courses.value = res.data
}
</script>

<style scoped>
/* 屏幕样式 */
.grade-view { padding: 20px; }
.header-actions h2 { margin: 0 0 20px; font-size: 22px; color: #303133; }
.query-form .el-form-item { margin-bottom: 0; }
.sub-score { font-size: 13px; color: #606266; }
.main-score { font-weight: bold; font-size: 14px; }

/* ✅ 修改：增加优秀和警告的样式 */
.score-success { color: #67c23a; font-weight: bold; } /* 绿色 */
.score-danger { color: #f56c6c; font-weight: bold; }  /* 红色 */

.pagination-wrapper { margin-top: 20px; display: flex; justify-content: flex-end; }
.mb-20 { margin-bottom: 20px; }
.score-mini-card { background: #f9fafc; border: 1px solid #e4e7ed; border-radius: 4px; padding: 8px; text-align: center; }
.score-mini-card.highlight { background: #ecf5ff; border-color: #b3d8ff; }
.score-mini-card .label { font-size: 12px; color: #909399; margin-bottom: 5px; }
.flex-input { display: flex; align-items: center; justify-content: center; gap: 2px; }
.sep { font-size: 12px; color: #c0c4cc; width: 15px;}
.result-panel { margin-top: 20px; background: #fcfcfc; border: 1px solid #ebeef5; border-radius: 4px; }
.total-bar, .gpa-bar { padding: 10px 15px; display: flex; justify-content: space-between; align-items: center; }
.total-bar { border-bottom: 1px dashed #ebeef5; background: #f0f9eb; color: #67c23a; }
.total-bar .num { font-size: 20px; font-weight: bold; }

/* 默认隐藏打印区域 */
#print-section { display: none; }

/* ================== 打印专用样式 ================== */
@media print {
  body * { visibility: hidden; }
  .no-print, .el-header, .el-aside, .sidebar, .header, .el-dialog, .v-modal { display: none !important; }

  #print-section {
    display: block !important;
    visibility: visible !important;
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 100%;
    background: white;
    z-index: 9999;
    font-family: "SimSun", "宋体", serif; /* 强制宋体 */
    color: #000;
  }
  #print-section * { visibility: visible !important; }

  .print-container { width: 100%; padding: 0 5mm; }

  /* 标题 */
  .print-title {
    text-align: center;
    font-size: 18pt;
    font-weight: bold;
    margin: 5mm 0;
  }

  /* 表头信息区域 */
  .print-header-info { margin-bottom: 2mm; font-size: 10.5pt; }
  .info-table { width: 100%; border-collapse: collapse; border: none; }
  .info-table td { padding: 2px 0; border: none; }
  .info-label { white-space: nowrap; font-weight: bold; width: 1%; padding-right: 5px; }
  .info-val { white-space: nowrap; padding-right: 15px; border-bottom: 1px solid #ccc; min-width: 50px; }

  /* 核心表格样式 (单栏) */
  .print-table-grid {
    width: 100%;
    border-collapse: collapse;
    border: 1.5px solid #000;
    font-size: 10.5pt; /* 五号字 */
    text-align: center;
  }

  .print-table-grid th,
  .print-table-grid td {
    border: 1px solid #000;
    padding: 4px 2px;
    height: 7mm; /* 稍微调高行高 */
  }

  /* 自动分页表头 */
  thead { display: table-header-group; }
  tfoot { display: table-footer-group; }

  .center { text-align: center; }
  .bold { font-weight: bold; }

  /* 页脚 */
  .print-footer-info {
    margin-top: 5mm;
    font-size: 10.5pt;
    display: flex;
    justify-content: space-between;
  }

  @page {
    size: A4 portrait; /* 纵向打印 */
    margin: 10mm 10mm 10mm 15mm; /* 左边距稍大供装订 */
  }
}
</style>