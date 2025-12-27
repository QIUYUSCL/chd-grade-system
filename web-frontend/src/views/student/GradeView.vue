<template>
  <div class="grade-view">
    <div class="page-header">
      <h2>我的成绩单</h2>
      <div class="filter-bar">
        <el-select v-model="currentSemester" placeholder="选择学期" clearable @change="fetchData" style="width:200px">
          <el-option
              v-for="sem in semesters"
              :key="sem"
              :label="sem"
              :value="sem"
          />
        </el-select>
        <el-button type="primary" @click="fetchData" icon="Refresh">刷新</el-button>
        <el-button type="success" @click="printReport" icon="Printer">打印完整成绩单</el-button>
      </div>
    </div>

    <el-card class="grade-card" shadow="never">
      <el-descriptions title="个人信息" :column="4" border size="small" style="margin-bottom:15px">
        <el-descriptions-item label="姓名">{{ report.student.name }}</el-descriptions-item>
        <el-descriptions-item label="学号">{{ report.student.studentId }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ report.student.className }}</el-descriptions-item>
        <el-descriptions-item label="专业">{{ report.student.major }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ report.student.email }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="report.grades" stripe border style="width:100%" v-loading="loading" :header-cell-style="{background:'#f5f7fa',color:'#606266'}">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="course_name" label="课程名称" min-width="160" />
        <el-table-column prop="credit" label="学分" width="60" align="center" />
        <el-table-column prop="teacher_name" label="教师" width="90" align="center" />
        <el-table-column label="平时分项" align="center">
          <el-table-column prop="attendance_score" label="考勤" width="70" align="center"><template #default="{row}">{{ formatScore(row.attendance_score) }}</template></el-table-column>
          <el-table-column prop="homework_score" label="作业" width="70" align="center"><template #default="{row}">{{ formatScore(row.homework_score) }}</template></el-table-column>
          <el-table-column prop="experiment_score" label="实验" width="70" align="center"><template #default="{row}">{{ formatScore(row.experiment_score) }}</template></el-table-column>
          <el-table-column prop="midterm_score" label="期中" width="70" align="center"><template #default="{row}">{{ formatScore(row.midterm_score) }}</template></el-table-column>
        </el-table-column>
        <el-table-column prop="daily_score" label="平时总评" width="90" align="center"><template #default="{row}">{{ formatScore(row.daily_score) }}</template></el-table-column>
        <el-table-column prop="final_score" label="期末卷面" width="90" align="center"><template #default="{row}">{{ formatScore(row.final_score) }}</template></el-table-column>
        <el-table-column label="补考" width="80" align="center"><template #default="{row}">{{ row.exam_type==='补考'?formatScore(row.makeup_score):'--' }}</template></el-table-column>
        <el-table-column label="专业排名" width="100" align="center">
          <template #default="{row}">
            <div v-if="row.major_rank && row.major_rank !== '-'">
              <span style="font-weight:bold;color:#67C23A">{{ row.major_rank }}</span>
              <span style="font-size:12px;color:#909399">/ {{ row.major_total_count }}</span>
            </div>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="total_score" label="总成绩" width="100" align="center" fixed="right">
          <template #default="{row}">
            <span :class="getScoreClass(row.total_score)" style="font-size:16px">{{ row.total_score || '--' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && report.grades.length === 0" description="暂无成绩" />
    </el-card>

    <div v-if="isPrinting" class="print-only">
      <div class="print-header">
        <h2>长安大学学生个人成绩分析表</h2>
        <div class="info-row">
          <span>姓名：{{ report.student.name }}</span>
          <span>学号：{{ report.student.studentId }}</span>
          <span>班级：{{ report.student.className }}</span>
          <span>专业：{{ report.student.major }}</span>
          <span>邮箱：{{ report.student.email }}</span>
          <span>学期：{{ currentSemester || '全部学期' }}</span>
        </div>
      </div>
      <table class="print-table">
        <thead>
        <tr>
          <th>课程名称</th><th>学分</th><th>教师</th><th>平时分项</th><th>平时总评</th><th>期末卷面</th><th>补考</th><th>专业排名</th><th>总成绩</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(g, i) in report.grades" :key="i">
          <td>{{ g.course_name }}</td><td>{{ g.credit || '3.0' }}</td><td>{{ g.teacher_name }}</td>
          <td>考勤{{ formatScore(g.attendance_score) }}/作业{{ formatScore(g.homework_score) }}/实验{{ formatScore(g.experiment_score) }}/期中{{ formatScore(g.midterm_score) }}</td>
          <td>{{ formatScore(g.daily_score) }}</td><td>{{ formatScore(g.final_score) }}</td>
          <td>{{ g.exam_type==='补考'?formatScore(g.makeup_score):'--' }}</td>
          <td>{{ g.major_rank&&g.major_rank!=='-'?`${g.major_rank}/${g.major_total_count}`:'--' }}</td>
          <td>{{ g.total_score||'--' }}</td>
        </tr>
        </tbody>
      </table>
      <div class="print-footer">
        <p>打印日期：{{ new Date().toLocaleDateString() }}</p>
        <p class="sign-row">学生签字：________________　　辅导员签字：________________</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
// ✅ 引入 getSemesters
import { getFullReport, getSemesters } from '@/api/student'
import { ElMessage } from 'element-plus'

/* 数据 */
const loading = ref(false)
const currentSemester = ref('') // 默认空
const semesters = ref([]) // ✅ 存储学期列表
const isPrinting = ref(false)
const report = reactive({
  student: {},
  grades: []
})

// ✅ 初始化
const initData = async () => {
  try {
    const res = await getSemesters()
    if (res.code === 200 && res.data) {
      semesters.value = res.data
      // 默认选中第一个学期（最新的）
      if (res.data.length > 0) {
        currentSemester.value = res.data[0]
      }
    }
    // 无论有没有学期，都查一次数据
    fetchData()
  } catch (e) {
    ElMessage.error('初始化失败')
  }
}

/* 查询完整成绩单 */
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getFullReport(currentSemester.value)
    if (res.code === 200) {
      report.student = res.data.student || {}
      report.grades = res.data.grades || []
    } else {
      ElMessage.error(res.message || '查询失败')
    }
  } catch (e) {
    ElMessage.error('网络异常：' + e.message)
  } finally {
    loading.value = false
  }
}

