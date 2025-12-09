<template>
  <div class="grade-analysis" id="analysis-page">
    <div class="page-header no-print">
      <h2>成绩分析表生成与录入</h2>
    </div>

    <el-card shadow="never" class="query-card no-print">
      <el-form :inline="true" :model="queryForm">
        <el-form-item label="学期">
          <el-select v-model="queryForm.semester" placeholder="选择学期" style="width: 180px">
            <el-option label="2024-2025-1" value="2024-2025-1" />
            <el-option label="2024-2025-2" value="2024-2025-2" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="queryForm.courseId" placeholder="选择课程" style="width: 220px">
            <el-option v-for="c in courses" :key="c.course_id" :label="c.course_name" :value="c.course_id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleGenerate" :loading="loading" icon="DataLine">生成统计 / 加载分析</el-button>
          <el-button type="warning" @click="printReport" :disabled="!statsData" icon="Printer">打印分析表</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="statsData" class="analysis-content" id="print-section">

      <div class="print-header show-in-print">
        <h1>长安大学课程成绩统计分析表</h1>
        <div class="meta-info">
          <span>学年学期：{{ queryForm.semester }}</span>
          <span>课程名称：{{ getCourseName(queryForm.courseId) }}</span>
          <span>课程代码：{{ queryForm.courseId }}</span>
        </div>
      </div>

      <div class="stats-overview">
        <el-row :gutter="20">
          <el-col :span="6"><div class="stat-card"><div class="label">平均分</div><div class="value highlight">{{ statsData.avgScore }}</div></div></el-col>
          <el-col :span="6"><div class="stat-card"><div class="label">及格率</div><div class="value" :class="parseFloat(statsData.passRate)<60?'danger':'success'">{{ statsData.passRate }}%</div></div></el-col>
          <el-col :span="6"><div class="stat-card"><div class="label">最高 / 最低</div><div class="value">{{ statsData.maxScore }} / {{ statsData.minScore }}</div></div></el-col>
          <el-col :span="6"><div class="stat-card"><div class="label">应考 / 实考</div><div class="value">{{ statsData.totalStudents }} / {{ statsData.realStudents }}</div></div></el-col>
        </el-row>

        <el-row :gutter="20" style="margin-top: 20px;">
          <el-col :span="14" class="chart-col">
            <el-card shadow="hover" header="成绩分布趋势图" class="chart-card">
              <div ref="chartRef" style="width: 100%; height: 300px;"></div>
            </el-card>
          </el-col>

          <el-col :span="10">
            <el-card shadow="hover" header="分布详情表" class="table-card" style="height: 100%">
              <el-table :data="distributionTable" border size="small" style="height: 300px">
                <el-table-column prop="label" label="分数段" />
                <el-table-column prop="count" label="人数" align="center" width="80" />
                <el-table-column label="比例" align="center">
                  <template #default="scope">
                    <span class="no-print"> <el-progress :percentage="scope.row.percent" :format="p=>p+'%'" :status="scope.row.label.includes('不及格')?'exception':''"/>
                    </span>
                    <span class="show-in-print">{{ scope.row.percent }}%</span> </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <el-card class="analysis-input-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>试卷/成绩分析报告</span>
            <el-tag type="info" size="small" class="no-print">请根据统计数据填写</el-tag>
          </div>
        </template>

        <el-form :model="analysisForm" label-position="top">
          <el-form-item label="分析内容 (试题质量、学生掌握情况、改进措施)" class="input-item">
            <el-input
                v-model="analysisForm.content"
                type="textarea"
                :rows="8"
                placeholder="请输入详细的成绩分析报告..."
                class="no-print-input"
            />
            <div class="print-text-content show-in-print">
              {{ analysisForm.content || '（暂无分析内容）' }}
            </div>
          </el-form-item>

          <div class="form-footer no-print">
            <span class="save-tip" v-if="lastSavedTime">上次保存: {{ lastSavedTime }}</span>
            <el-button type="success" icon="Check" @click="saveAnalysis" :loading="saving">保存分析报告</el-button>
          </div>
        </el-form>
      </el-card>

      <div class="print-footer show-in-print">
        <div class="sign-box">任课教师签字：__________________</div>
        <div class="sign-box">教研室主任签字：__________________</div>
        <div class="sign-box">主管院长签字：__________________</div>
        <div class="print-date">打印日期：{{ new Date().toLocaleDateString() }}</div>
      </div>

    </div>
    <el-empty v-else description="请选择课程并点击生成按钮" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, EditPen, Check, Printer } from '@element-plus/icons-vue' // 引入图标
