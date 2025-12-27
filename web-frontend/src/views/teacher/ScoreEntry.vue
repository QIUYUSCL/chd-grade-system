<template>
  <div class="score-entry">
    <div class="page-header">
      <h2>成绩录入</h2>
      <p class="sub-title">请选择录入模式，设置系数后即可录入。</p>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="entry-tabs">
      <el-tab-pane label="单条录入" name="single">
        <el-form :model="form" :rules="rules" ref="formRef" label-position="top" size="large">
          <el-row :gutter="24">
            <el-col :span="8">
              <el-form-item label="学期" prop="semester">
                <el-select v-model="form.semester" placeholder="选择学期" style="width: 100%">
                  <el-option v-for="sem in uniqueSemesters" :key="sem" :label="sem" :value="sem" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="课程" prop="courseId">
                <el-select v-model="form.courseId" placeholder="请选择课程" clearable filterable :loading="courseLoading" :disabled="courseLoading || !form.studentId" style="width: 100%">
                  <el-option v-for="course in selectedCourses" :key="course.course_id" :label="course.course_name" :value="course.course_id">
                    <span style="float: left">{{ course.course_name }}</span>
                  </el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="学生学号" prop="studentId">
                <el-input v-model="form.studentId" placeholder="输入学号" @blur="fetchStudentName" clearable>
                  <template #suffix><span v-if="form.studentName" style="color: #67c23a; font-weight: bold; margin-right: 5px">{{ form.studentName }}</span></template>
                </el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="考试类型" prop="examType">
            <el-radio-group v-model="form.examType" fill="#1890ff" @change="calculateTotal">
              <el-radio-button label="正考">正考录入</el-radio-button>
              <el-radio-button label="补考">补考录入</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-divider content-position="left"><el-icon><EditPen /></el-icon> 成绩配置与录入</el-divider>
          <div class="score-input-area">
            <div v-if="form.examType === '正考'">
              <el-alert v-if="totalRatio !== 100" :title="`当前系数总和 ${totalRatio}%，需为100%`" type="warning" show-icon style="margin-bottom:15px"/>
              <el-row :gutter="20">
                <el-col :span="8" v-for="item in scoreConfigItems" :key="item.key">
                  <div class="score-item-card" :class="{ disabled: form[item.ratioKey] === 0, highlight: item.key === 'final' }">
                    <div class="label">{{ item.label }}</div>
                    <div class="inputs">
                      <el-input-number v-model="form[item.ratioKey]" :min="0" :max="100" size="small" :controls="false" @change="calculateTotal" placeholder="系数"/>
                      <span class="symbol">%</span>
                      <el-input-number v-model="form[item.scoreKey]" :min="0" :max="100" :precision="1" :controls="false" :disabled="form[item.ratioKey] === 0" @change="calculateTotal" placeholder="得分"/>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </div>
            <div v-else class="makeup-area">
              <el-alert title="补考总成绩直接取卷面分" type="info" :closable="false" show-icon />
              <el-form-item label="补考卷面成绩" prop="makeupScore" style="margin-top:15px">
                <el-input-number v-model="form.makeupScore" :min="0" :max="100" :precision="1" @change="calculateTotal"/>
              </el-form-item>
            </div>
          </div>
          <div class="total-score-bar">
            <div class="score-group"><span class="label">总评：</span><span class="value">{{ form.totalScore !== null ? form.totalScore : '--' }}</span></div>
            <div class="score-group gpa-group"><span class="label">绩点：</span><span class="value">{{ form.gpa }}</span></div>
          </div>
          <div class="form-actions">
            <el-button @click="handleReset" :icon="Refresh">重置</el-button>
            <el-button type="primary" @click="handleSubmit('DRAFT')" :loading="loading" plain :icon="Document">暂存</el-button>
            <el-button type="success" @click="handleSubmit('SUBMITTED')" :loading="loading" :icon="Select" :disabled="form.examType === '正考' && totalRatio !== 100">提交</el-button>
          </div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="批量录入" name="batch">
        <div class="batch-header">
          <el-descriptions border :column="4" size="small" title="1. 录入配置">
            <el-descriptions-item label="学期">
              <el-select v-model="form.semester" placeholder="选择学期" size="small" style="width: 130px" @change="handleBatchSemesterChange">
                <el-option v-for="sem in uniqueSemesters" :key="sem" :label="sem" :value="sem" />
              </el-select>
            </el-descriptions-item>
            <el-descriptions-item label="课程">
              <el-select v-model="form.courseId" placeholder="请选择课程" size="small" @change="batchList = []" no-data-text="该学期无课程">
                <el-option v-for="c in filteredBatchCourses" :key="c.course_id" :label="c.course_name" :value="c.course_id" />
              </el-select>
            </el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-radio-group v-model="form.examType" size="small" @change="recalcAllBatchRows">
                <el-radio-button label="正考">正考</el-radio-button>
                <el-radio-button label="补考">补考</el-radio-button>
              </el-radio-group>
            </el-descriptions-item>
            <el-descriptions-item label="系数配置" v-if="form.examType === '正考'">
              <el-popover placement="bottom" title="全局系数设置" :width="300" trigger="click">
                <template #reference><el-button size="small" type="primary" link>配置 (当前:{{ totalRatio }}%)</el-button></template>
                <div class="ratio-popover">
                  <div v-for="item in scoreConfigItems" :key="item.key" class="ratio-row"><span>{{ item.label }}</span><el-input-number v-model="form[item.ratioKey]" size="small" :min="0" :max="100" @change="recalcAllBatchRows" />%</div>
                </div>
              </el-popover>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="batch-loader" style="margin: 20px 0; display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
          <el-button type="primary" @click="loadBatchStudents" :loading="batchLoading" :icon="Search" :disabled="!form.courseId">1. 加载学生名单</el-button>
          <span class="divider-vertical">|</span>
          <el-button type="warning" plain :icon="Download" @click="downloadTemplate" :disabled="batchList.length === 0">2. 导出录入模板</el-button>
          <el-upload class="upload-excel" action="" :auto-upload="false" :show-file-list="false" :on-change="handleExcelImport" accept=".xlsx, .xls" :disabled="batchList.length === 0">
            <el-button type="success" plain :icon="Upload" :disabled="batchList.length === 0">3. Excel 导入成绩</el-button>
          </el-upload>
          <el-popconfirm title="确定要清空表格中已填写的成绩吗？" @confirm="resetBatchScores">
            <template #reference><el-button type="danger" plain :icon="Delete" :disabled="batchList.length === 0">一键清零</el-button></template>
          </el-popconfirm>
        </div>

        <el-table :data="batchList" border stripe height="500" style="width: 100%" v-if="batchList.length > 0">
          <el-table-column prop="student_id" label="学号" width="120" fixed />
          <el-table-column prop="name" label="姓名" width="100" fixed />
          <template v-if="form.examType === '正考'">
            <el-table-column v-for="item in activeScoreItems" :key="item.key" :label="`${item.label}(${form[item.ratioKey]}%)`" width="110" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row[item.scoreKey]" size="small" :min="0" :max="100" :precision="1" :controls="false" style="width: 100%" @change="calcRowTotal(row)" />
              </template>
            </el-table-column>
          </template>
          <template v-else>
            <el-table-column label="补考卷面" width="150" align="center">
              <template #default="{ row }">
                <el-input-number v-model="row.makeupScore" size="small" :min="0" :max="100" :precision="1" @change="calcRowTotal(row)" />
              </template>
            </el-table-column>
          </template>
          <el-table-column label="总评" width="90" fixed="right" align="center">
            <template #default="{ row }"><span :style="{ fontWeight: 'bold', color: row.totalScore < 60 ? '#f56c6c' : '#67c23a' }">{{ row.totalScore ?? '--' }}</span></template>
          </el-table-column>
          <el-table-column label="绩点" width="80" fixed="right" align="center">
            <template #default="{ row }"><span style="color: #909399">{{ row.gpa ?? '0.0' }}</span></template>
          </el-table-column>
        </el-table>

        <div class="batch-footer" v-if="batchList.length > 0">
          <div class="count-info">共 {{ batchList.length }} 名学生</div>
          <div>
            <el-button @click="batchList = []">清空列表</el-button>
            <el-button type="primary" plain @click="submitBatch('DRAFT')" :loading="loading" :icon="Document">批量暂存</el-button>
            <el-button type="success" @click="submitBatch('SUBMITTED')" :loading="loading" :icon="Select">批量提交归档</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { EditPen, Refresh, Document, Select, Search, Download, Upload, Delete } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
