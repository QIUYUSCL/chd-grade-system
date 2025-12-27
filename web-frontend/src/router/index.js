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
    {
        path: '/password/change',
        name: 'ChangePassword',
        component: () => import('@/components/ChangePassword.vue'),
        meta: { requiresAuth: true } // 需要登录
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


   // 5. 学生端路由
       {
           path: '/student',
           component: () => import('@/components/common/Layout.vue'),
           meta: { requiresAuth: true, roles: ['STUDENT'] },
           children: [
               {
                   path: 'grades',
                   component: () => import('@/views/student/GradeView.vue')
               },
               // [新增] 选课页面路由
               {
                   path: 'course-selection',
                   name: 'StudentCourseSelection',
                   component: () => import('@/views/student/CourseSelection.vue'),
                   meta: { title: '选课界面' }
               }
           ]
       },


    // 教师相关路由
    {
        path: '/teacher',
        component: () => import('@/components/common/Layout.vue'),
        meta: { requiresAuth: true, roles: ['TEACHER'] },
        // 重定向到 dashboard
        redirect: '/teacher/dashboard',
        children: [
            {
                path: 'dashboard',
                name: 'TeacherDashboard',
                component: () => import('@/views/teacher/TeacherDashboard.vue'),
                meta: { title: '教师工作台' }
            },
            { path: 'entry', component: () => import('@/views/teacher/ScoreEntry.vue'), meta: { title: '成绩录入' } },
            { path: 'grade-view', component: () => import('@/views/teacher/GradeView.vue'), meta: { title: '成绩管理' } },
            { path: 'analysis', component: () => import('@/views/teacher/GradeAnalysis.vue'), meta: { title: '成绩分析' } }
        ]
    },





    // 管理员相关路由
    {
        path: '/admin',
        component: () => import('@/components/common/Layout.vue'),
        meta: { requiresAuth: true, roles: ['ADMIN'] },
        children: [
            { path: 'management', component: () => import('@/views/admin/Management.vue') },
            { path: 'grade-management', component: () => import('@/views/admin/GradeManagement.vue') },
            { path: 'user-management', component: () => import('@/views/admin/UserManagement.vue') },
            { path: 'course-management', component: () => import('@/views/admin/CourseManagement.vue') },
            { path: 'security', component: () => import('@/views/admin/SecurityManagement.vue') }
        ]
    },
    {
        path: '/admin/users',
        name: 'UserList',
        component: () => import('@/views/admin/UserList.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
        path: '/admin/password-reset',
        name: 'PasswordReset',
        component: () => import('@/views/admin/PasswordReset.vue'),
        meta: { requiresAuth: true, requiresAdmin: true }
    }

]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const userStore = useUserStore();

    if (to.meta.requiresAuth && !userStore.isAuthenticated) {
        next('/login');
    } else if (to.meta.requiresAdmin && !userStore.isAdmin) {
        next('/'); // 非管理员跳转首页
    } else {
        next();
    }
});

export default router