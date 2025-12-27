<template>
  <div class="grade-analysis" id="analysis-page">
    <div class="page-header no-print">
      <div class="header-content">
        <h2>成绩分析与统计</h2>
        <p class="subtitle">生成课程成绩统计数据，并填写教学质量分析报告</p>
      </div>
    </div>

    <el-card shadow="hover" class="query-card no-print">
      <el-form :inline="true" :model="queryForm" class="query-form">
        <el-form-item label="学期">
          <el-select
              v-model="queryForm.semester"
              placeholder="选择学期"
              style="width: 160px"
              @change="handleSemesterChange"
          >
            <el-option
                v-for="sem in uniqueSemesters"
                :key="sem"
                :label="sem"
                :value="sem"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="课程">
          <el-select
              v-model="queryForm.courseId"
              placeholder="选择课程"
              style="width: 220px"
              filterable
              no-data-text="该学期无课程"
          >
            <el-option
                v-for="c in filteredCourses"
                :key="c.course_id"
                :label="c.course_name"
                :value="c.course_id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryForm.examType" style="width: 100px">
            <el-option label="正考" value="正考" />
            <el-option label="补考" value="补考" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleGenerate" :loading="loading" icon="DataLine">生成统计数据</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="statsData" class="analysis-content">

      <el-card class="info-card no-print" shadow="never" style="margin-bottom: 20px;">
        <template #header>
          <div class="card-header">
            <span>📊 报表基础信息 </span>
          </div>
        </template>

        <el-form :inline="true" :model="reportInfo" label-width="90px" size="small">
          <el-row :gutter="20">
            <el-col :span="6"><el-form-item label="教师姓名"><el-input v-model="userInfo.name" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="教师职称"><el-input v-model="reportInfo.teacherTitle" placeholder="自动读取中..." /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="课程名称"><el-input v-model="currentCourseName" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="所在院系"><el-input v-model="reportInfo.department" placeholder="自动读取中..." /></el-form-item></el-col>

            <el-col :span="12"><el-form-item label="教学班级" style="width: 100%"><el-input v-model="reportInfo.classNames" style="width: 400px" placeholder="生成统计后自动读取" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="考试人数"><el-input v-model="reportInfo.examCount" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="课程学时"><el-input v-model="reportInfo.courseHours" placeholder="如: 32" /></el-form-item></el-col>

            <el-col :span="6"><el-form-item label="考试性质"><el-input v-model="reportInfo.examNature" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="考试方式"><el-input v-model="reportInfo.examMethod" placeholder="如: 闭卷" /></el-form-item></el-col>
            <el-col :span="6"><el-form-item label="考试时间"><el-date-picker v-model="reportInfo.examDate" type="date" value-format="YYYY-MM-DD" style="width: 100%"/></el-form-item></el-col>

            <el-col :span="12">
              <el-form-item label="试题来源">
                <el-checkbox-group v-model="reportInfo.questionSource">
                  <el-checkbox label="自主命题" />
                  <el-checkbox label="试题库" />
                  <el-checkbox label="其它" />
                </el-checkbox-group>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-divider content-position="left">考试成绩分布表</el-divider>
        <div class="distribution-preview-table">
          <table class="preview-table">
            <thead>
            <tr><th style="width: 100px; background: #f5f7fa;">项目</th><th>不及格(0~59)</th><th>及格(60~69)</th><th>中等(70~79)</th><th>良好(80~89)</th><th>优秀(90~100)</th></tr>
            </thead>
            <tbody>
            <tr><td style="font-weight: bold; background: #f5f7fa;">学生数</td><td>{{ distributionTable[0]?.count }}</td><td>{{ distributionTable[1]?.count }}</td><td>{{ distributionTable[2]?.count }}</td><td>{{ distributionTable[3]?.count }}</td><td>{{ distributionTable[4]?.count }}</td></tr>
            <tr><td style="font-weight: bold; background: #f5f7fa;">占总比</td><td>{{ distributionTable[0]?.percent }}%</td><td>{{ distributionTable[1]?.percent }}%</td><td>{{ distributionTable[2]?.percent }}%</td><td>{{ distributionTable[3]?.percent }}%</td><td>{{ distributionTable[4]?.percent }}%</td></tr>
            </tbody>
          </table>
          <table class="preview-table" style="margin-top: -1px;">
            <tbody>
            <tr>
              <td style="width: 100px; font-weight: bold; background: #f5f7fa;">最高分</td><td>{{ statsData.maxScore }}</td>
              <td style="width: 100px; font-weight: bold; background: #f5f7fa;">最低分</td><td>{{ statsData.minScore }}</td>
              <td style="width: 100px; font-weight: bold; background: #f5f7fa;">平均分</td><td>{{ statsData.avgScore }}</td>
              <td style="width: 100px; font-weight: bold; background: #f5f7fa;">标准差</td><td>{{ standardDeviation }}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </el-card>

      <div class="section-container no-print">
        <div class="section-title">成绩图表概览</div>
        <el-row :gutter="15" class="chart-row">
          <el-col :span="12"><el-card shadow="never" class="chart-card"><template #header>成绩分布直方图</template><div ref="histogramRef" class="echart-container" style="height: 300px;"></div></el-card></el-col>
          <el-col :span="12"><el-card shadow="never" class="chart-card"><template #header>成绩分布趋势图</template><div ref="lineChartRef" class="echart-container" style="height: 300px;"></div></el-card></el-col>
        </el-row>
      </div>

      <div class="section-container analysis-section no-print">
        <div class="section-title"><el-icon><EditPen /></el-icon> 试卷/成绩分析报告内容录入</div>
        <el-row :gutter="20">
          <el-col :span="24"><div class="analysis-block"><div class="sub-label">1. 试题质量分析</div><el-input v-model="analysisForm.questionAnalysis" type="textarea" :rows="4" placeholder="覆盖面、难易度、信度、效度等" /></div></el-col>
          <el-col :span="24"><div class="analysis-block"><div class="sub-label">2. 考试成绩分析</div><el-input v-model="analysisForm.gradeAnalysis" type="textarea" :rows="4" placeholder="分数分布、典型错误、知识点掌握情况" /></div></el-col>
          <el-col :span="24"><div class="analysis-block"><div class="sub-label">3. 教学效果分析</div><el-input v-model="analysisForm.teachingAnalysis" type="textarea" :rows="4" placeholder="教学效果分析及改进措施" /></div></el-col>
        </el-row>
      </div>

      <div class="action-bar no-print" style="margin-top: 20px; text-align: right;">
        <span class="save-tip" v-if="lastSavedTime">上次保存: {{ lastSavedTime }}</span>
        <el-button type="success" icon="Check" @click="saveAnalysis" :loading="saving">保存分析内容</el-button>
        <el-button color="#626aef" plain icon="Printer" @click="doPrint">打印标准质量分析报告</el-button>
      </div>

      <div id="print-section" class="print-layout">
        <div class="print-header">
          <h1>长安大学{{ queryForm.semester.split('-')[0] }}-{{ queryForm.semester.split('-')[1] }}学年第{{ queryForm.semester.split('-')[2] }}学期考试质量分析</h1>
        </div>

        <table class="pdf-table">
          <tbody>
          <tr>
            <td class="label">课程名称</td>
            <td class="value" colspan="2">{{ currentCourseName }}</td>
            <td class="label">考试性质</td>
            <td class="value">{{ reportInfo.examNature }}</td>
            <td class="label">课程学时</td>
            <td class="value">{{ reportInfo.courseHours }}</td>
            <td class="label">考试方式</td>
            <td class="value">{{ reportInfo.examMethod }}</td>
          </tr>
          <tr>
            <td class="label">教学班级</td>
            <td class="value" colspan="5" style="text-align: left; padding-left: 10px;">{{ reportInfo.classNames }}</td>
            <td class="label">考试时间</td>
            <td class="value" colspan="2">{{ reportInfo.examDate }}</td>
          </tr>
          <tr>
            <td class="label">教师姓名</td>
            <td class="value" colspan="2">{{ userInfo.name }}</td>
            <td class="label">教师职称</td>
            <td class="value" colspan="2">{{ reportInfo.teacherTitle }}</td>
            <td class="label">所在院(系)</td>
            <td class="value" colspan="2">{{ reportInfo.department }}</td>
          </tr>

          <tr>
            <td class="label" rowspan="3">考试成绩<br>分布</td>
            <td class="label">分数段</td>
            <td>不及格(0~59)</td>
            <td>及格(60~69)</td>
            <td colspan="2">中等(70~79)</td>
            <td colspan="2">良好(80~89)</td>
            <td>优秀(90~100)</td>
          </tr>
          <tr>
            <td class="label">学生数</td>
            <td>{{ distributionTable[0]?.count }}</td>
            <td>{{ distributionTable[1]?.count }}</td>
            <td colspan="2">{{ distributionTable[2]?.count }}</td>
            <td colspan="2">{{ distributionTable[3]?.count }}</td>
            <td>{{ distributionTable[4]?.count }}</td>
          </tr>
          <tr>
            <td class="label">占总比</td>
            <td>{{ distributionTable[0]?.percent }}%</td>
            <td>{{ distributionTable[1]?.percent }}%</td>
            <td colspan="2">{{ distributionTable[2]?.percent }}%</td>
            <td colspan="2">{{ distributionTable[3]?.percent }}%</td>
            <td>{{ distributionTable[4]?.percent }}%</td>
          </tr>

          <tr>
            <td class="label">最高分</td>
            <td class="value" colspan="2">{{ statsData.maxScore }}</td>
            <td class="label">最低分</td>
            <td class="value">{{ statsData.minScore }}</td>
            <td class="label">平均分</td>
            <td class="value">{{ statsData.avgScore }}</td>
            <td class="label">标准差</td>
            <td class="value">{{ standardDeviation }}</td>
          </tr>

          <tr>
            <td class="label">试题来源</td>
            <td colspan="8" class="checkbox-container">
              <span class="cb-item"><span class="box">{{ reportInfo.questionSource.includes('自主命题') ? '☑' : '☐' }}</span> 自主命题</span>
              <span class="cb-item"><span class="box">{{ reportInfo.questionSource.includes('试题库') ? '☑' : '☐' }}</span> 试题库</span>
              <span class="cb-item"><span class="box">{{ reportInfo.questionSource.includes('其它') ? '☑' : '☐' }}</span> 其它</span>
            </td>
          </tr>

          <tr>
            <td class="label">考试人数</td>
            <td colspan="8" style="text-align: left; padding-left: 20px;">{{ reportInfo.examCount }} 人</td>
          </tr>

          <tr>
            <td colspan="9" style="padding: 0;">
              <div class="charts-row">
                <div class="chart-col">
                  <div class="chart-caption">成绩分布直方图</div>
                  <img :src="histogramImg" class="chart-snapshot" v-if="histogramImg" />
                </div>
                <div class="chart-col" style="border-left: 1px solid #000;">
                  <div class="chart-caption">成绩分布趋势图</div>
                  <img :src="lineChartImg" class="chart-snapshot" v-if="lineChartImg" />
                </div>
              </div>
            </td>
          </tr>

          <tr class="section-bar"><td colspan="9">试卷分析及成绩分析</td></tr>

          <tr class="analysis-row">
            <td class="label vertical-text">试题<br>质量<br>分析</td>
            <td colspan="8">
              <div class="analysis-text">
                <div class="content">{{ analysisForm.questionAnalysis || '（未填写）' }}</div>
              </div>
            </td>
          </tr>
          <tr class="analysis-row">
            <td class="label vertical-text">考试<br>成绩<br>分析</td>
            <td colspan="8">
              <div class="analysis-text">
                <div class="content">{{ analysisForm.gradeAnalysis || '（未填写）' }}</div>
              </div>
            </td>
          </tr>
          <tr class="analysis-row">
            <td class="label vertical-text">教学<br>效果<br>分析</td>
            <td colspan="8">
              <div class="analysis-text">
                <div class="content">{{ analysisForm.teachingAnalysis || '（未填写）' }}</div>
              </div>
            </td>
          </tr>

          <tr class="analysis-row" style="height: 80px;">
            <td class="label vertical-text">审查<br>意见</td>
            <td colspan="8">
              <div class="review-box">
                <div class="signature-box"><span>任课教师签字：__________________</span></div>
                <div class="sign-line">教研室主任签字：__________________</div>
                <div class="date-line">日期：______年___月___日</div>
              </div>
            </td>
          </tr>
          </tbody>
        </table>

        <div class="print-footer">注：本表一式两份，一份随试卷装订，一份交学院（系）存档。</div>
      </div>
    </div>

    <div v-else class="empty-state no-print"><el-empty description="请选择课程并点击生成统计" /></div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, EditPen, Check, Printer } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import * as echarts from 'echarts'
