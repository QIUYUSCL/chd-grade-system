<template>
  <div class="grade-analysis">
    <div class="page-header">
      <h2>成绩分析表生成与录入</h2>
    </div>

    <el-card shadow="never" class="query-card">
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
          <el-button type="primary" @click="handleGenerate" :loading="loading" icon="DataLine">生成统计</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="statsData" class="analysis-content">

      <div class="stats-overview">
        <el-row :gutter="20">
          <el-col :span="6"><div class="stat-card"><div class="label">平均分</div><div class="value highlight">{{ statsData.avgScore }}</div></div></el-col>
          <el-col :span="6"><div class="stat-card"><div class="label">及格率</div><div class="value" :class="parseFloat(statsData.passRate)<60?'danger':'success'">{{ statsData.passRate }}%</div></div></el-col>
          <el-col :span="6"><div class="stat-card"><div class="label">最高 / 最低</div><div class="value">{{ statsData.maxScore }} / {{ statsData.minScore }}</div></div></el-col>
          <el-col :span="6"><div class="stat-card"><div class="label">实考人数</div><div class="value">{{ statsData.realStudents }}</div></div></el-col>
        </el-row>

        <el-row :gutter="20" style="margin-top: 20px;">
          <el-col :span="14">
            <el-card shadow="hover" header="成绩分布趋势图">
              <div ref="chartRef" style="width: 100%; height: 320px;"></div>
            </el-card>
          </el-col>

          <el-col :span="10">
            <el-card shadow="hover" header="分布详情表" style="height: 100%">
              <el-table :data="distributionTable" border size="small" style="height: 320px">
                <el-table-column prop="label" label="分数段" />
                <el-table-column prop="count" label="人数" align="center" width="80" />
                <el-table-column label="比例" align="center">
                  <template #default="scope">
                    <el-progress :percentage="scope.row.percent" :format="p=>p+'%'" :status="scope.row.label.includes('不及格')?'exception':''"/>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <el-card class="analysis-input-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span><el-icon><EditPen /></el-icon> 试卷/成绩分析录入</span>
            <el-tag type="info" size="small">请根据上方统计图表填写分析报告</el-tag>
          </div>
        </template>

        <el-form :model="analysisForm" label-position="top">
          <el-form-item label="分析内容 (包括试题质量、学生掌握情况、改进措施等)">
            <el-input
                v-model="analysisForm.content"
                type="textarea"
                :rows="6"
                placeholder="请输入详细的成绩分析报告..."
                maxlength="2000"
                show-word-limit
            />
          </el-form-item>

          <div class="form-footer">
            <span class="save-tip" v-if="lastSavedTime">上次保存: {{ lastSavedTime }}</span>
            <el-button type="success" icon="Check" @click="saveAnalysis" :loading="saving">保存分析报告</el-button>
          </div>
        </el-form>
      </el-card>

    </div>
    <el-empty v-else description="请选择课程并点击生成按钮" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, EditPen, Check } from '@element-plus/icons-vue'
import request from '@/utils/request'
import * as echarts from 'echarts' // [关键] 引入 ECharts

const loading = ref(false)
const saving = ref(false)
const courses = ref([])
const statsData = ref(null)
const lastSavedTime = ref('')
const chartRef = ref(null) // 图表容器引用
let chartInstance = null   // 图表实例

const queryForm = reactive({ semester: '2024-2025-1', courseId: '' })
const analysisForm = reactive({ content: '' })

onMounted(async () => {
  const res = await request.get('/remote/client/teacher/courses')
  if (res.code === 200) courses.value = res.data

  // 监听窗口大小改变，重绘图表
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  if (chartInstance) chartInstance.dispose()
})

const resizeChart = () => {
  if (chartInstance) chartInstance.resize()
}

const handleGenerate = async () => {
  if (!queryForm.courseId) return ElMessage.warning('请先选择课程')
  loading.value = true

  try {
    // 1. 获取统计数据
    const statRes = await request.get('/remote/client/grade/stats', { params: queryForm })
    if (statRes.code === 200) {
      statsData.value = statRes.data

      // [关键] 数据获取成功后，等待 DOM 更新，然后渲染图表
      nextTick(() => {
        renderChart()
      })

      ElMessage.success('统计表已生成')
    }

    // 2. 获取历史分析
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

// [新增] 渲染折线图逻辑
const renderChart = () => {
  if (!chartRef.value || !statsData.value) return

  // 初始化或获取实例
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const dist = statsData.value.distribution || [0,0,0,0,0]

  // ECharts 配置项
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' }
    },
    grid: {
      left: '3%', right: '4%', bottom: '3%', containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false, // 折线图通常不需要两端留白
      data: ['不及格', '及格', '中等', '良好', '优秀']
    },
    yAxis: {
      type: 'value',
      name: '人数',
      minInterval: 1 // 保证Y轴是整数
    },
    series: [
      {
        name: '学生人数',
        type: 'line',
        data: dist,
        smooth: true, // 平滑曲线
        symbol: 'circle',
        symbolSize: 8,
        itemStyle: { color: '#1890ff' },
        areaStyle: { // 填充颜色，让趋势更明显
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(24,144,255,0.5)' },
            { offset: 1, color: 'rgba(24,144,255,0.01)' }
          ])
        },
        label: { show: true, position: 'top' } // 显示数值
      }
    ]
  }

  chartInstance.setOption(option)
}

const saveAnalysis = async () => {
  if (!analysisForm.content.trim()) return ElMessage.warning('分析内容不能为空')

  saving.value = true
  try {
    const payload = {
      courseId: queryForm.courseId,
      semester: queryForm.semester,
      content: analysisForm.content,
      avgScore: statsData.value.avgScore,
      passRate: statsData.value.passRate,
      maxScore: statsData.value.maxScore,
      minScore: statsData.value.minScore,
      distributionJson: JSON.stringify(statsData.value.distribution)
    }

    const res = await request.post('/remote/client/grade/analysis/save', payload)
    if (res.code === 200) {
      ElMessage.success('分析报告保存成功')
      lastSavedTime.value = new Date().toLocaleString()
    } else {
      ElMessage.error(res.message)
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
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

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>