import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '@/stores/auth'
import { P } from '@/lib/labels'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    perm?: string          // UI uchun; backend baribir tekshiradi
    public?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: 'Kirish' } },
    {
      path: '/',
      component: () => import('@/components/app/AppLayout.vue'),
      children: [
        { path: '', component: () => import('@/views/DashboardView.vue'), meta: { title: 'Dashboard', perm: P.FARMER_READ } },
        { path: 'farmers', component: () => import('@/views/FarmersView.vue'), meta: { title: 'Fermerlar', perm: P.FARMER_READ } },
        { path: 'farmers/:id', component: () => import('@/views/FarmerDetailView.vue'), meta: { title: 'Fermer', perm: P.FARMER_READ } },
        { path: 'fields', component: () => import('@/views/FieldsView.vue'), meta: { title: 'Dalalar', perm: P.FIELD_READ } },
        { path: 'fields/new', component: () => import('@/views/FieldCreateView.vue'), meta: { title: 'Lab dala', perm: P.FIELD_WRITE } },
        { path: 'fields/:id', component: () => import('@/views/FieldDetailView.vue'), meta: { title: 'Dala', perm: P.FIELD_READ } },
        { path: 'analyses', component: () => import('@/views/AnalysesView.vue'), meta: { title: 'Tahlillar', perm: P.ANALYSIS_READ } },
        { path: 'analyses/:id', component: () => import('@/views/AnalysisDetailView.vue'), meta: { title: 'Tahlil', perm: P.ANALYSIS_READ } },
        { path: 'billing/plans', component: () => import('@/views/PlansView.vue'), meta: { title: 'Tariflar', perm: P.BILLING_READ } },
        { path: 'billing/discounts', component: () => import('@/views/DiscountsView.vue'), meta: { title: 'Chegirmalar', perm: P.BILLING_READ } },
        { path: 'billing/subscriptions', component: () => import('@/views/SubscriptionsView.vue'), meta: { title: 'Toʻlovlar', perm: P.BILLING_READ } },
        { path: 'feedback', component: () => import('@/views/FeedbackView.vue'), meta: { title: 'Murojaatlar', perm: P.FEEDBACK_READ } },
        { path: 'referrals', component: () => import('@/views/ReferralsView.vue'), meta: { title: 'Takliflar', perm: P.REFERRAL_READ } },
        { path: 'advisor', component: () => import('@/views/AdvisorView.vue'), meta: { title: 'AI maslahat', perm: P.ADVISOR_READ } },
        { path: 'problems', component: () => import('@/views/ProblemsView.vue'), meta: { title: 'Muammolar', perm: P.PROBLEMS_READ } },
        { path: 'notifications', component: () => import('@/views/NotificationsView.vue'), meta: { title: 'Bildirishnomalar', perm: P.NOTIFICATION_READ } },
        { path: 'onboarding', component: () => import('@/views/OnboardingView.vue'), meta: { title: 'Onboarding', perm: P.ONBOARDING_READ } },
        { path: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: 'Sozlamalar', perm: P.SETTINGS_READ } },
        { path: 'files', component: () => import('@/views/FilesView.vue'), meta: { title: 'Fayllar', perm: P.FILE_MANAGE } },
        { path: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: 'Xodimlar', perm: P.USER_READ } },
        { path: 'roles', component: () => import('@/views/RolesView.vue'), meta: { title: 'Rollar', perm: P.ROLE_READ } },
        { path: 'audit', component: () => import('@/views/AuditView.vue'), meta: { title: 'Audit', perm: P.AUDIT_READ } },
        { path: 'profile', component: () => import('@/views/ProfileView.vue'), meta: { title: 'Profil' } },
        { path: 'forbidden', component: () => import('@/views/ForbiddenView.vue'), meta: { title: 'Ruxsat yoʻq' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuth()
  // Ochiq sahifa (login) — sessiyani tekshirmaymiz: aks holda /auth/me 401 + ortiqcha /auth/refresh
  if (to.meta.public) return true
  if (!auth.loaded) await auth.load()
  if (!auth.me) return { path: '/login', query: to.fullPath !== '/' ? { next: to.fullPath } : {} }
  if (to.meta.perm && !auth.can(to.meta.perm)) {
    // Dashboard ruxsati yo'q (masalan USER) — profilga
    return to.path === '/' ? '/profile' : '/forbidden'
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · Dropy admin` : 'Dropy admin'
})

export default router