// ✅ 引入接口
import {
  getTeacherCourses,
  getGradeStats,
  getAnalysisContent,
  saveAnalysisContent,
  getCourseStudents,
  getTeacherInfo // <--- 新增
} from '@/api/teacher'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)

const loading = ref(false)
const saving = ref(false)
const courses = ref([])
const statsData = ref(null)
const lastSavedTime = ref('')
const histogramRef = ref(null)
const lineChartRef = ref(null)
let histogramInstance = null
let lineChartInstance = null
const histogramImg = ref('')
const lineChartImg = ref('')

const queryForm = reactive({ semester: '2024-2025-1', courseId: '', examType: '正考' })
const analysisForm = reactive({ questionAnalysis: '', gradeAnalysis: '', teachingAnalysis: '' })

const reportInfo = reactive({
  courseHours: '32',
  examNature: '考试',
  examMethod: '闭卷', // ✅ 考试方式
  examDate: new Date().toISOString().split('T')[0],
  teacherTitle: '', // 自动读取
  department: '信息工程学院', // 默认值
  questionSource: ['自主命题'],
  classNames: '',
  examCount: 0
})

const uniqueSemesters = computed(() => {
  if (!courses.value || courses.value.length === 0) return []
  const all = courses.value.map(c => c.semester).filter(s => s)
  return Array.from(new Set(all)).sort().reverse()
})

