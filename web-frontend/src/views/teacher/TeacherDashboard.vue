<template>
  <div class="teacher-dashboard">
    <el-config-provider :locale="zhCn">

      <div class="welcome-section no-print">
        <div class="welcome-content">
          <div class="avatar-box">
            <el-avatar :size="64" :icon="UserFilled" class="user-avatar" />
          </div>
          <div class="info-box">
            <h2 class="welcome-title">欢迎回来，{{ userInfo.name }} 老师</h2>
            <p class="welcome-subtitle">
              {{ currentDate }} | {{ userInfo.title || '教师' }} | {{ userInfo.department || '信息工程学院' }}
            </p>
          </div>
        </div>
      </div>

      <el-row :gutter="20" class="stat-cards">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card blue-card clickable" @click="openDetail('course')">
            <div class="stat-body">
              <div class="icon-wrapper"><el-icon><Reading /></el-icon></div>
              <div class="text-wrapper">
                <div class="stat-value">{{ courses.length }}</div>
                <div class="stat-label">执教课程总数</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card green-card clickable" @click="openDetail('semester')">
            <div class="stat-body">
              <div class="icon-wrapper"><el-icon><DataLine /></el-icon></div>
              <div class="text-wrapper">
                <div class="stat-value">{{ activeSemester }}</div>
                <div class="stat-label">当前教学学期</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card orange-card clickable" @click="openDetail('class')">
            <div class="stat-body">
              <div class="icon-wrapper"><el-icon><User /></el-icon></div>
              <div class="text-wrapper">
                <div class="stat-value">{{ classCountLoading ? '...' : uniqueClassCount }}</div>
                <div class="stat-label">覆盖行政班级</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card purple-card clickable" @click="openDetail('credit')">
            <div class="stat-body">
              <div class="icon-wrapper"><el-icon><Timer /></el-icon></div>
              <div class="text-wrapper">
                <div class="stat-value">{{ totalCredits }}</div>
                <div class="stat-label">总学分</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <div class="dashboard-main">
        <el-row :gutter="20">
          <el-col :span="16">
            <el-card class="action-card" shadow="never">
              <template #header>
                <div class="card-header">
                  <span><el-icon><Menu /></el-icon> 快捷工作台</span>
                </div>
              </template>
              <div class="quick-actions">
                <div class="action-item" @click="$router.push('/teacher/entry')">
                  <div class="action-icon bg-blue"><el-icon><EditPen /></el-icon></div>
                  <h3>成绩录入</h3>
                  <p>录入、修改、提交课程成绩</p>
                </div>
                <div class="action-item" @click="$router.push('/teacher/grade-view')">
                  <div class="action-icon bg-green"><el-icon><List /></el-icon></div>
                  <h3>成绩管理</h3>
                  <p>查看成绩单、打印报表</p>
                </div>
                <div class="action-item" @click="$router.push('/teacher/analysis')">
                  <div class="action-icon bg-orange"><el-icon><TrendCharts /></el-icon></div>
                  <h3>成绩分析</h3>
                  <p>生成试卷分析与质量报告</p>
                </div>
              </div>
            </el-card>

            <el-card class="course-list-card" shadow="never" style="margin-top: 20px;">
              <template #header>
                <div class="card-header">
                  <span><el-icon><Collection /></el-icon> 我的课程列表</span>
                </div>
              </template>
              <el-table :data="courses" stripe style="width: 100%" height="300">
                <el-table-column prop="semester" label="学期" width="140" sortable />
                <el-table-column prop="course_name" label="课程名称" min-width="150" />
                <el-table-column prop="course_id" label="课程代码" width="120" />
                <el-table-column prop="credit" label="学分" width="80" align="center" />
                <el-table-column label="操作" width="100" align="center">
                  <template #default>
                    <el-button link type="primary" size="small" @click="$router.push('/teacher/grade-view')">查看</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>

          <el-col :span="8">
            <el-card class="calendar-card" shadow="never">
              <template #header>
                <div class="card-header">
                  <span><el-icon><Calendar /></el-icon> 教学日历</span>
                </div>
              </template>
              <el-calendar v-model="calendarDate" class="mini-calendar" />
            </el-card>
          </el-col>
        </el-row>
      </div>

      <el-dialog
          v-model="dialogVisible"
          :title="dialogTitle"
          width="600px"
          align-center
          destroy-on-close
      >
        <div v-if="currentDetailType === 'course'">
          <el-table :data="courses" stripe border max-height="400">
            <el-table-column property="course_name" label="课程名称" />
            <el-table-column property="course_id" label="代码" width="100" />
            <el-table-column property="semester" label="学期" width="140" />
          </el-table>
        </div>

        <div v-if="currentDetailType === 'semester'">
          <el-alert type="success" :closable="false" style="margin-bottom: 15px;">
            <template #title>当前主要教学学期：<b>{{ activeSemester }}</b></template>
          </el-alert>
          <p style="margin-bottom:10px; font-weight:bold;">该学期执教课程：</p>
          <el-table :data="courses.filter(c => c.semester === activeSemester)" border>
            <el-table-column property="course_name" label="课程名称" />
            <el-table-column property="credit" label="学分" width="80" align="center" />
          </el-table>
        </div>

        <div v-if="currentDetailType === 'class'">
          <div v-if="classCountLoading" style="text-align: center; padding: 20px;">
            <el-icon class="is-loading"><Loading /></el-icon> 数据加载中...
          </div>
          <div v-else>
            <p style="margin-bottom: 10px; color: #606266;">
              本学期 ({{ activeSemester }}) 共覆盖 <b>{{ uniqueClassList.length }}</b> 个行政班级：
            </p>
            <div style="display: flex; flex-wrap: wrap; gap: 10px;">
              <el-tag
                  v-for="cls in uniqueClassList"
                  :key="cls"
                  type="warning"
                  effect="plain"
                  size="large"
              >
                {{ cls }}
              </el-tag>
            </div>
            <el-empty v-if="uniqueClassList.length === 0" description="暂无班级数据" :image-size="60" />
          </div>
        </div>

        <div v-if="currentDetailType === 'credit'">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; background:#f9fafc; padding:15px; border-radius:8px;">
            <span>总计学分</span>
            <span style="font-size: 24px; font-weight: bold; color: #722ed1;">{{ totalCredits }}</span>
          </div>
          <el-table :data="courses" stripe border max-height="300" show-summary sum-text="合计">
            <el-table-column property="course_name" label="课程名称" />
            <el-table-column property="semester" label="学期" width="140" />
            <el-table-column property="credit" label="学分" width="100" align="center" />
          </el-table>
        </div>

        <template #footer>
          <span class="dialog-footer">
            <el-button @click="dialogVisible = false">关闭</el-button>
          </span>
        </template>
      </el-dialog>

    </el-config-provider>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getTeacherCourses, getTeacherInfo, getCourseStudents } from '@/api/teacher'
