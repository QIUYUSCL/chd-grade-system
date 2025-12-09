import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', {
    state: () => ({
        token: localStorage.getItem('token') || '',
        // 从本地存储恢复用户信息
        userInfo: JSON.parse(localStorage.getItem('userInfo') || '{}')
    }),
    actions: {
        setToken(token) {
            this.token = token
            localStorage.setItem('token', token)

            // [新增] 解析 JWT Token 获取包含姓名的完整信息
            if (token) {
                try {
                    const base64Url = token.split('.')[1]
                    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
                    // 处理中文乱码的关键步骤
                    const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function(c) {
                        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)
                    }).join(''))

                    const payload = JSON.parse(jsonPayload)

                    // 保存完整信息：ID、角色、姓名
                    this.setUserInfo({
                        userId: payload.userId,
                        role: payload.role,
                        name: payload.name // 后端 Token 中包含了 name
                    })
                } catch (e) {
                    console.error('Token解析失败:', e)
                }
            }
        },
        setUserInfo(info) {
            this.userInfo = info
            localStorage.setItem('userInfo', JSON.stringify(info))
        },
        clearUser() {
            this.token = ''
            this.userInfo = {}
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
        },
        isAuthenticated() {
            return !!this.token
        }
    }
})