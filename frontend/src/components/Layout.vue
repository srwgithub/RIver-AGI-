<template>
  <el-container class="layout-container">
    <el-aside
      class="sidebar-pro"
      :class="{ 'is-collapsed': isCollapsed }"
      :width="isCollapsed ? '64px' : '240px'"
    >
      <div class="sidebar-logo">
        <div class="logo-icon">R</div>
        <span class="logo-text">RIver AGI</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        :collapse="isCollapsed"
        :collapse-transition="false"
        background-color="transparent"
        text-color="rgba(255,255,255,0.65)"
        active-text-color="#ffffff"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <template #title>工作台</template>
        </el-menu-item>

        <el-menu-item index="/datasets">
          <el-icon><Files /></el-icon>
          <template #title>数据中心</template>
        </el-menu-item>

        <el-sub-menu index="annotation-sub" popper-class="sidebar-module-popper">
          <template #title>
            <el-icon><Upload /></el-icon>
            <span>数据采集与标注平台</span>
          </template>
          <el-menu-item-group title="数据采集与导入">
            <el-menu-item index="/collection-annotation">多源数据导入</el-menu-item>
            <el-menu-item index="/collection-annotation/config">任务配置与派发</el-menu-item>
          </el-menu-item-group>
          <el-menu-item-group title="标注执行">
            <el-menu-item index="/annotation-platform">标注工作台</el-menu-item>
          </el-menu-item-group>
          <el-menu-item-group title="规则配置">
            <el-menu-item index="/collection-annotation/config/rules">采集标注规则</el-menu-item>
          </el-menu-item-group>
        </el-sub-menu>

        <el-sub-menu index="quality-sub" popper-class="sidebar-module-popper">
          <template #title>
            <el-icon><CircleCheck /></el-icon>
            <span>标注质量管理</span>
          </template>
          <el-menu-item-group title="质量执行">
            <el-menu-item index="/annotation-quality">质量管理中心</el-menu-item>
          </el-menu-item-group>
          <el-menu-item-group title="规则配置">
            <el-menu-item index="/annotation-quality/rules">规则配置后台</el-menu-item>
          </el-menu-item-group>
        </el-sub-menu>

        <el-sub-menu index="prediction-sub" popper-class="sidebar-module-popper">
          <template #title>
            <el-icon><Cpu /></el-icon>
            <span>市场需求预测引擎</span>
          </template>
          <el-menu-item-group title="预测业务">
            <el-menu-item index="/prediction-engine">预测创建</el-menu-item>
          </el-menu-item-group>
          <el-menu-item-group title="模型与实验">
            <el-menu-item index="/prediction-engine/models">模型管理</el-menu-item>
          </el-menu-item-group>
          <el-menu-item-group title="引擎配置">
            <el-menu-item index="/prediction-engine/config">引擎规则配置</el-menu-item>
          </el-menu-item-group>
        </el-sub-menu>

        <el-sub-menu index="trend-sub" popper-class="sidebar-module-popper">
          <template #title>
            <el-icon><DataLine /></el-icon>
            <span>趋势分析与可视化</span>
          </template>
          <el-menu-item-group title="趋势分析">
            <el-menu-item index="/trend-dashboard">趋势看板</el-menu-item>
            <el-menu-item index="/charts">图表中心</el-menu-item>
          </el-menu-item-group>
          <el-menu-item-group title="报表配置">
            <el-menu-item index="/trend-dashboard/config">看板配置后台</el-menu-item>
            <el-menu-item index="/trend-dashboard/reports">自定义报表</el-menu-item>
          </el-menu-item-group>
        </el-sub-menu>

        <el-sub-menu index="optimization-sub" popper-class="sidebar-module-popper">
          <template #title>
            <el-icon><TrendCharts /></el-icon>
            <span>预测结果评估与优化</span>
          </template>
          <el-menu-item-group title="评估分析">
            <el-menu-item index="/model-optimization">模块总览</el-menu-item>
            <el-menu-item index="/model-optimization/accuracy">准确率评估</el-menu-item>
            <el-menu-item index="/model-optimization/performance">性能监控</el-menu-item>
            <el-menu-item index="/model-optimization/bias">偏差分析</el-menu-item>
          </el-menu-item-group>
          <el-menu-item-group title="模型优化">
            <el-menu-item index="/model-optimization/retraining">迭代优化与重训</el-menu-item>
            <el-menu-item index="/prediction-evaluation/config">优化配置中心</el-menu-item>
          </el-menu-item-group>
        </el-sub-menu>

        <el-sub-menu index="security-sub" popper-class="sidebar-module-popper">
          <template #title>
            <el-icon><Lock /></el-icon>
            <span>数据管理与安全审计</span>
          </template>
          <el-menu-item-group title="审计与追溯">
            <el-menu-item index="/security">安全中心</el-menu-item>
            <el-menu-item index="/audit">审计日志</el-menu-item>
          </el-menu-item-group>
          <el-menu-item-group title="安全管理">
            <el-menu-item index="/security-admin">权限管理</el-menu-item>
            <el-menu-item index="/security-audit/backup">备份恢复</el-menu-item>
            <el-menu-item index="/security-audit/config">安全配置后台</el-menu-item>
          </el-menu-item-group>
        </el-sub-menu>

        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>AI对话</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="top-header" height="60px">
        <div class="header-left">
          <div class="collapse-btn" @click="toggleCollapse">
            <el-icon :size="18">
              <Fold v-if="!isCollapsed" />
              <Expand v-else />
            </el-icon>
          </div>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index" :to="item.path">
              {{ item.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <div class="service-status"><span></span>服务正常</div>
          <div class="header-action-btn" title="搜索" @click="searchDialogVisible = true">
            <el-icon :size="18"><Search /></el-icon>
          </div>

          <el-badge :value="1" class="header-badge" @click="goToChat">
            <div class="header-action-btn" title="AI助手">
              <el-icon :size="18"><MagicStick /></el-icon>
            </div>
          </el-badge>

          <el-popover placement="bottom-end" :width="340" trigger="click">
            <template #reference>
              <el-badge :value="notifications.length" class="header-badge">
                <div class="header-action-btn" title="通知"><el-icon :size="18"><Bell /></el-icon></div>
              </el-badge>
            </template>
            <div class="notice-title">通知中心 <span>{{ notifications.length }} 条未读</span></div>
            <button v-for="notice in notifications" :key="notice.title" class="notice-item" @click="router.push(notice.path)">
              <span :class="notice.type"></span><div><b>{{ notice.title }}</b><small>{{ notice.detail }}</small></div><time>{{ notice.time }}</time>
            </button>
          </el-popover>

          <el-dropdown trigger="click" @command="handleUserCommand">
            <div class="user-info">
              <el-avatar :size="28" class="user-avatar">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span class="user-name">{{ user?.realName || user?.username || 'admin' }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人设置
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>

    <el-dialog v-model="searchDialogVisible" title="全局搜索" width="520px" :close-on-click-modal="true">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索数据集、模型、任务..."
        size="large"
        clearable
        autofocus
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <div class="search-results">
        <button v-for="item in searchResults" :key="item.path" @click="openSearchResult(item.path)">
          <el-icon><Search /></el-icon><span><b>{{ item.name }}</b><small>{{ item.group }}</small></span><el-icon><ArrowRight /></el-icon>
        </button>
        <el-empty v-if="searchKeyword && !searchResults.length" description="未找到相关功能" :image-size="56" />
      </div>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DataBoard,
  Files,
  Upload,
  CircleCheck,
  Cpu,
  DataLine,
  TrendCharts,
  Lock,
  ChatDotRound,
  Fold,
  Expand,
  Search,
  MagicStick,
  Bell,
  UserFilled,
  User,
  SwitchButton,
  ArrowRight
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true')
const searchKeyword = ref('')
const searchDialogVisible = ref(false)
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const navigationItems = [
  { name: '数据中心', group: '数据管理', path: '/datasets' }, { name: '多源数据导入', group: '数据采集与标注', path: '/collection-annotation' },
  { name: '任务配置与派发', group: '数据采集与标注', path: '/collection-annotation/config' }, { name: '标注工作台', group: '数据采集与标注', path: '/annotation-platform' }, { name: '采集标注规则', group: '数据采集与标注', path: '/collection-annotation/config/rules' }, { name: '质量抽检', group: '标注质量管理', path: '/annotation-quality' },
  { name: '预测创建', group: '市场需求预测', path: '/prediction-engine' }, { name: '模型管理后台', group: '市场需求预测', path: '/prediction-engine/models' }, { name: '引擎规则配置', group: '市场需求预测', path: '/prediction-engine/config' }, { name: '趋势看板', group: '趋势分析', path: '/trend-dashboard' },
  { name: '图表中心', group: '趋势分析', path: '/charts' }, { name: '看板配置后台', group: '趋势分析', path: '/trend-dashboard/config' }, { name: '安全中心', group: '安全审计', path: '/security' },
  { name: '审计日志', group: '安全审计', path: '/audit' }, { name: '安全配置后台', group: '安全审计', path: '/security-audit/config' }, { name: 'AI 对话', group: '智能助手', path: '/chat' }
]
const notifications = [
  { title: '数据解析完成', detail: '综合测试数据已可用于分析', time: '15:56', type: 'success', path: '/datasets' },
  { title: '安全扫描完成', detail: '未发现高风险数据项', time: '15:58', type: 'success', path: '/security' },
  { title: '任务等待配置', detail: '预测任务尚未创建', time: '昨天', type: 'warning', path: '/prediction-engine' }
]
const searchResults = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  return (keyword ? navigationItems.filter(item => `${item.name}${item.group}`.toLowerCase().includes(keyword)) : navigationItems.slice(0, 6))
})
const openSearchResult = path => { searchDialogVisible.value = false; searchKeyword.value = ''; router.push(path) }

const activeMenu = computed(() => route.fullPath)
const defaultOpeneds = computed(() => {
  const modulePaths = {
    '/collection-annotation': 'annotation-sub',
    '/annotation-platform': 'annotation-sub',
    '/annotation-quality': 'quality-sub',
    '/prediction-engine': 'prediction-sub',
    '/trend-dashboard': 'trend-sub',
    '/trend-dashboard/reports': 'trend-sub',
    '/charts': 'trend-sub',
    '/model-optimization': 'optimization-sub',
    '/prediction-evaluation': 'optimization-sub',
    '/security': 'security-sub',
    '/audit': 'security-sub',
    '/security-admin': 'security-sub'
  }
  const key = Object.keys(modulePaths).find(path => route.path.startsWith(path))
  return key ? [modulePaths[key]] : []
})

const menuTitleMap = {
  '/dashboard': { title: '工作台', parent: null },
  '/datasets': { title: '数据中心', parent: null },
  '/datasets/:id/preview': { title: '数据集预览', parent: { name: '数据中心', path: '/datasets' } },
  '/analysis': { title: '数据分析', parent: null },
  '/collection-annotation': { title: '多源数据导入', parent: { name: '数据采集与标注平台', path: '/collection-annotation' } },
  '/collection-annotation/config': { title: '任务配置与派发', parent: { name: '数据采集与标注平台', path: '/collection-annotation' } },
  '/collection-annotation/config/rules': { title: '采集标注规则', parent: { name: '数据采集与标注平台', path: '/collection-annotation' } },
  '/annotation-platform': { title: '标注工作台', parent: { name: '数据采集与标注平台', path: '/annotation-platform' } },
  '/annotation-quality': { title: '质量管理中心', parent: { name: '标注质量管理', path: '/annotation-quality' } },
  '/annotation-quality/rules': { title: '规则配置后台', parent: { name: '标注质量管理', path: '/annotation-quality' } },
  '/prediction-engine': { title: '预测创建', parent: { name: '市场需求预测引擎', path: '/prediction-engine' } },
  '/prediction-engine/models': { title: '模型管理后台', parent: { name: '市场需求预测引擎', path: '/prediction-engine' } },
  '/prediction-engine/config': { title: '引擎规则配置', parent: { name: '市场需求预测引擎', path: '/prediction-engine' } },
  '/prediction': { title: '预测详情', parent: { name: '市场需求预测引擎', path: '/prediction-engine' } },
  '/trend-dashboard': { title: '趋势看板', parent: { name: '趋势分析与可视化', path: '/charts' } },
  '/trend-dashboard/config': { title: '看板配置后台', parent: { name: '趋势分析与可视化', path: '/trend-dashboard' } },
  '/trend-dashboard/reports': { title: '自定义报表', parent: { name: '趋势分析与可视化', path: '/trend-dashboard' } },
  '/charts': { title: '图表中心', parent: null },
  '/model-optimization': { title: '预测结果评估与优化', parent: null },
  '/model-optimization/accuracy': { title: '模型准确率评估', parent: { name: '预测结果评估与优化', path: '/model-optimization' } },
  '/model-optimization/performance': { title: '模型性能监控中心', parent: { name: '预测结果评估与优化', path: '/model-optimization' } },
  '/model-optimization/bias': { title: '预测偏差分析工作台', parent: { name: '预测结果评估与优化', path: '/model-optimization' } },
  '/model-optimization/retraining': { title: '迭代优化与 Retraining', parent: { name: '预测结果评估与优化', path: '/model-optimization' } },
  '/prediction-evaluation': { title: '预测评估', parent: { name: '预测结果评估与优化', path: '/model-optimization' } },
  '/prediction-evaluation/config': { title: '预测评估中心及模型优化系统', parent: { name: '预测结果评估与优化', path: '/model-optimization' } },
  '/security-audit': { title: '安全审计', parent: { name: '数据管理与安全审计', path: '/security-audit' } },
  '/security-audit/config': { title: '安全配置后台', parent: { name: '数据管理与安全审计', path: '/security-audit' } },
  '/security': { title: '安全中心', parent: { name: '数据管理与安全审计', path: '/security' } },
  '/audit': { title: '审计日志', parent: { name: '数据管理与安全审计', path: '/audit' } },
  '/security-admin': { title: '权限管理', parent: { name: '数据管理与安全审计', path: '/security-admin' } },
  '/chat': { title: 'AI对话', parent: null },
  '/annotation': { title: '标注任务', parent: { name: '数据采集与标注平台', path: '/annotation-platform' } }
}

const breadcrumbs = computed(() => {
  const basePath = route.path.split('?')[0]
  const items = []
  const menuItem = menuTitleMap[basePath] || menuTitleMap[route.path]
  if (menuItem?.parent) {
    items.push(menuItem.parent)
  }
  const pageTitle = menuItem?.title || ''
  if (pageTitle && route.path !== '/dashboard') {
    items.push({ name: pageTitle, path: route.path })
  }
  return items
})

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  localStorage.setItem('sidebarCollapsed', String(isCollapsed.value))
}

