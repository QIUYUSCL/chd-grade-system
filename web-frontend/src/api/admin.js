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

/**
 * 管理员重置用户密码
 */
export const adminResetPassword = (targetUserId, targetRole, newPassword) => {
    return request.post('/remote/client/password/admin/reset', {
        targetUserId,
        targetRole,
        newPassword
    });
};