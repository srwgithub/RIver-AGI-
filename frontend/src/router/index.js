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
      { path: '/collection-annotation', name: 'CollectionImport', component: () => import('../views/CollectionImport.vue') },
      { path: '/collection-annotation/config', name: 'CollectionTaskConfig', component: () => import('../views/CollectionTaskConfig.vue') },
      { path: '/collection-annotation/config/rules', name: 'CollectionRules', component: () => import('../views/CollectionRules.vue') },
      { path: '/chat', name: 'Chat', component: () => import('../views/Chat.vue') },
      { path: '/charts', name: 'Charts', component: () => import('../views/Charts.vue') },
      { path: '/prediction', name: 'Prediction', component: () => import('../views/Prediction.vue') },
      { path: '/trend-dashboard', name: 'TrendAnalysisCenter', component: () => import('../views/TrendAnalysisCenter.vue') },
      { path: '/trend-dashboard/config', name: 'TrendConfig', component: () => import('../views/TrendConfig.vue') },
      { path: '/trend-dashboard/reports', name: 'CustomReports', component: () => import('../views/CustomReports.vue') },
      { path: '/annotation-platform', name: 'AnnotationWorkbench', component: () => import('../views/AnnotationWorkbench.vue') },
      { path: '/annotation-quality', name: 'AnnotationQualityCenter', component: () => import('../views/AnnotationQualityCenter.vue') },
      { path: '/annotation-quality/rules', name: 'QualityRulesBackend', component: () => import('../views/QualityRulesBackend.vue') },
      { path: '/prediction-engine', name: 'PredictionEngineCenter', component: () => import('../views/PredictionEngineCenter.vue') },
      { path: '/prediction-engine/models', name: 'PredictionModelManagement', component: () => import('../views/PredictionModelManagement.vue') },
      { path: '/prediction-engine/config', name: 'PredictionEngineConfig', component: () => import('../views/PredictionEngineConfig.vue') },
      { path: '/prediction-evaluation', name: 'PredictionEvaluation', component: () => import('../views/PredictionEvaluation.vue') },
      { path: '/prediction-evaluation/config', name: 'PredictionEvaluationConfig', component: () => import('../views/PredictionEvaluationConfig.vue') },
      { path: '/security-audit', name: 'SecurityAuditCenter', component: () => import('../views/SecurityAuditCenter.vue') },
      { path: '/security-audit/config', name: 'SecurityConfig', component: () => import('../views/SecurityConfig.vue') },
      { path: '/security-audit/backup', name: 'BackupRecovery', component: () => import('../views/BackupRecovery.vue') },
      { path: '/model-optimization', name: 'ModelOptimization', component: () => import('../views/ModelOptimization.vue') },
      { path: '/model-optimization/accuracy', name: 'ModelAccuracyEvaluation', component: () => import('../views/ModelAccuracyEvaluation.vue') },
      { path: '/model-optimization/performance', name: 'ModelPerformanceMonitoring', component: () => import('../views/ModelPerformanceMonitoring.vue') },
      { path: '/model-optimization/bias', name: 'PredictionBiasAnalysis', component: () => import('../views/PredictionBiasAnalysis.vue') },
      { path: '/model-optimization/retraining', name: 'ModelRetrainingCenter', component: () => import('../views/ModelRetrainingCenter.vue') },
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
