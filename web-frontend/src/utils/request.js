import axios from 'axios'

const request = axios.create({
    //baseURL: 'http://localhost:8080',
    baseURL: '',
    timeout: 10000
})

// 请求拦截器：添加Token
request.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    error => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
    response => response.data,
    error => {
        console.error('请求失败详情:', {
            status: error.response?.status,
            statusText: error.response?.statusText,
            data: error.response?.data,
            config: {
                url: error.config?.url,
                method: error.config?.method,
                headers: error.config?.headers
            }
        })
        
        const message = error.response?.data?.message || '请求失败'
        
        // 特殊处理403错误
        if (error.response?.status === 403) {
            console.warn('403权限错误，可能需要重新登录')
            // 可以在这里添加自动跳转到登录页的逻辑
        }
        
        return Promise.reject(new Error(message))
    }
)

export default request