// ✅ 引入 API
import {
  getTeacherCourses,
  getStudentName,
  getCourseStudents,
  entryGrade,
  batchEntryGrade,
  getStudentSelectedCourses
} from '@/api/teacher'

const selectedCourses = ref([])
const activeTab = ref('single')
const loading = ref(false)
const courseLoading = ref(false)
const courses = ref([])
const formRef = ref()
const batchLoading = ref(false)
const batchList = ref([])

const form = reactive({
  semester: '2024-2025-1',
  courseId: '',
  studentId: '',
  studentName: '',
  examType: '正考',
  dailyRatio: 10,       dailyScore: null,
  attendanceRatio: 0, attendanceScore: null,
  homeworkRatio: 0,   homeworkScore: null,
  experimentRatio: 0,  experimentScore: null,
  midtermRatio: 20,     midtermScore: null,
  finalRatio: 70,      finalScore: null,
  makeupScore: null,
  totalScore: null,
  gpa: '0.0'
})

const scoreConfigItems = [
  { key: 'daily',  label: '平时', ratioKey: 'dailyRatio',      scoreKey: 'dailyScore' },
  { key: 'attend', label: '考勤', ratioKey: 'attendanceRatio', scoreKey: 'attendanceScore' },
  { key: 'hw',     label: '作业', ratioKey: 'homeworkRatio',   scoreKey: 'homeworkScore' },
  { key: 'exp',    label: '实验', ratioKey: 'experimentRatio', scoreKey: 'experimentScore' },
  { key: 'mid',    label: '期中', ratioKey: 'midtermRatio',    scoreKey: 'midtermScore' },
  { key: 'final',  label: '期末', ratioKey: 'finalRatio',      scoreKey: 'finalScore' },
]