const filteredCourses = computed(() => {
  if (!queryForm.semester) return courses.value
  return courses.value.filter(c => c.semester === queryForm.semester)
})

const currentCourseName = computed(() => {
  const c = courses.value.find(i => i.course_id === queryForm.courseId)
  return c ? c.course_name : queryForm.courseId
})

const handleSemesterChange = () => { queryForm.courseId = ''; statsData.value = null }

const standardDeviation = computed(() => {
  if (!statsData.value?.scoreList?.length) return '0.00'
  const scores = statsData.value.scoreList
  const mean = scores.reduce((a, b) => a + b, 0) / scores.length
  return Math.sqrt(scores.reduce((a, b) => a + Math.pow(b - mean, 2), 0) / scores.length).toFixed(2)
})

const distributionTable = computed(() => {
  if (!statsData.value) return []
  const dist = statsData.value.distribution || [0,0,0,0,0]
  const total = statsData.value.realStudents || 1
  const p = (v) => Math.round((v / total) * 1000) / 10
  return [
    { label: '不及格', count: dist[0], percent: p(dist[0]) },
    { label: '及格', count: dist[1], percent: p(dist[1]) },
    { label: '中等', count: dist[2], percent: p(dist[2]) },
    { label: '良好', count: dist[3], percent: p(dist[3]) },
    { label: '优秀', count: dist[4], percent: p(dist[4]) }
  ]
})