import request from '@/utils/request'
import * as echarts from 'echarts'

const loading = ref(false)
const saving = ref(false)
const courses = ref([])
const statsData = ref(null)
const lastSavedTime = ref('')
const chartRef = ref(null)
let chartInstance = null

const queryForm = reactive({ semester: '2024-2025-1', courseId: '' })
const analysisForm = reactive({ content: '' })

onMounted(async () => {
  // 加载教师课程
  const res = await request.get('/remote/client/teacher/courses')
  if (res.code === 200) courses.value = res.data

  // 监听窗口大小改变图表尺寸
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  if (chartInstance) chartInstance.dispose()
})

// 获取课程名称
const getCourseName = (id) => {
  const c = courses.value.find(i => i.course_id === id)
  return c ? c.course_name : id
}

const resizeChart = () => { if (chartInstance) chartInstance.resize() }

// [新增] 打印报表方法
const printReport = () => {
  // 1. 临时调整图表大小以适应A4纸宽度
  if (chartInstance) chartInstance.resize({ width: 700, height: 300 })

  // 2. 延迟调用打印，给DOM渲染一点时间
  setTimeout(() => {
    window.print()
    // 3. 打印对话框关闭后，恢复图表自适应
    if (chartInstance) chartInstance.resize()
  }, 300)
}

const handleGenerate = async () => {
  if (!queryForm.courseId) return ElMessage.warning('请先选择课程')
  loading.value = true

  try {
    // 获取统计数据
    const statRes = await request.get('/remote/client/grade/stats', { params: queryForm })
    if (statRes.code === 200) {
      statsData.value = statRes.data
      nextTick(() => { renderChart() })
      ElMessage.success('统计表已生成')
    }

    // 获取历史分析
    const analysisRes = await request.get('/remote/client/grade/analysis/get', { params: queryForm })
    if (analysisRes.code === 200 && analysisRes.data) {
      analysisForm.content = analysisRes.data.analysis_content || ''
      lastSavedTime.value = analysisRes.data.updated_at || '刚刚'
    } else {
      analysisForm.content = ''
      lastSavedTime.value = ''
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const renderChart = () => {
  if (!chartRef.value || !statsData.value) return
  if (!chartInstance) chartInstance = echarts.init(chartRef.value)

  const dist = statsData.value.distribution || [0,0,0,0,0]
  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: ['不及格', '及格', '中等', '良好', '优秀'] },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '人数', type: 'line', data: dist, smooth: true,
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{offset: 0, color: 'rgba(24,144,255,0.5)'}, {offset: 1, color: 'rgba(24,144,255,0.01)'}]) },
      itemStyle: { color: '#1890ff' },
      label: { show: true, position: 'top' }
    }]
  }
  chartInstance.setOption(option)
}