const activeScoreItems = computed(() => scoreConfigItems.filter(item => form[item.ratioKey] > 0))
const totalRatio = computed(() => form.examType === '补考' ? 100 : scoreConfigItems.reduce((sum, item) => sum + (form[item.ratioKey] || 0), 0))

const uniqueSemesters = computed(() => {
  if (!courses.value || courses.value.length === 0) return []
  const all = courses.value.map(c => c.semester).filter(s => s)
  return Array.from(new Set(all)).sort().reverse()
})

const filteredBatchCourses = computed(() => {
  if (!courses.value || courses.value.length === 0) return []
  return courses.value.filter(c => c.semester === form.semester)
})

const rules = {
  semester: [{ required: true, message: '必填', trigger: 'change' }],
  courseId: [{ required: true, message: '必填', trigger: 'change' }],
  studentId: [{ required: true, message: '必填', trigger: 'blur' }]
}

const handleBatchSemesterChange = () => {
  form.courseId = ''
  batchList.value = []
}

const calcGPA = (score) => {
  if (score == null || score === '') return '0.0'
  const s = parseFloat(score)
  if (isNaN(s)) return '0.0'
  if (s < 60) return '0.0'
  let gpa = (s - 50) / 10
  if (gpa > 5.0) gpa = 5.0
  return gpa.toFixed(1)
}

const calcScoreLogic = (dataContext, type) => {
  if (type === '补考') {
    const raw = Number(dataContext.makeupScore) || 0
    return raw >= 60 ? 60 : raw
  }
  let total = 0
  scoreConfigItems.forEach(item => {
    const score = Number(dataContext[item.scoreKey]) || 0
    const ratio = form[item.ratioKey] || 0
    if (ratio > 0 && score != null) total += score * (ratio / 100)
  })
  return Math.round(total * 10) / 10
}

const calculateTotal = () => {
  form.totalScore = calcScoreLogic(form, form.examType)
  form.gpa = calcGPA(form.totalScore)
}

const calcRowTotal = (row) => {
  row.totalScore = calcScoreLogic(row, form.examType)
  row.gpa = calcGPA(row.totalScore)
}

const recalcAllBatchRows = () => {
  batchList.value.forEach(row => calcRowTotal(row))
  calculateTotal()
}

watch(() => form.examType, () => calculateTotal(), { immediate: true })

onMounted(() => loadTeacherCourses())

const loadTeacherCourses = async () => {
  courseLoading.value = true
  try {
    const res = await getTeacherCourses() // ✅ 使用 API
    if (res.code === 200 && res.data) {
      courses.value = res.data
      if (uniqueSemesters.value.length > 0) {
        form.semester = uniqueSemesters.value[0]
      }
    }
  } finally { courseLoading.value = false }
}

const fetchStudentName = async () => {
  if (!form.studentId) return
  try {
    const nameRes = await getStudentName(form.studentId) // ✅ 使用 API
    form.studentName = nameRes.code === 200 ? nameRes.data : '未找到'
  } catch {
    form.studentName = '查询失败'
  }
  await loadSelectedCourses()
}

