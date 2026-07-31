import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('../components/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
      { path: '/datasets', name: 'Datasets', component: () => import('../views/Datasets.vue') },
      { path: '/datasets/:id/preview', name: 'DatasetPreview', component: () => import('../views/DatasetPreview.vue') },
      { path: '/analysis', name: 'Analysis', component: () => import('../views/Analysis.vue') },
      { path: '/security', name: 'Security', component: () => import('../views/Security.vue') },
      { path: '/annotation', name: 'Annotation', component: () => import('../views/Annotation.vue') },
      { path: '/collection-annotation', name: 'CollectionAnnotation', component: () => import('../views/CollectionAnnotation.vue') },
      { path: '/chat', name: 'Chat', component: () => import('../views/Chat.vue') },
      { path: '/charts', name: 'Charts', component: () => import('../views/Charts.vue') },
      { path: '/prediction', name: 'Prediction', component: () => import('../views/Prediction.vue') },
      { path: '/trend-dashboard', name: 'TrendDashboard', component: () => import('../views/TrendDashboard.vue') },
      { path: '/annotation-platform', name: 'AnnotationPlatform', component: () => import('../views/AnnotationPlatform.vue') },
      { path: '/annotation-quality', name: 'AnnotationQuality', component: () => import('../views/AnnotationQuality.vue') },
      { path: '/annotation-quality/rules', name: 'AnnotationQualityRules', component: () => import('../views/AnnotationQualityRules.vue') },
      { path: '/prediction-engine', name: 'PredictionEngine', component: () => import('../views/PredictionEngine.vue') },
      { path: '/prediction-evaluation', name: 'PredictionEvaluation', component: () => import('../views/PredictionEvaluation.vue') },
      { path: '/security-audit', name: 'SecurityAudit', component: () => import('../views/SecurityAudit.vue') },
      { path: '/model-optimization', name: 'ModelOptimization', component: () => import('../views/ModelOptimization.vue') },
      { path: '/audit', name: 'Audit', component: () => import('../views/Audit.vue') },
      { path: '/security-admin', name: 'SecurityAdmin', component: () => import('../views/SecurityAdmin.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
