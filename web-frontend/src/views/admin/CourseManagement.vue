<template>
  <div class="course-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课程管理</span>
          <el-button type="primary" @click="showAddDialog">添加课程</el-button>
        </div>
      </template>

      <el-form :model="queryForm" inline class="query-form">
        <el-form-item label="课程编号">
          <el-input v-model="queryForm.courseId" placeholder="请输入课程编号" clearable />
        </el-form-item>
        <el-form-item label="课程名称">
          <el-input v-model="queryForm.courseName" placeholder="请输入课程名称" clearable />
        </el-form-item>

        <el-form-item label="开课院系">
          <el-select
              v-model="queryForm.department"
              placeholder="请输入或选择开课院系"
              clearable
              filterable
              allow-create
              default-first-option
          >
            <el-option
                v-for="dept in departmentsList"
                :key="dept"
                :label="dept"
                :value="dept"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="任课教师">
          <el-select v-model="queryForm.teacherId" placeholder="请选择任课教师" clearable filterable>
            <el-option
                v-for="teacher in teachersList"
                :key="teacher.teacher_id"
                :label="`${teacher.name} (${teacher.teacher_id})`"
                :value="teacher.teacher_id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="学期">
          <el-select
              v-model="queryForm.semester"
              placeholder="请输入或选择学期"
              clearable
              filterable
              allow-create
              default-first-option
          >
            <el-option label="2024-2025-1" value="2024-2025-1" />
            <el-option label="2024-2025-2" value="2024-2025-2" />
            <el-option label="2023-2024-1" value="2023-2024-1" />
            <el-option label="2023-2024-2" value="2023-2024-2" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="courseList" v-loading="loading" border stripe>
        <el-table-column prop="course_id" label="课程编号" width="120" />
        <el-table-column prop="course_name" label="课程名称" width="200" />
        <el-table-column prop="credit" label="学分" width="80" />
        <el-table-column prop="department" label="开课院系" width="150" />
        <el-table-column prop="teacher_id" label="教师编号" width="100" />
        <el-table-column prop="teacher_name" label="任课教师" width="100" />
        <el-table-column prop="semester" label="学期" width="120" />
        <el-table-column prop="course_type" label="课程类型" width="100" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          class="pagination"
      />
    </el-card>

    <el-dialog
        :title="dialogTitle"
        v-model="dialogVisible"
        width="600px"
        @close="resetForm"
    >
      <el-form :model="courseForm" :rules="formRules" ref="courseFormRef" label-width="100px">
        <el-form-item label="课程编号" prop="courseId">
          <el-input v-model="courseForm.courseId" placeholder="请输入课程编号" :disabled="isEdit" />
        </el-form-item>

        <el-form-item label="课程名称" prop="courseName">
          <el-input v-model="courseForm.courseName" placeholder="请输入课程名称" />
        </el-form-item>

        <el-form-item label="学分" prop="credit">
          <el-input-number v-model="courseForm.credit" :min="0" :max="10" :step="0.5" />
        </el-form-item>

        <el-form-item label="开课院系" prop="department">
          <el-select
              v-model="courseForm.department"
              placeholder="请输入或选择开课院系"
              filterable
              allow-create
              default-first-option
          >
            <el-option
                v-for="dept in departmentsList"
                :key="dept"
                :label="dept"
                :value="dept"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="任课教师" prop="teacherId">
          <el-select
              v-model="courseForm.teacherId"
              placeholder="请选择任课教师"
              filterable
              @change="handleTeacherChange"
          >
            <el-option
                v-for="teacher in filteredTeachers"
                :key="teacher.teacher_id"
                :label="`${teacher.name} (${teacher.teacher_id}) - ${teacher.department}`"
                :value="teacher.teacher_id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="学期" prop="semester">
          <el-select
              v-model="courseForm.semester"
              placeholder="请输入或选择学期"
              filterable
              allow-create
              default-first-option
          >
            <el-option label="2025-2026-1" value="2025-2026-1" />
            <el-option label="2025-2026-2" value="2025-2026-2" />
            <el-option label="2024-2025-1" value="2024-2025-1" />
            <el-option label="2024-2025-2" value="2024-2025-2" />
            <el-option label="2023-2024-1" value="2023-2024-1" />
            <el-option label="2023-2024-2" value="2023-2024-2" />
          </el-select>
        </el-form-item>

        <el-form-item label="课程类型" prop="courseType">
          <el-select v-model="courseForm.courseType" placeholder="请选择课程类型">
            <el-option label="必修课" value="必修课" />
            <el-option label="选修课" value="选修课" />
            <el-option label="实践课" value="实践课" />
            <el-option label="实验课" value="实验课" />
          </el-select>
        </el-form-item>

        <el-form-item label="课程描述" prop="description">
          <el-input
              v-model="courseForm.description"
              type="textarea"
              :rows="3"
              placeholder="请输入课程描述"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  adminQueryCourses,
  adminAddCourse,
  adminUpdateCourse,
  adminDeleteCourse,
  getTeachersList,
  getDepartmentsList
} from '@/api/admin.js';