import {
  UserFilled, Reading, DataLine, User, Timer,
  Menu, EditPen, List, TrendCharts, Collection, Calendar, Loading
} from '@element-plus/icons-vue'
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

const router = useRouter()
const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const courses = ref([])
const calendarDate = ref(new Date())
const currentDate = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

// 班级统计相关
const uniqueClassCount = ref(0)
const uniqueClassList = ref([]) // ✅ 新增：存储班级名称列表
const classCountLoading = ref(true)

// 弹窗控制相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const currentDetailType = ref('')

const activeSemester = computed(() => {
  if (courses.value.length === 0) return '2024-2025-1'
  const semesters = [...new Set(courses.value.map(c => c.semester))].sort().reverse()
  return semesters[0]
})

const totalCredits = computed(() => {
  return courses.value.reduce((sum, c) => sum + (parseFloat(c.credit) || 0), 0)
})

// ✅ 打开详情弹窗
const openDetail = (type) => {
  currentDetailType.value = type
  dialogVisible.value = true

  switch (type) {
    case 'course':
      dialogTitle.value = '执教课程明细'
      break
    case 'semester':
      dialogTitle.value = '学期教学任务'
      break
    case 'class':
      dialogTitle.value = '覆盖行政班级列表'
      break
    case 'credit':
      dialogTitle.value = '教学学分统计'
      break
  }
}

