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
          <el-select v-model="queryForm.semester" placeholder="选择学期" style="width: 160px">
            <el-option label="2024-2025-1" value="2024-2025-1" />
            <el-option label="2024-2025-2" value="2024-2025-2" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="queryForm.courseId" placeholder="选择课程" style="width: 220px" filterable>
            <el-option v-for="c in courses" :key="c.course_id" :label="c.course_name" :value="c.course_id" />
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

    <div v-if="statsData" class="analysis-content" id="print-section" :class="printClass">

      <div class="print-header show-in-print">
        <h1>{{ printClass === 'mode-stats-only' ? '长安大学课程成绩统计表' : '长安大学课程成绩统计分析表' }}</h1>
        <div class="meta-info">
          <span class="meta-item"><strong>学年学期：</strong>{{ queryForm.semester }}</span>
          <span class="meta-item"><strong>课程名称：</strong>{{ getCourseName(queryForm.courseId) }}</span>
          <span class="meta-item"><strong>课程代码：</strong>{{ queryForm.courseId }}</span>
          <span class="meta-item"><strong>考试类型：</strong>{{ queryForm.examType }}</span>
        </div>
      </div>

      <div class="section-container">
        <div class="section-title no-print">成绩统计概览</div>

        <el-row :gutter="20" class="stats-cards">
          <el-col :span="6" :xs="12"><div class="stat-box"><span>平均分</span><strong>{{ statsData.avgScore }}</strong></div></el-col>
          <el-col :span="6" :xs="12"><div class="stat-box"><span>及格率</span><strong :class="getRateColor(statsData.passRate)">{{ statsData.passRate }}%</strong></div></el-col>
          <el-col :span="6" :xs="12"><div class="stat-box"><span>最高/最低</span><strong>{{ statsData.maxScore }} / {{ statsData.minScore }}</strong></div></el-col>
          <el-col :span="6" :xs="12"><div class="stat-box"><span>应考/实考</span><strong>{{ statsData.totalStudents }} / {{ statsData.realStudents }}</strong></div></el-col>
        </el-row>

        <el-row :gutter="15" class="chart-row flex-equal-height">
          <el-col :span="12" :xs="24" class="flex-col">
            <el-card shadow="never" class="chart-card flex-card">
              <template #header><div class="card-header">成绩分布直方图</div></template>
              <div ref="histogramRef" class="echart-container no-print" style="width: 100%; height: 300px;"></div>
              <img :src="histogramImg" class="chart-snapshot show-in-print" v-if="histogramImg" />
            </el-card>
          </el-col>

          <el-col :span="12" :xs="24" class="flex-col">
            <el-card shadow="never" class="table-card flex-card">
              <template #header><div class="card-header">成绩段分布表</div></template>
              <el-table :data="distributionTable" border stripe size="small" class="custom-table">
                <el-table-column prop="label" label="分数段" />
                <el-table-column prop="count" label="人数" align="center" width="80" />
                <el-table-column label="比例" align="center">
                  <template #default="scope">
                    <div class="progress-cell no-print">
                      <el-progress :percentage="scope.row.percent" :color="getProgressColor(scope.row.label)" :show-text="false" style="width: 50px" />
                      <span style="margin-left: 5px">{{ scope.row.percent }}%</span>
                    </div>
                    <span class="show-in-print">{{ scope.row.percent }}%</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="15" class="chart-row" style="margin-top: 15px;">
          <el-col :span="24">
            <el-card shadow="never" class="chart-card">
              <template #header><div class="card-header">具体成绩分布趋势图 (全体实考学生)</div></template>
              <div ref="lineChartRef" class="echart-container no-print" style="width: 100%; height: 300px;"></div>
              <img :src="lineChartImg" class="chart-snapshot show-in-print" v-if="lineChartImg" />
            </el-card>
          </el-col>
        </el-row>
      </div>

      <div class="action-bar no-print">
        <el-button color="#626aef" plain icon="Printer" @click="printStatsTable">
          仅打印成绩统计表
        </el-button>
        <span class="divider">|</span>
        <span class="tip">如需打印完整分析表，请先在下方填写分析内容</span>
      </div>

      <div class="section-container analysis-section">
        <div class="section-title">
          <el-icon><EditPen /></el-icon> 试卷/成绩分析报告
          <span class="no-print sub-tip">(请结合上方统计数据填写)</span>
        </div>

        <el-form :model="analysisForm">
          <div class="input-wrapper">
            <el-input
                v-model="analysisForm.content"
                type="textarea"
                :rows="8"
                placeholder="在此处输入详细的成绩分析报告..."
                class="no-print-input custom-textarea"
                maxlength="2000"
                show-word-limit
            />
            <div class="print-text-content show-in-print">
              {{ analysisForm.content || '（暂无分析内容）' }}
            </div>
          </div>

          <div class="form-footer no-print">
            <div class="save-status" v-if="lastSavedTime">上次保存: {{ lastSavedTime }}</div>
            <el-button type="success" icon="Check" @click="saveAnalysis" :loading="saving">保存分析内容</el-button>
            <el-button type="primary" icon="Printer" @click="printAnalysisReport" :disabled="!analysisForm.content">
              打印完整分析表
            </el-button>
          </div>
        </el-form>
      </div>

      <div class="print-footer show-in-print signature-section">
        <div class="sign-row">
          <div class="sign-box">任课教师签字：__________________</div>
          <div class="sign-box">教研室主任签字：__________________</div>
        </div>
        <div class="sign-row">
          <div class="sign-box">主管院长签字：__________________</div>
          <div class="date-box">日期：{{ new Date().toLocaleDateString() }}</div>
        </div>
      </div>

    </div>

    <div v-else class="empty-state">
      <el-empty description="请选择课程并点击生成统计" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, EditPen, Check, Printer } from '@element-plus/icons-vue'