// 响应式数据
const loading = ref(false);
const courseList = ref([]);
const dialogVisible = ref(false);
const isEdit = ref(false);
const courseFormRef = ref();

// 下拉选择数据
const teachersList = ref([]);
// ✅ 1. 预设常用院系
const commonDepartments = [
  '信息工程学院',
  '公路学院',
  '汽车学院',
  '电子与控制工程学院',
  '经济与管理学院',
  '地质工程与测绘学院',
  '建筑工程学院',
  '水利与环境学院'
];
// 初始化时先放入常用院系
const departmentsList = ref([...commonDepartments]);
const selectedTeacher = ref(null);

// 查询表单
const queryForm = reactive({
  courseId: '',
  courseName: '',
  department: '',
  teacherId: '',
  semester: ''
});

// 分页信息
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
});

// 课程表单
const courseForm = reactive({
  courseId: '',
  courseName: '',
  credit: 0,
  department: '',
  teacherId: '',
  semester: '',
  courseType: '',
  description: ''
});

// 计算属性
const dialogTitle = computed(() => isEdit.value ? '编辑课程' : '添加课程');

// 根据选择的院系过滤教师
const filteredTeachers = computed(() => {
  if (!courseForm.department) {
    return teachersList.value;
  }
  // 注意：这里过滤逻辑可以根据实际情况调整，如果教师department和课程department不完全一致，可能需要放宽限制
  // 如果手动输入的院系是新的，可能没有匹配的教师，此时显示所有教师可能更好，或者仅作提示
  return teachersList.value;
  // 暂时改为不强制过滤，因为手动输入的院系可能还没教师关联
  // return teachersList.value.filter(teacher => teacher.department === courseForm.department);
});