const initData = async () => {
  try {
    const res = await getTeacherCourses()
    if (res.code === 200) {
      courses.value = res.data
      calculateClassCount()
    }

    if (userInfo.value.userId) {
      const infoRes = await getTeacherInfo(userInfo.value.userId)
      if (infoRes.code === 200 && infoRes.data) {
        userStore.setUserInfo({
          ...userInfo.value,
          title: infoRes.data.title,
          department: infoRes.data.department || infoRes.data.dept_name
        })
      }
    }
  } catch (e) {
    console.error('初始化数据失败', e)
  }
}

// 统计班级详情
const calculateClassCount = async () => {
  if (courses.value.length === 0) {
    uniqueClassCount.value = 0
    classCountLoading.value = false
    return
  }

  const currentSemCourses = courses.value.filter(c => c.semester === activeSemester.value)

  if (currentSemCourses.length === 0) {
    uniqueClassCount.value = 0
    classCountLoading.value = false
    return
  }

  classCountLoading.value = true
  const classSet = new Set()

  try {
    const promises = currentSemCourses.map(c => getCourseStudents(c.course_id, c.semester))
    const results = await Promise.all(promises)

    results.forEach(res => {
      if (res.code === 200 && res.data) {
        res.data.forEach(stu => {
          if (stu.class_name) {
            classSet.add(stu.class_name)
          }
        })
      }
    })

    uniqueClassCount.value = classSet.size
    uniqueClassList.value = Array.from(classSet).sort() // ✅ 保存班级列表并排序
  } catch (e) {
    console.error("班级统计失败", e)
    uniqueClassCount.value = '-'
  } finally {
    classCountLoading.value = false
  }
}

onMounted(() => {
  initData()
})
</script>

<style scoped>
.teacher-dashboard {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.welcome-section {
  background: #fff;
  padding: 25px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.welcome-content { display: flex; align-items: center; gap: 20px; }
.user-avatar { background-color: #409eff; }
.welcome-title { margin: 0 0 8px 0; font-size: 24px; color: #303133; }
.welcome-subtitle { margin: 0; color: #909399; font-size: 14px; }

.stat-cards { margin-bottom: 20px; }
.stat-card { border: none; border-radius: 8px; transition: transform 0.3s; }
/* ✅ 新增鼠标手势和悬停效果 */
.stat-card.clickable { cursor: pointer; }
.stat-card:hover { transform: translateY(-5px); box-shadow: 0 8px 16px rgba(0,0,0,0.1); }

.stat-body { display: flex; align-items: center; padding: 10px; }
.icon-wrapper {
  width: 56px; height: 56px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; margin-right: 15px; color: white;
}
.blue-card .icon-wrapper { background: linear-gradient(135deg, #36cfc9 0%, #1890ff 100%); }
.green-card .icon-wrapper { background: linear-gradient(135deg, #b7eb8f 0%, #52c41a 100%); }
.orange-card .icon-wrapper { background: linear-gradient(135deg, #ffd591 0%, #fa8c16 100%); }
.purple-card .icon-wrapper { background: linear-gradient(135deg, #d3adf7 0%, #722ed1 100%); }

.text-wrapper .stat-value { font-size: 24px; font-weight: bold; color: #303133; line-height: 1.2; }
.text-wrapper .stat-label { font-size: 13px; color: #909399; margin-top: 4px; }

.action-card { border-radius: 8px; border: none; }
.card-header { font-weight: bold; font-size: 16px; display: flex; align-items: center; gap: 8px; }
.quick-actions { display: flex; gap: 20px; padding: 10px 0; }
.action-item {
  flex: 1;
  background: #f9fafc;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  border: 1px solid #ebeef5;
}
.action-item:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.1); border-color: #409eff; background: #ecf5ff; }
.action-icon {
  width: 48px; height: 48px; border-radius: 50%; margin: 0 auto 15px;
  display: flex; align-items: center; justify-content: center; color: white; font-size: 24px;
}
.action-item h3 { margin: 0 0 8px 0; color: #303133; font-size: 16px; }
.action-item p { margin: 0; color: #909399; font-size: 12px; }

.bg-blue { background: #409eff; }
.bg-green { background: #67c23a; }
.bg-orange { background: #e6a23c; }

.mini-calendar :deep(.el-calendar-table .el-calendar-day) { height: 40px; padding: 5px; text-align: center; }
</style>