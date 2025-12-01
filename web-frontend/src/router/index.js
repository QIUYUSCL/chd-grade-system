import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: () => import('@/views/login/Login.vue')
    },
    {
        path: '/',
        redirect: '/login'
    },
    // 通用个人信息页面
    {
        path: '/profile',
        component: () => import('@/components/common/Layout.vue'),
        meta: { requiresAuth: true },
        children: [
            { path: '', component: () => import('@/components/common/Profile.vue') }
        ]
    },
    // 学生相关路由
    {
        path: '/student',
        component: () => import('@/components/common/Layout.vue'),
        meta: { requiresAuth: true, roles: ['STUDENT'] },
        children: [
            { path: 'grades', component: () => import('@/views/student/GradeView.vue') }
        ]
    },
    // 教师相关路由
    {
        path: '/teacher',
        component: () => import('@/components/common/Layout.vue'),
        meta: { requiresAuth: true, roles: ['TEACHER'] },
        children: [
            { path: 'entry', component: () => import('@/views/teacher/ScoreEntry.vue') }
        ]
    },
    // 管理员相关路由
    {
        path: '/admin',
        component: () => import('@/components/common/Layout.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN'] },
        children: [
            { path: 'management', component: () => import('@/views/admin/Management.vue') }
        ]
    },
    {
        path: '/password/change',
        name: 'ChangePassword',
        component: () => import('@/components/ChangePassword.vue'),
        meta: { requiresAuth: true } // 需要登录
    },
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const userStore = useUserStore()

    if (to.path === '/login') {
        next()
        return
    }

    if (!userStore.isAuthenticated()) {
        next('/login')
        return
    }

    const requiredRoles = to.meta.roles
    if (requiredRoles && !requiredRoles.includes(userStore.userInfo.role)) {
        next('/login')
        return
    }

    next()
})

export default router