import request from '@/utils/request'
import * as echarts from 'echarts'

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

const printClass = ref('mode-full-report')
const queryForm = reactive({ semester: '2024-2025-1', courseId: '', examType: '正考' })
const analysisForm = reactive({ content: '' })

onMounted(async () => {
  const res = await request.get('/remote/client/teacher/courses')
  if (res.code === 200) courses.value = res.data
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})

const getCourseName = (id) => courses.value.find(i => i.course_id === id)?.course_name || id
const resizeCharts = () => {
  if (histogramInstance) histogramInstance.resize()
  if (lineChartInstance) lineChartInstance.resize()
}
const disposeCharts = () => {
  if (histogramInstance) histogramInstance.dispose()
  if (lineChartInstance) lineChartInstance.dispose()
}

const getRateColor = (rate) => parseFloat(rate) < 60 ? 'text-danger' : 'text-success'
const getProgressColor = (label) => label.includes('不及格') ? '#f56c6c' : '#409eff'

const handleGenerate = async () => {
  if (!queryForm.courseId) return ElMessage.warning('请先选择课程')
  loading.value = true
  try {
    const statRes = await request.get('/remote/client/grade/stats', { params: queryForm })
    if (statRes.code === 200) {
      statsData.value = statRes.data
      nextTick(() => {
        renderHistogram()
        renderLineChart()
      })
      ElMessage.success('统计生成完毕')
    }
    const analysisRes = await request.get('/remote/client/grade/analysis/get', { params: queryForm })
    if (analysisRes.code === 200 && analysisRes.data) {
      analysisForm.content = analysisRes.data.analysis_content || ''
      lastSavedTime.value = analysisRes.data.updated_at || ''
    } else {
      analysisForm.content = ''
    }
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

const renderHistogram = () => {
  if (!histogramRef.value || !statsData.value) return
  if (!histogramInstance) histogramInstance = echarts.init(histogramRef.value, null, { renderer: 'svg' })

  const dist = statsData.value.distribution || [0,0,0,0,0]
  histogramInstance.setOption({
    animation: false,
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '8%', bottom: '5%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: ['不及格', '及格', '中等', '良好', '优秀'],
      axisLabel: { interval: 0, color: '#333' }
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '人数', type: 'bar', data: dist, barWidth: '50%',
      itemStyle: { color: '#1890ff', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', color: '#000' }
    }]
  })
}

const renderLineChart = () => {
  if (!lineChartRef.value || !statsData.value) return
  if (!lineChartInstance) lineChartInstance = echarts.init(lineChartRef.value, null, { renderer: 'svg' })

  const scores = statsData.value.scoreList || []
  const xAxisData = scores.map((_, index) => index + 1)

  lineChartInstance.setOption({
    animation: false,
    tooltip: { trigger: 'axis', formatter: '排名: {b}<br/>分数: {c}' },
    grid: { left: '3%', right: '5%', bottom: '5%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: xAxisData,
      axisLabel: { show: false } // 隐藏具体的X轴数字，避免太乱
    },
    yAxis: { type: 'value', max: 100, min: 0, name: '分数' },
    series: [{
      name: '分数',
      type: 'line',
      data: scores,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 3, color: '#409eff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
          { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
        ])
      },
      markLine: {
        symbol: 'none',
        label: { position: 'end', formatter: '及格线' },
        data: [{ yAxis: 60, lineStyle: { color: '#f56c6c', type: 'dashed' } }]
      }
    }]
  })
}