const loadSelectedCourses = async () => {
  if (!form.studentId || !form.semester) return
  courseLoading.value = true
  try {
    const res = await getStudentSelectedCourses(form.studentId, form.semester) // ✅ 使用 API
    selectedCourses.value = res.data || []
    if (res.code !== 200) ElMessage.warning(res.message || '暂无选课记录')
  } catch (e) {
    ElMessage.error('选课查询失败：' + e.message)
    selectedCourses.value = []
  } finally {
    courseLoading.value = false
  }
}

const loadBatchStudents = async () => {
  if (!form.courseId) return ElMessage.warning('请先选择课程')
  batchLoading.value = true
  try {
    const res = await getCourseStudents(form.courseId, form.semester) // ✅ 使用 API
    if (res.code === 200) {
      batchList.value = res.data.map(stu => ({
        ...stu,
        attendanceScore: null, homeworkScore: null, experimentScore: null,
        midtermScore: null, dailyScore: null, finalScore: null,
        makeupScore: null, totalScore: null, gpa: '0.0'
      }))
      if (!batchList.value.length) ElMessage.warning('该课程暂无学生选课')
      else ElMessage.success(`加载了 ${batchList.value.length} 名学生`)
    } else ElMessage.error(res.message)
  } catch (e) { ElMessage.error(e.message) }
  finally { batchLoading.value = false }
}

// 导出模板
const downloadTemplate = () => {
  if (batchList.value.length === 0) return ElMessage.warning('请先加载学生名单')
  const headers = {}
  headers['学号'] = 'student_id'
  headers['姓名'] = 'name'
  if (form.examType === '正考') {
    activeScoreItems.value.forEach(item => {
      headers[item.label] = item.scoreKey
    })
  } else {
    headers['补考卷面'] = 'makeupScore'
  }
  const data = batchList.value.map(student => {
    const row = {}
    row['学号'] = student.student_id
    row['姓名'] = student.name
    if (form.examType === '正考') {
      activeScoreItems.value.forEach(item => {
        row[item.label] = student[item.scoreKey] || ''
      })
    } else {
      row['补考卷面'] = student.makeupScore || ''
    }
    return row
  })
  const ws = XLSX.utils.json_to_sheet(data)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, "成绩录入模板")
  XLSX.writeFile(wb, `${form.courseId}_${form.examType}_成绩模板.xlsx`)
}

// Excel 导入
const handleExcelImport = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = new Uint8Array(e.target.result)
      const workbook = XLSX.read(data, { type: 'array' })
      const firstSheetName = workbook.SheetNames[0]
      const worksheet = workbook.Sheets[firstSheetName]
      const jsonData = XLSX.utils.sheet_to_json(worksheet)

      if (jsonData.length === 0) {
        ElMessage.warning('Excel文件为空')
        return
      }

      const keyMap = {
        '补考卷面': 'makeupScore',
        '平时': 'dailyScore',
        '考勤': 'attendanceScore',
        '作业': 'homeworkScore',
        '实验': 'experimentScore',
        '期中': 'midtermScore',
        '期末': 'finalScore'
      }

      let matchCount = 0
      jsonData.forEach(excelRow => {
        const studentId = excelRow['学号'] ? String(excelRow['学号']) : null
        if (!studentId) return

        const targetStudent = batchList.value.find(s => s.student_id === studentId)
        if (targetStudent) {
          matchCount++
          if (form.examType === '补考') {
            if (excelRow['补考卷面'] !== undefined) targetStudent.makeupScore = parseFloat(excelRow['补考卷面'])
          } else {
            Object.keys(keyMap).forEach(cnKey => {
              if (excelRow[cnKey] !== undefined && keyMap[cnKey] !== 'makeupScore') {
                targetStudent[keyMap[cnKey]] = parseFloat(excelRow[cnKey])
              }
            })
          }
          calcRowTotal(targetStudent)
        }
      })
      if (matchCount > 0) ElMessage.success(`成功更新 ${matchCount} 条成绩`)
      else ElMessage.warning('未匹配到任何学号')
    } catch (err) {
      console.error(err)
      ElMessage.error('解析失败')
    }
  }
  reader.readAsArrayBuffer(file.raw)
}

const resetBatchScores = () => {
  batchList.value.forEach(row => {
    row.attendanceScore = null
    row.homeworkScore = null
    row.experimentScore = null
    row.midtermScore = null
    row.dailyScore = null
    row.finalScore = null
    row.makeupScore = null
    row.totalScore = null
    row.gpa = '0.0'
  })
  ElMessage.success('已清空分数')
}