const saveAnalysis = async () => {
  if (!analysisForm.content.trim()) return ElMessage.warning('分析内容不能为空')
  saving.value = true
  try {
    const payload = {
      ...queryForm,
      content: analysisForm.content,
      avgScore: statsData.value.avgScore,
      passRate: statsData.value.passRate,
      maxScore: statsData.value.maxScore,
      minScore: statsData.value.minScore,
      distributionJson: JSON.stringify(statsData.value.distribution)
    }
    const res = await request.post('/remote/client/grade/analysis/save', payload)
    if (res.code === 200) {
      ElMessage.success('保存成功')
      lastSavedTime.value = new Date().toLocaleString()
    } else { ElMessage.error(res.message) }
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

const distributionTable = computed(() => {
  if (!statsData.value) return []
  const dist = statsData.value.distribution || [0,0,0,0,0]
  const total = statsData.value.realStudents || 1
  const calcPercent = (val) => Math.round((val / total) * 1000) / 10
  return [
    { label: '优秀 (90-100)', count: dist[4], percent: calcPercent(dist[4]) },
    { label: '良好 (80-89)', count: dist[3], percent: calcPercent(dist[3]) },
    { label: '中等 (70-79)', count: dist[2], percent: calcPercent(dist[2]) },
    { label: '及格 (60-69)', count: dist[1], percent: calcPercent(dist[1]) },
    { label: '不及格 (<60)', count: dist[0], percent: calcPercent(dist[0]) }
  ]
})
</script>

<style scoped>
.grade-analysis { padding: 20px; max-width: 1200px; margin: 0 auto; }
.page-header { margin-bottom: 20px; }
.query-card { margin-bottom: 20px; }

.stats-overview { margin-bottom: 30px; animation: fadeIn 0.5s ease; }
.stat-card {
  background: #fff; padding: 20px; border-radius: 8px; text-align: center;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05); margin-bottom: 10px;
}
.stat-card .label { color: #909399; font-size: 14px; margin-bottom: 8px; }
.stat-card .value { font-size: 24px; font-weight: bold; color: #303133; }
.stat-card .value.highlight { color: #409eff; }
.stat-card .value.success { color: #67c23a; }
.stat-card .value.danger { color: #f56c6c; }

.analysis-input-card { margin-top: 20px; border-top: 3px solid #1890ff; animation: fadeIn 0.8s ease; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.form-footer { display: flex; justify-content: flex-end; align-items: center; margin-top: 10px; }
.save-tip { margin-right: 15px; font-size: 12px; color: #909399; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

/* ================== [核心] 打印专用样式 ================== */
@media print {
  /* 1. 全局重置：隐藏所有内容，背景设为白色 */
  body * {
    visibility: hidden;
  }

  /* 2. 定位打印区域：只显示 #print-section 及其子元素 */
  #print-section, #print-section * {
    visibility: visible;
  }

  /* 3. 绝对定位：将打印区域拉到页面最顶端，覆盖其他内容 */
  #print-section {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    margin: 0;
    padding: 20px;
    background-color: white; /* 确保背景纯白 */
    z-index: 9999;
  }

  /* 4. 隐藏屏幕元素：按钮、输入框、提示标签 */
  .no-print, .el-button, .el-select, .no-print-input, .el-tag {
    display: none !important;
  }

  /* 5. 显示打印元素：表头、页脚、纯文本内容 */
  .show-in-print {
    display: block !important;
  }

  /* 6. 打印排版优化 */
  .print-header {
    text-align: center;
    margin-bottom: 30px;
    border-bottom: 2px solid #333;
    padding-bottom: 10px;
  }
  .print-header h1 { font-size: 24px; margin: 0 0 15px 0; font-family: "SimSun", "Songti SC", serif; }
  .meta-info { display: flex; justify-content: space-between; font-size: 14px; padding: 0 20px; }

  /* 强制卡片无阴影、有边框 */
  .el-card {
    box-shadow: none !important;
    border: 1px solid #999 !important;
    margin-bottom: 15px !important;
  }

  /* 纯文本分析内容样式 */
  .print-text-content {
    border: 1px solid #ddd;
    padding: 15px;
    min-height: 150px;
    white-space: pre-wrap; /* 保留换行 */
    font-size: 14px;
    line-height: 1.6;
    font-family: "FangSong", serif; /* 仿宋字体更像公文 */
  }

  /* 底部签字栏布局 */
  .print-footer {
    margin-top: 50px;
    display: flex;
    justify-content: space-between;
    flex-wrap: wrap;
    padding: 0 20px;
  }
  .sign-box { width: 45%; margin-bottom: 30px; font-size: 16px; }
  .print-date { width: 100%; text-align: right; margin-top: 10px; font-size: 14px; }
}

/* 屏幕模式下默认隐藏打印专用元素 */
.show-in-print { display: none; }
</style>