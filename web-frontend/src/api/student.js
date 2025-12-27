import request from '@/utils/request'

/**
 * 获取当前登录学生的成绩列表
 * @param {string} semester - 学期 (可选，例如 "2024-2025-1")
 */
export const getMyGrades = (semester) => {
    return request({
        url: '/remote/client/student/my-grades',
        method: 'get',
        params: { semester }
    })
}

// 获取选课列表
export const getCourseSelectionList = (semester) => {
    return request({
        url: '/remote/client/student/course/selection-list',
        method: 'get',
        params: { semester }
    })
}

// 选课 (复用之前的，确保路径对齐)
export const selectCourse = (data) => {
    return request({
        url: '/remote/client/student/course/select',
        method: 'post',
        data
    })
}

// 退课
export const dropCourse = (data) => {
    return request({
        url: '/remote/client/student/course/drop',
        method: 'post',
        data // { courseId, semester }
    })
}
// 完整成绩单（含基本信息）
export const getFullReport = (semester) => request({
  url: '/remote/client/student/full-report',
  method: 'get',
  params: { semester }
})

/**
 * 获取系统所有学期
 */
export const getSemesters = () => {
    return request({
        url: '/remote/client/common/semesters',
        method: 'get'
    })
}