const handleSubmit = async (status) => submitSingle(status)

const submitSingle = async (status) => {
  if (form.examType === '正考' && totalRatio.value !== 100) return ElMessage.warning('系数和非100%')
  await formRef.value.validate()
  loading.value = true
  try {
    const payload = buildPayload(form, status)
    const res = await entryGrade(payload) // ✅ 使用 API
    if (res.code === 200) { ElMessage.success('成功'); handleReset() }
    else ElMessage.error(res.message)
  } catch(e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

const submitBatch = async (status) => {
  if (!form.courseId) return ElMessage.warning('请选择课程')
  if (form.examType === '正考' && totalRatio.value !== 100) return ElMessage.warning('系数和非100%')
  const validRows = batchList.value.filter(row => row.totalScore != null)
  if (!validRows.length) return ElMessage.warning('没有检测到有效分数的记录')

  const actionName = status === 'DRAFT' ? '暂存' : '归档'
  const tips = status === 'SUBMITTED' ? '归档后将无法修改！' : '暂存后可随时修改。'
  await ElMessageBox.confirm(`确定要${actionName}这 ${validRows.length} 条成绩吗？${tips}`, `批量${actionName}`, { type: status === 'SUBMITTED' ? 'warning' : 'info' })

  loading.value = true
  try {
    const grades = validRows.map(row => buildPayload(row, status))
    const res = await batchEntryGrade({ grades }) // ✅ 使用 API
    if (res.code === 200) {
      ElMessage.success(`批量${actionName}成功`)
      if (status === 'SUBMITTED') batchList.value = []
    } else ElMessage.error(res.message)
  } catch (e) { if(e !== 'cancel') ElMessage.error(e.message) }
  finally { loading.value = false }
}

const buildPayload = (source, status) => {
  const data = {
    studentId: source.student_id || source.studentId,
    courseId: form.courseId, semester: form.semester, examType: form.examType, status: status,
    totalScore: source.totalScore?.toString()
  }
  if (form.examType === '补考') { if (source.makeupScore != null) data.makeupScore = source.makeupScore.toString() }
  else { scoreConfigItems.forEach(item => { if (source[item.scoreKey] != null) data[item.scoreKey] = source[item.scoreKey].toString() }) }
  return data
}

const handleReset = () => { formRef.value.resetFields(); form.studentName = ''; form.totalScore = null; form.gpa = '0.0' }
</script>

<style scoped>
/* 保持原有样式 */
.score-entry { max-width: 1000px; margin: 0 auto; padding-bottom: 40px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0 0 5px; color: #303133; }
.sub-title { color: #909399; font-size: 14px; margin: 0; }

.score-input-area { background-color: #f8faff; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
.score-item-card { background: white; border-radius: 6px; padding: 10px; border: 1px solid #e4e7ed; }
.score-item-card.disabled { background: #f5f7fa; opacity: 0.7; }
.score-item-card.highlight { border-color: #b3d8ff; background: #ecf5ff; }
.score-item-card .label { font-weight: bold; color: #606266; margin-bottom: 8px; font-size: 13px; }
.score-item-card .inputs { display: flex; align-items: center; gap: 5px; }
.symbol { color: #909399; font-size: 12px; }

.total-score-bar {
  background: linear-gradient(135deg, #1890ff 0%, #36cfc9 100%);
  color: white; padding: 15px 25px; border-radius: 8px;
  display: flex; align-items: center; justify-content: flex-end; margin-top: 10px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
  gap: 30px;
}

.score-group { display: flex; align-items: center; }
.score-group .label { font-size: 16px; margin-right: 5px; opacity: 0.9; }
.score-group .value { font-size: 24px; font-weight: bold; font-family: 'DIN Alternate', sans-serif; }
.gpa-group .value { color: #ffd700; }

.form-actions { display: flex; justify-content: flex-end; gap: 15px; margin-top: 30px; }
.batch-header { margin-bottom: 20px; }
.batch-footer { margin-top: 20px; display: flex; justify-content: space-between; align-items: center; }
.ratio-row { display: flex; align-items: center; gap: 10px; margin-bottom: 5px; justify-content: space-between;}

.upload-excel { display: inline-block; margin: 0 10px; }
.divider-vertical { color: #dcdfe6; margin: 0 10px; font-size: 14px;}
</style>