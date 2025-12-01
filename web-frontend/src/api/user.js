import request from '@/utils/request'

export const loginApi = (loginData) => {
    return request({
        url: '/remote/client/login',
        method: 'post',
        data: loginData
    })
};

// 修改密码 API（需要带 Token）
export const changePassword = (oldPassword, newPassword) => {
    return request({
        url: '/remote/client/password/change',
        method: 'post',
        data: {
            oldPassword,
            newPassword
        }
    })
};