// 表单验证规则
const formRules = {
  courseId: [{ required: true, message: '请输入课程编号', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  credit: [{ required: true, message: '请输入学分', trigger: 'blur' }],
  department: [{ required: true, message: '请输入开课院系', trigger: 'blur' }],
  teacherId: [{ required: true, message: '请输入教师编号', trigger: 'blur' }],
  semester: [{ required: true, message: '请选择学期', trigger: 'change' }],
  courseType: [{ required: true, message: '请选择课程类型', trigger: 'change' }]
};

// 获取教师和院系列表
const fetchTeachersAndDepartments = async () => {
  try {
    const [teachersResponse, departmentsResponse] = await Promise.all([
      getTeachersList(),
      getDepartmentsList()
    ]);

    if (teachersResponse.code === 200) {
      teachersList.value = teachersResponse.data;
    }

    if (departmentsResponse.code === 200) {
      // ✅ 2. 合并后端数据和常用数据，并去重
      const apiDepts = departmentsResponse.data || [];
      // 使用 Set 去重
      const mergedDepts = Array.from(new Set([...commonDepartments, ...apiDepts]));
      departmentsList.value = mergedDepts;
    }
  } catch (error) {
    console.error('获取教师和院系列表失败:', error);
    ElMessage.error('获取教师和院系列表失败：' + error.message);
  }
};

// 处理教师选择变化
const handleTeacherChange = (teacherId) => {
  const teacher = teachersList.value.find(t => t.teacher_id === teacherId);
  if (teacher) {
    selectedTeacher.value = teacher;
    // 如果没有选择院系，自动填充教师所在院系
    if (!courseForm.department) {
      courseForm.department = teacher.department;
    }
  }
};

const fetchCourses = async () => {
  loading.value = true;
  try {
    const params = {
      ...queryForm,
      page: pagination.page,
      pageSize: pagination.pageSize
    };

    const response = await adminQueryCourses(params);
    if (response.code === 200) {
      courseList.value = response.data.list;
      pagination.total = response.data.total;
    } else {
      ElMessage.error(response.message || '查询失败');
    }
  } catch (error) {
    console.error('查询课程失败:', error);
    ElMessage.error('查询失败：' + error.message);
  } finally {
    loading.value = false;
  }
};

// 处理查询
const handleQuery = () => {
  pagination.page = 1;
  fetchCourses();
};

// 重置查询条件
const resetQuery = () => {
  Object.keys(queryForm).forEach(key => {
    queryForm[key] = '';
  });
  pagination.page = 1;
  fetchCourses();
};

// 处理分页大小变化
const handleSizeChange = (val) => {
  pagination.pageSize = val;
  pagination.page = 1;
  fetchCourses();
};

// 处理页码变化
const handleCurrentChange = (val) => {
  pagination.page = val;
  fetchCourses();
};

// 显示添加对话框
const showAddDialog = async () => {
  isEdit.value = false;
  await fetchTeachersAndDepartments(); // 获取最新的教师和院系数据
  dialogVisible.value = true;
  resetForm();
};

// 处理编辑
const handleEdit = async (row) => {
  isEdit.value = true;
  await fetchTeachersAndDepartments(); // 获取最新的教师和院系数据

  // 填充表单数据
  courseForm.courseId = row.course_id;
  courseForm.courseName = row.course_name;
  courseForm.credit = row.credit;
  courseForm.department = row.department;
  courseForm.teacherId = row.teacher_id;
  courseForm.semester = row.semester;
  courseForm.courseType = row.course_type || '';
  courseForm.description = row.description || '';

  // 设置选中的教师
  const teacher = teachersList.value.find(t => t.teacher_id === row.teacher_id);
  if (teacher) {
    selectedTeacher.value = teacher;
  }

  dialogVisible.value = true;
};

// 处理删除
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
        `确定要删除课程 ${row.course_name}(${row.course_id}) 吗？`,
        '删除确认',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        }
    );

    loading.value = true;
    const response = await adminDeleteCourse(row.course_id);

    if (response.code === 200) {
      ElMessage.success('删除成功');
      fetchCourses();
    } else {
      ElMessage.error(response.message || '删除失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + error.message);
    }
  } finally {
    loading.value = false;
  }
};

// 处理表单提交
const handleSubmit = async () => {
  try {
    await courseFormRef.value.validate();

    loading.value = true;
    let response;

    if (isEdit.value) {
      response = await adminUpdateCourse(courseForm);
    } else {
      response = await adminAddCourse(courseForm);
    }

    if (response.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '添加成功');
      dialogVisible.value = false;
      fetchCourses();
    } else {
      ElMessage.error(response.message || '操作失败');
    }
  } catch (error) {
    ElMessage.error('操作失败：' + error.message);
  } finally {
    loading.value = false;
  }
};

// 重置表单
const resetForm = () => {
  if (courseFormRef.value) {
    courseFormRef.value.resetFields();
  }

  Object.keys(courseForm).forEach(key => {
    if (key === 'credit') {
      courseForm[key] = 0;
    } else {
      courseForm[key] = '';
    }
  });

  selectedTeacher.value = null;
};

// 组件挂载时获取数据
onMounted(() => {
  fetchCourses();
  fetchTeachersAndDepartments();
});
</script>

<style scoped>
.course-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.query-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

.el-table {
  margin-bottom: 20px;
}

.dialog-footer {
  text-align: right;
}
</style>