const saveAnalysis = async () => {
  if (!analysisForm.content.trim()) return ElMessage.warning('内容不能为空')
  saving.value = true
  try {
    await request.post('/remote/client/grade/analysis/save', {
      ...queryForm,
      content: analysisForm.content,
      avgScore: statsData.value.avgScore,
      passRate: statsData.value.passRate,
      maxScore: statsData.value.maxScore,
      minScore: statsData.value.minScore,
      distributionJson: JSON.stringify(statsData.value.distribution)
    })
    ElMessage.success('保存成功')
    lastSavedTime.value = new Date().toLocaleString()
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

const generateChartImages = () => {
  if (histogramInstance) histogramImg.value = histogramInstance.getDataURL({ pixelRatio: 2, backgroundColor: '#fff' })
  if (lineChartInstance) lineChartImg.value = lineChartInstance.getDataURL({ pixelRatio: 2, backgroundColor: '#fff' })
}

const doPrint = () => {
  generateChartImages()
  nextTick(() => {
    setTimeout(() => {
      window.print()
    }, 300)
  })
}

const printStatsTable = () => {
  printClass.value = 'mode-stats-only'
  doPrint()
}

const printAnalysisReport = () => {
  if (!analysisForm.content) return ElMessage.warning('请先填写分析内容')
  printClass.value = 'mode-full-report'
  doPrint()
}

const distributionTable = computed(() => {
  if (!statsData.value) return []
  const dist = statsData.value.distribution || [0,0,0,0,0]
  const total = statsData.value.realStudents || 1
  const p = (v) => Math.round((v / total) * 1000) / 10
  return [
    { label: '优秀 (90-100)', count: dist[4], percent: p(dist[4]) },
    { label: '良好 (80-89)', count: dist[3], percent: p(dist[3]) },
    { label: '中等 (70-79)', count: dist[2], percent: p(dist[2]) },
    { label: '及格 (60-69)', count: dist[1], percent: p(dist[1]) },
    { label: '不及格 (<60)', count: dist[0], percent: p(dist[0]) }
  ]
})
</script>

<style scoped>
.grade-analysis { padding: 20px; max-width: 1200px; margin: 0 auto; }
.page-header { margin-bottom: 20px; border-left: 4px solid #1890ff; padding-left: 15px; }
.page-header h2 { margin: 0; font-size: 20px; }
.subtitle { margin: 5px 0 0; color: #909399; font-size: 13px; }

.stats-cards { margin-bottom: 20px; }
.stat-box { background: #f8faff; border: 1px solid #ebeef5; padding: 15px; border-radius: 8px; text-align: center; display: flex; flex-direction: column; }
.stat-box span { font-size: 12px; color: #909399; margin-bottom: 5px; }
.stat-box strong { font-size: 20px; color: #303133; }
.text-danger { color: #f56c6c; } .text-success { color: #67c23a; }

.section-container { margin-bottom: 30px; }
.section-title { font-size: 16px; font-weight: bold; margin-bottom: 15px; display: flex; align-items: center; gap: 8px; }
.action-bar { background: #f0f2f5; padding: 10px; border-radius: 4px; margin-bottom: 20px; display: flex; align-items: center; gap: 15px; }
.divider { color: #dcdfe6; } .tip { font-size: 12px; color: #909399; }

.analysis-section { border-top: 2px dashed #ebeef5; padding-top: 20px; }
.custom-textarea :deep(.el-textarea__inner) { background-color: #fff; padding: 15px; font-size: 14px; }
.form-footer { margin-top: 15px; display: flex; justify-content: flex-end; gap: 15px; align-items: center; }
.save-status { font-size: 12px; color: #909399; }

/* === 强制等高 === */
.flex-equal-height { display: flex; flex-wrap: wrap; }
.flex-col { display: flex; flex-direction: column; }
.flex-card { flex: 1; display: flex; flex-direction: column; }
.flex-card :deep(.el-card__body) { flex: 1; display: flex; flex-direction: column; padding: 10px; }
.progress-cell { display: flex; align-items: center; justify-content: space-between; }

.custom-table { height: 300px; width: 100%; }

/* 打印样式 */
@media print {
  body * { visibility: hidden; }
  #print-section, #print-section * { visibility: visible; }
  #print-section { position: absolute; left: 0; top: 0; width: 100%; margin: 0; padding: 20px; background-color: white; z-index: 9999; }

  .no-print, .el-button, .el-select, .no-print-input { display: none !important; }
  .show-in-print { display: block !important; }

  .print-header { text-align: center; border-bottom: 2px solid #333; padding-bottom: 10px; margin-bottom: 10px; }
  .print-header h1 { font-family: "SimSun"; margin: 0 0 10px; font-size: 20px; }
  .meta-info { display: flex; justify-content: space-between; font-size: 12px; }

  /* 压缩卡片边距，节省空间 */
  .stat-box { border: 1px solid #000 !important; background: none !important; padding: 5px !important; margin-bottom: 10px; }
  .stat-box strong { font-size: 16px; }

  .el-card { border: 1px solid #000 !important; box-shadow: none !important; margin-bottom: 10px; break-inside: avoid; }
  .el-card__header { padding: 5px 10px !important; font-size: 13px; font-weight: bold; border-bottom: 1px solid #000 !important; }

  .custom-table { height: auto !important; min-height: 200px; font-size: 11px; }
  .el-table th, .el-table td { border-color: #000 !important; color: #000 !important; padding: 2px 0 !important; }

  .mode-stats-only .analysis-section, .mode-stats-only .signature-section { display: none !important; }

  /* 压缩文本框高度 */
  .print-text-content {
    border: 1px solid #000; padding: 8px; min-height: 150px;
    font-family: "FangSong"; line-height: 1.5; font-size: 13px;
    text-align: justify; white-space: pre-wrap;
  }

  .signature-section { margin-top: 20px; }
  .sign-row { display: flex; justify-content: space-between; margin-bottom: 20px; font-family: "KaiTi"; font-size: 14px; }

  /* 强制图片快照大小 - 关键点：高度调小，挤在一页 */
  .chart-card .echart-container { display: none !important; }
  .chart-snapshot {
    display: block !important;
    width: 100% !important;
    height: 290px !important; /* 压缩高度 */
    object-fit: contain;
  }
}
.show-in-print { display: none; }
</style>