/* 打印 */
const printReport = () => {
  if (report.grades.length === 0) {
    ElMessage.warning('暂无成绩数据，无法打印')
    return
  }

  isPrinting.value = true
  nextTick(() => {
    window.print()
    setTimeout(() => {
      isPrinting.value = false
    }, 100)
  })
}

/* 工具函数 */
const formatScore = val =>
    val === null || val === undefined || val === '' || String(val) === 'NaN' || val === '--' ? '' : val

const getScoreClass = score => {
  const n = parseFloat(score)
  if (isNaN(n)) return ''
  return n < 60 ? 'score-fail' : n >= 90 ? 'score-excellent' : 'score-pass'
}

onMounted(initData) // ✅ 替换 fetchData
</script>

<style scoped>
/* 样式保持不变 */
.page-header { display: flex; justify-content: space-between; margin-bottom: 20px; }
.filter-bar { display: flex; gap: 10px; align-items: center; }
.score-fail { color: #f56c6c; font-weight: bold; }
.score-excellent { color: #67c23a; font-weight: bold; }
.score-pass { color: #303133; }
.print-only { display: none; }
@media print {
  .common-layout > .sidebar, .common-layout > .main-content > .header, .page-header .el-button, .el-card__header, .grade-view > .grade-card { display: none !important; }
  .print-only { display: block !important; position: fixed; left: 0; top: 0; width: 100vw; height: 100vh; margin: 0; padding: 20px; font-size: 12px; color: #000; background: #fff; z-index: 9999; }
  .print-only table { width: 100%; border-collapse: collapse; margin-top: 10px; }
  .print-only th, .print-only td { border: 1px solid #000; padding: 6px 4px; text-align: center; }
  .print-only th { background: #f0f0f0; }
  .sign-row { margin-top: 40px; text-align: center; font-size: 13px; }
}
</style>