import request from '@/utils/request'

// ==================== 基础信息查询 ====================


//  获取教师详细信息（姓名、职称、院系等）
export const getTeacherInfo = (teacherId) => {
    return request({
        url: '/remote/client/teacher/info',
        method: 'post',
        data: { teacherId }
    })
}

/**
 * 获取当前教师教授的所有课程列表
 */
export const getTeacherCourses = () => {
    return request({
        url: '/remote/client/teacher/courses',
        method: 'get'
    })
}

/**
 * 获取某课程某学期的学生名单（用于批量录入、报表班级统计）
 * @param {string} courseId
 * @param {string} semester
 */
export const getCourseStudents = (courseId, semester) => {
    return request({
        url: '/remote/client/course/students',
        method: 'get',
        params: { courseId, semester }
    })
}

/**
 * 根据学号查询学生姓名（单条录入回显用）
 */
export const getStudentName = (studentId) => {
    return request({
        url: '/remote/client/student/name',
        method: 'get',
        params: { studentId }
    })
}

/**
 * 获取学生本学期已选课程（单条录入下拉框用）
 */
export const getStudentSelectedCourses = (studentId, semester) => {
    return request({
        url: '/remote/client/student/selected-courses',
        method: 'get',
        params: { studentId, semester }
    })
}

// ==================== 成绩录入 ====================

/**
 * 单条成绩录入
 * @param {Object} data { studentId, courseId, semester, examType, status, ...scores }
 */
export const entryGrade = (data) => {
    return request({
        url: '/remote/client/grade/entry',
        method: 'post',
        data
    })
}

/**
 * 批量成绩录入
 * @param {Object} data { grades: [...] }
 */
export const batchEntryGrade = (data) => {
    return request({
        url: '/remote/client/grade/batch-entry',
        method: 'post',
        data
    })
}

// ==================== 成绩管理（查询、修改、撤销） ====================

/**
 * 教师查询已录入的成绩记录（分页）
 */
export const queryGrades = (params) => {
    return request({
        url: '/remote/client/grade/view',
        method: 'get',
        params
    })
}

/**
 * 修改成绩（仅草稿状态可用）
 * @param {string} recordId
 * @param {Object} updateData 需要更新的字段
 */
export const updateGrade = (recordId, updateData) => {
    return request({
        url: '/remote/client/grade/update',
        method: 'post',
        data: { recordId, data: updateData }
    })
}

/**
 * 撤销暂存成绩
 */
export const revokeGrade = (recordId) => {
    return request({
        url: '/remote/client/grade/revoke',
        method: 'post',
        data: { recordId }
    })
}

// ==================== 成绩分析 ====================

/**
 * 获取成绩统计数据（平均分、直方图数据等）
 */
export const getGradeStats = (params) => {
    return request({
        url: '/remote/client/grade/stats',
        method: 'get',
        params
    })
}

/**
 * 获取已保存的分析报告内容
 */
export const getAnalysisContent = (params) => {
    return request({
        url: '/remote/client/grade/analysis/get',
        method: 'get',
        params
    })
}

/**
 * 保存分析报告内容
 */
export const saveAnalysisContent = (data) => {
    return request({
        url: '/remote/client/grade/analysis/save',
        method: 'post',
        data
    })
}