import request from '@/utils/request.js';

/**
 * 管理员重置用户密码
 * @param {string} targetUserId - 目标用户ID
 * @param {string} targetRole - 目标用户角色（STUDENT/TEACHER）
 * @param {string} newPassword - 新密码
 */
export const adminResetPassword = (targetUserId, targetRole, newPassword) => {
    return request.post('/remote/client/password/admin/reset', {
        targetUserId,
        targetRole,
        newPassword
    });
};

/**
 * 获取用户列表（分页）
 */
export const getUserList = (role, page = 1, pageSize = 20, userId = '') => {
    const dto = {
        operation: "SELECT",
        table: role === 'TEACHER' ? 'teachers' : 'students',
        conditions: userId ? { [role === 'TEACHER' ? 'teacher_id' : 'student_id']: userId } : {},
        role: 'ADMIN'
    };

    return request.post('/remote/client/query', dto, {
        params: { page, pageSize }
    });
};

// ==================== 成绩录入管理 ====================

/**
 * 管理员查询成绩记录
 */
export const adminQueryGrades = (params) => {
    return request.post('/remote/client/admin/grade/query', params);
};

/**
 * 管理员小撤销：将已提交成绩改为草稿状态
 */
export const adminMinorRevoke = (recordId) => {
    return request.post('/remote/client/admin/grade/minor-revoke', { recordId });
};

/**
 * 管理员大撤销：完全删除成绩记录
 */
export const adminMajorRevoke = (recordId) => {
    return request.post('/remote/client/admin/grade/major-revoke', { recordId });
};

// ==================== 人员组织库维护 ====================

/**
 * 管理员查询用户列表
 */
export const adminQueryUsers = (params) => {
    return request.post('/remote/client/admin/users/query', params);
};

/**
 * 管理员添加用户
 */
export const adminAddUser = (userData) => {
    return request.post('/remote/client/admin/users/add', userData);
};

/**
 * 管理员更新用户信息
 */
export const adminUpdateUser = (userData) => {
    return request.post('/remote/client/admin/users/update', userData);
};

/**
 * 管理员删除用户
 */
export const adminDeleteUser = (userType, userId) => {
    return request.post('/remote/client/admin/users/delete', { userType, userId });
};

// ==================== 安全管理 ====================

/**
 * 管理员查询安全日志
 */
export const adminQuerySecurityLog = (params) => {
    return request.post('/remote/client/admin/security-log/query', params);
};

/**
 * 管理员数据完整性校验
 */
export const adminVerifyDataIntegrity = () => {
    return request.post('/remote/client/admin/data/verify');
};

// ==================== 课程管理 ====================

/**
 * 管理员查询课程列表
 */
export const adminQueryCourses = (params) => {
    return request.post('/remote/client/admin/courses/query', params);
};

/**
 * 管理员添加课程
 */
export const adminAddCourse = (courseData) => {
    return request.post('/remote/client/admin/courses/add', courseData);
};

/**
 * 管理员更新课程信息
 */
export const adminUpdateCourse = (courseData) => {
    return request.post('/remote/client/admin/courses/update', courseData);
};

/**
 * 管理员删除课程
 */
export const adminDeleteCourse = (courseId) => {
    return request.post('/remote/client/admin/courses/delete', { courseId });
};

/**
 * 管理员查询选课关系
 */
export const adminQueryStudentCourses = (params) => {
    return request.post('/remote/client/admin/student-courses/query', params);
};

/**
 * 管理员添加选课关系
 */
export const adminAddStudentCourse = (scData) => {
    return request.post('/remote/client/admin/student-courses/add', scData);
};

/**
 * 管理员删除选课关系
 */
export const adminDeleteStudentCourse = (id) => {
    return request.post('/remote/client/admin/student-courses/delete', { id });
};

/**
 * 获取教师列表（用于课程管理下拉选择）
 */
export const getTeachersList = () => {
    return request.get('/remote/client/admin/teachers/list');
};

/**
 * 获取院系列表（用于课程管理下拉选择）
 */
export const getDepartmentsList = () => {
    return request.get('/remote/client/admin/departments/list');
};