const goToChat = () => {
  router.push('/chat')
}

const navigateTo = (path, tab) => {
  if (tab) {
    router.push({ path, query: { tab } })
  } else {
    router.push(path)
  }
}

const handleUserCommand = (command) => {
  if (command === 'logout') {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('river_view_mode')
    localStorage.removeItem('sidebarCollapsed')
    router.push('/login')
  }
}

const handleResize = () => {
  if (window.innerWidth < 768 && !isCollapsed.value) {
    isCollapsed.value = true
    localStorage.setItem('sidebarCollapsed', 'true')
  }
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  overflow: hidden;
}

.sidebar-pro {
  overflow-y: auto;
  overflow-x: hidden;
}
.sidebar-pro::-webkit-scrollbar {
  width: 0;
}

.sidebar-pro.is-collapsed .sidebar-logo {
  padding: 0 16px;
  justify-content: center;
}
.sidebar-pro.is-collapsed .sidebar-logo .logo-text {
  display: none;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-2);
}
.collapse-btn:hover {
  background: var(--bg-hover);
  color: var(--primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.service-status { display: flex; align-items: center; gap: 7px; margin-right: 8px; color: var(--text-3); font-size: 12px; }
.service-status span { width: 7px; height: 7px; border-radius: 50%; background: var(--success); box-shadow: 0 0 0 3px var(--success-light); }
.notice-title { display: flex; justify-content: space-between; padding: 4px 4px 12px; color: var(--text-1); font-weight: 600; border-bottom: 1px solid var(--border-1); }
.notice-title span { color: var(--text-3); font-size: 12px; font-weight: 400; }
.notice-item { width: 100%; display: grid; grid-template-columns: 8px 1fr auto; align-items: start; gap: 10px; padding: 13px 4px; border: 0; border-bottom: 1px solid var(--border-1); background: #fff; text-align: left; cursor: pointer; }
.notice-item:hover { background: var(--bg-hover); }.notice-item > span { width: 7px; height: 7px; margin-top: 5px; border-radius: 50%; }.notice-item > span.success { background: var(--success); }.notice-item > span.warning { background: var(--warning); }.notice-item b, .notice-item small { display: block; }.notice-item b { color: var(--text-1); font-size: 13px; }.notice-item small, .notice-item time { margin-top: 4px; color: var(--text-3); font-size: 11px; }
.search-results { margin-top: 12px; max-height: 360px; overflow: auto; }.search-results button { width: 100%; display: grid; grid-template-columns: 24px 1fr 18px; align-items: center; gap: 10px; padding: 11px 10px; border: 0; border-radius: 6px; color: var(--text-3); background: #fff; text-align: left; cursor: pointer; }.search-results button:hover { color: var(--primary); background: var(--primary-light); }.search-results b, .search-results small { display: block; }.search-results b { color: var(--text-1); font-size: 14px; }.search-results small { margin-top: 3px; color: var(--text-3); font-size: 12px; }

.header-badge {
  cursor: pointer;
}

.header-action-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-2);
}
.header-action-btn:hover {
  background: var(--bg-hover);
  color: var(--primary);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-left: 4px;
}
.user-info:hover {
  background: var(--bg-hover);
}

.user-avatar {
  background: var(--primary);
}

.user-name {
  font-size: var(--font-size-base);
  color: var(--text-1);
  font-weight: 400;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-content {
  background: var(--bg-body);
  padding: 0;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .service-status { display: none; }
  .user-name {
    display: none;
  }
}
</style>