// ✅ 获取教师信息
const fetchTeacherInfo = async () => {
  if (!userInfo.value?.userId) return
  try {
    const res = await getTeacherInfo(userInfo.value.userId)
    if (res.code === 200 && res.data) {
      if (res.data.title) reportInfo.teacherTitle = res.data.title
      if (res.data.department) reportInfo.department = res.data.department
      if (res.data.dept_name) reportInfo.department = res.data.dept_name // 兼容不同字段名
    }
  } catch (e) {
    console.warn("自动获取职称失败", e)
  }
}

onMounted(async () => {
  const res = await getTeacherCourses()
  if (res.code === 200) {
    courses.value = res.data
    if (uniqueSemesters.value.length && !uniqueSemesters.value.includes(queryForm.semester)) queryForm.semester = uniqueSemesters.value[0]
  }
  fetchTeacherInfo() // 调用
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})

const resizeCharts = () => { histogramInstance?.resize(); lineChartInstance?.resize() }
const disposeCharts = () => { histogramInstance?.dispose(); lineChartInstance?.dispose() }
const getRateColor = (rate) => parseFloat(rate) < 60 ? 'text-danger' : 'text-success'

const handleGenerate = async () => {
  if (!queryForm.courseId) return ElMessage.warning('请先选择课程')
  loading.value = true
  try {
    const statRes = await getGradeStats(queryForm)
    if (statRes.code === 200) {
      statsData.value = statRes.data
      reportInfo.examCount = statsData.value.realStudents || 0
      fetchClassNames()
      nextTick(() => { renderHistogram(); renderLineChart() })
      ElMessage.success('统计生成完毕')
    }
    const analysisRes = await getAnalysisContent(queryForm)
    if (analysisRes.code === 200 && analysisRes.data?.analysis_content) {
      try {
        const parsed = JSON.parse(analysisRes.data.analysis_content)
        if (typeof parsed === 'object') {
          analysisForm.questionAnalysis = parsed.q || ''
          analysisForm.gradeAnalysis = parsed.g || ''
          analysisForm.teachingAnalysis = parsed.t || ''
        } else analysisForm.questionAnalysis = analysisRes.data.analysis_content
      } catch (e) { analysisForm.questionAnalysis = analysisRes.data.analysis_content }
      lastSavedTime.value = analysisRes.data.updated_at || ''
    } else { analysisForm.questionAnalysis = ''; analysisForm.gradeAnalysis = ''; analysisForm.teachingAnalysis = '' }
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

const fetchClassNames = async () => {
  try {
    const res = await getCourseStudents(queryForm.courseId, queryForm.semester)
    if (res.code === 200 && res.data) reportInfo.classNames = [...new Set(res.data.map(s => s.class_name).filter(c => c))].join('、')
  } catch (e) { console.error('班级获取失败', e) }
}

const renderHistogram = () => {
  if (!histogramRef.value || !statsData.value) return
  if (!histogramInstance) histogramInstance = echarts.init(histogramRef.value, null, { renderer: 'svg' })
  histogramInstance.setOption({
    animation: false, color: ['#3398DB'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: { type: 'category', data: ['不及格', '及格', '中等', '良好', '优秀'], axisTick: { alignWithLabel: true } },
    yAxis: { type: 'value' },
    series: [{ name: '人数', type: 'bar', barWidth: '50%', data: statsData.value.distribution, label: { show: true, position: 'top' } }]
  })
}

const renderLineChart = () => {
  if (!lineChartRef.value || !statsData.value) return
  if (!lineChartInstance) lineChartInstance = echarts.init(lineChartRef.value, null, { renderer: 'svg' })
  const scores = statsData.value.scoreList || []
  lineChartInstance.setOption({
    animation: false, tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: { type: 'category', data: scores.map((_, i) => i + 1), show: false },
    yAxis: { type: 'value', max: 100, min: 0 },
    series: [{ data: scores, type: 'line', smooth: true, symbol: 'none', lineStyle: { width: 2, color: '#409eff' }, areaStyle: { opacity: 0.2, color: '#409eff' } }]
  })
}

const saveAnalysis = async () => {
  const contentPackage = JSON.stringify({ q: analysisForm.questionAnalysis, g: analysisForm.gradeAnalysis, t: analysisForm.teachingAnalysis })
  if (!analysisForm.questionAnalysis && !analysisForm.gradeAnalysis) return ElMessage.warning('请至少填写一部分分析内容')
  saving.value = true
  try {
    await saveAnalysisContent({
      ...queryForm, content: contentPackage, avgScore: statsData.value.avgScore, passRate: statsData.value.passRate,
      maxScore: statsData.value.maxScore, minScore: statsData.value.minScore, distributionJson: JSON.stringify(statsData.value.distribution)
    })
    ElMessage.success('保存成功'); lastSavedTime.value = new Date().toLocaleString()
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

const doPrint = () => {
  if (histogramInstance) histogramImg.value = histogramInstance.getDataURL({ pixelRatio: 2, backgroundColor: '#fff' })
  if (lineChartInstance) lineChartImg.value = lineChartInstance.getDataURL({ pixelRatio: 2, backgroundColor: '#fff' })
  nextTick(() => window.print())
}
</script>

<style scoped>
/* 屏幕显示样式 */
.grade-analysis { padding: 20px; max-width: 1200px; margin: 0 auto; }
.page-header { margin-bottom: 20px; border-left: 4px solid #1890ff; padding-left: 15px; }
.stats-cards { margin-bottom: 20px; }
.stat-box { background: #f8faff; border: 1px solid #ebeef5; padding: 15px; border-radius: 8px; text-align: center; display: flex; flex-direction: column; }
.stat-box strong { font-size: 20px; color: #303133; }
.text-danger { color: #f56c6c; } .text-success { color: #67c23a; }
.section-container { margin-bottom: 30px; }
.section-title { font-size: 16px; font-weight: bold; margin-bottom: 15px; }
.sub-label { font-size: 14px; font-weight: bold; margin-bottom: 5px; color: #606266; }
.distribution-preview-table { margin-top: 15px; overflow-x: auto; }
.preview-table { width: 100%; border-collapse: collapse; font-size: 13px; border: 1px solid #e4e7ed; }
.preview-table th, .preview-table td { border: 1px solid #e4e7ed; padding: 8px; text-align: center; color: #606266; }

/* 默认隐藏打印区域 */
#print-section { display: none; }

/* 打印专用样式 */
@media print {
  body * { visibility: hidden; }
  .no-print, .el-header, .el-aside, .sidebar, .header { display: none !important; }

  #print-section {
    display: block !important;
    visibility: visible !important;
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    padding: 0 10px;
    background: white;
    z-index: 9999;
  }
  #print-section * { visibility: visible !important; }

  .print-header { text-align: center; margin-bottom: 10px; }
  .print-header h1 { font-size: 18px; font-family: "SimSun", serif; font-weight: bold; margin: 0 0 5px 0; }

  .pdf-table { width: 100%; border-collapse: collapse; border: 1px solid #000; font-family: "SimSun", serif; font-size: 12px; }
  .pdf-table td { border: 1px solid #000; padding: 3px; text-align: center; vertical-align: middle; }

  .label { font-weight: bold; background-color: #f0f0f0 !important; -webkit-print-color-adjust: exact; width: 80px; }
  .value { font-family: "SimSun"; }
  .section-bar td { background-color: #e0e0e0 !important; -webkit-print-color-adjust: exact; font-weight: bold; padding: 5px; }
  .vertical-text { writing-mode: vertical-lr; width: 30px; letter-spacing: 2px; }

  .checkbox-container { text-align: left !important; padding-left: 15px !important; }
  .cb-item { margin-right: 20px; }
  .box { font-size: 14px; margin-right: 2px; }

  .charts-row { display: flex; width: 100%; height: 180px; }
  .chart-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; }
  .chart-caption { font-weight: bold; font-size: 11px; margin-bottom: 5px; }
  .chart-snapshot { max-height: 150px; max-width: 95%; object-fit: contain; }

  .analysis-text { text-align: left; padding: 5px; height: 110px; position: relative; display: flex; flex-direction: column; }
  .analysis-text p { margin: 0 0 5px 0; font-weight: bold; font-size: 11px; }
  .analysis-text .content { font-family: "KaiTi", serif; font-size: 13px; flex: 1; white-space: pre-wrap; }
  .signature-box { text-align: right; margin-top: 5px; font-family: "KaiTi"; }

  .review-box { text-align: right; padding: 5px; margin-top: 30px; font-family: "KaiTi"; }
  .sign-line, .date-line { margin-bottom: 5px; }

  .print-footer { font-size: 10px; margin-top: 5px; }
}
</style>