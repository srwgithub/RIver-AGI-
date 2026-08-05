<template>
  <div class="config-page page-container">
    <header class="page-header">
      <div>
        <div class="eyebrow">MODEL EVALUATION GOVERNANCE</div>
        <h1>预测评估中心及模型优化系统</h1>
        <p>统一管理评估标准、监控阈值、偏差规则、调优边界与自动化 Retraining 策略</p>
      </div>
      <div class="header-actions"><el-button :icon="ArrowLeft" @click="router.push('/model-optimization')">返回业务中心</el-button><el-button :icon="RefreshLeft" @click="restore">恢复默认</el-button><el-button type="primary" :icon="Check" :loading="saving" @click="save">保存并生效</el-button></div>
    </header>

    <el-alert title="本页面仅配置底层规则与自动化策略，不执行模型评估、分析或重训练任务。" type="info" :closable="false" show-icon />

    <div class="config-layout">
      <aside class="config-nav">
        <button v-for="item in sections" :key="item.id" :class="{ active: activeSection === item.id }" @click="scrollTo(item.id)"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.title }}<small>{{ item.desc }}</small></span></button>
      </aside>

      <main class="config-content">
        <section id="accuracy" class="config-card">
          <div class="section-heading"><span class="section-icon blue"><el-icon><TrendCharts /></el-icon></span><div><h2>准确率评估规则</h2><p>定义模型合格标准、误差评级与综合评分权重</p></div><el-switch v-model="form.accuracy.enabled" active-text="启用" /></div>
          <div class="form-grid three"><el-form-item label="模型合格准确率阈值"><el-input-number v-model="form.accuracy.passThreshold" :min="50" :max="100" :step="0.5"/><span class="unit">%</span></el-form-item><el-form-item label="优秀 MAPE 上限"><el-input-number v-model="form.accuracy.excellentMape" :min="0" :max="50" :step="0.5"/><span class="unit">%</span></el-form-item><el-form-item label="合格 MAPE 上限"><el-input-number v-model="form.accuracy.passMape" :min="0" :max="100" :step="0.5"/><span class="unit">%</span></el-form-item></div>
          <el-divider content-position="left">评估指标权重</el-divider>
          <div class="weight-grid"><div v-for="item in weightFields" :key="item.key"><div><span>{{ item.label }}</span><b>{{ form.accuracy.weights[item.key] }}%</b></div><el-slider v-model="form.accuracy.weights[item.key]" :max="100" /></div></div><div class="weight-total" :class="{ error: weightTotal !== 100 }"><span>当前权重合计</span><b>{{ weightTotal }}%</b><small>{{ weightTotal === 100 ? '权重配置有效' : '权重合计必须为 100%' }}</small></div>
        </section>

        <section id="performance" class="config-card">
          <div class="section-heading"><span class="section-icon green"><el-icon><Monitor /></el-icon></span><div><h2>性能监控策略</h2><p>配置推理超时、资源告警与性能退化判定规则</p></div><el-switch v-model="form.performance.enabled" active-text="启用监控" /></div>
          <div class="form-grid three"><el-form-item label="推理超时阈值"><el-input-number v-model="form.performance.latency" :min="10" :max="10000" :step="10"/><span class="unit">ms</span></el-form-item><el-form-item label="CPU 占用告警"><el-input-number v-model="form.performance.cpu" :min="1" :max="100"/><span class="unit">%</span></el-form-item><el-form-item label="内存占用告警"><el-input-number v-model="form.performance.memory" :min="1" :max="100"/><span class="unit">%</span></el-form-item><el-form-item label="连续异常次数"><el-input-number v-model="form.performance.consecutiveFailures" :min="1" :max="100"/><span class="unit">次</span></el-form-item><el-form-item label="性能退化幅度"><el-input-number v-model="form.performance.degradation" :min="1" :max="100"/><span class="unit">%</span></el-form-item><el-form-item label="监控采样周期"><el-select v-model="form.performance.interval"><el-option label="每 1 分钟" :value="1"/><el-option label="每 5 分钟" :value="5"/><el-option label="每 15 分钟" :value="15"/></el-select></el-form-item></div>
        </section>

        <section id="bias" class="config-card">
          <div class="section-heading"><span class="section-icon orange"><el-icon><Aim /></el-icon></span><div><h2>预测偏差判定规则</h2><p>设置偏差分级、异常样本筛选阈值与根因归因权重</p></div><el-switch v-model="form.bias.enabled" active-text="启用判定" /></div>
          <div class="form-grid three"><el-form-item label="一般偏差阈值"><el-input-number v-model="form.bias.normal" :min="1" :max="100"/><span class="unit">%</span></el-form-item><el-form-item label="高偏差阈值"><el-input-number v-model="form.bias.high" :min="1" :max="100"/><span class="unit">%</span></el-form-item><el-form-item label="严重偏差阈值"><el-input-number v-model="form.bias.critical" :min="1" :max="100"/><span class="unit">%</span></el-form-item></div>
          <el-divider content-position="left">根因归因权重</el-divider><div class="compact-fields"><el-form-item v-for="item in attributionFields" :key="item.key" :label="item.label"><el-input-number v-model="form.bias.attribution[item.key]" :min="0" :max="100"/><span class="unit">%</span></el-form-item></div>
        </section>

        <section id="tuning" class="config-card">
          <div class="section-heading"><span class="section-icon purple"><el-icon><MagicStick /></el-icon></span><div><h2>模型自动调优参数范围</h2><p>约束自动搜索边界，防止生成不可控参数组合</p></div><el-switch v-model="form.tuning.enabled" active-text="允许自动调优" /></div>
          <div class="range-table"><div class="range-head"><span>参数</span><span>最小值</span><span>最大值</span><span>搜索步长</span></div><div v-for="item in form.tuning.ranges" :key="item.key" class="range-row"><strong>{{ item.label }}</strong><el-input-number v-model="item.min" :min="item.floor" :max="item.ceiling" :step="item.step"/><el-input-number v-model="item.max" :min="item.floor" :max="item.ceiling" :step="item.step"/><el-input-number v-model="item.searchStep" :min="item.step" :max="item.ceiling" :step="item.step"/></div></div>
          <div class="form-grid three top-space"><el-form-item label="搜索策略"><el-select v-model="form.tuning.strategy"><el-option label="贝叶斯优化" value="bayesian"/><el-option label="网格搜索" value="grid"/><el-option label="随机搜索" value="random"/></el-select></el-form-item><el-form-item label="最大试验次数"><el-input-number v-model="form.tuning.maxTrials" :min="1" :max="500"/></el-form-item><el-form-item label="优化目标"><el-select v-model="form.tuning.objective"><el-option label="MAPE 最小" value="mape"/><el-option label="综合准确率最高" value="accuracy"/><el-option label="MAE 最小" value="mae"/></el-select></el-form-item></div>
        </section>

        <section id="retraining" class="config-card">
          <div class="section-heading"><span class="section-icon red"><el-icon><RefreshRight /></el-icon></span><div><h2>自动化 Retraining 触发策略</h2><p>配置精度、周期与偏差超标三类自动重训条件</p></div><el-switch v-model="form.retraining.enabled" active-text="启用自动重训" /></div>
          <div class="strategy-list"><div><el-switch v-model="form.retraining.accuracyEnabled"/><span><b>精度不达标自动触发</b><small>综合准确率持续低于阈值时启动重训</small></span><el-input-number v-model="form.retraining.accuracyThreshold" :min="50" :max="100"/><em>%</em></div><div><el-switch v-model="form.retraining.scheduleEnabled"/><span><b>定时周期触发</b><small>按固定周期重新训练并验证模型效果</small></span><el-select v-model="form.retraining.schedule"><el-option label="每周" value="weekly"/><el-option label="每月" value="monthly"/><el-option label="每季度" value="quarterly"/></el-select></div><div><el-switch v-model="form.retraining.biasEnabled"/><span><b>偏差超标自动触发</b><small>高偏差样本占比达到设定阈值时启动</small></span><el-input-number v-model="form.retraining.biasThreshold" :min="1" :max="100"/><em>%</em></div></div>
          <el-form-item label="自动触发冷却周期"><el-input-number v-model="form.retraining.cooldownHours" :min="1" :max="720"/><span class="unit">小时，避免短期内重复触发</span></el-form-item>
        </section>

        <section id="retention" class="config-card">
          <div class="section-heading"><span class="section-icon gray"><el-icon><Files /></el-icon></span><div><h2>模型迭代留存规则</h2><p>管理优化版本、迭代日志和新旧版本自动对比策略</p></div></div>
          <div class="form-grid three"><el-form-item label="模型版本留存数量"><el-input-number v-model="form.retention.versionCount" :min="1" :max="200"/><span class="unit">个</span></el-form-item><el-form-item label="迭代日志留存周期"><el-input-number v-model="form.retention.logDays" :min="7" :max="3650"/><span class="unit">天</span></el-form-item><el-form-item label="评估报告留存周期"><el-input-number v-model="form.retention.reportDays" :min="7" :max="3650"/><span class="unit">天</span></el-form-item></div>
          <div class="inline-switch"><div><b>新旧版本自动对比</b><small>每次优化完成后自动生成指标差异与收益报告</small></div><el-switch v-model="form.retention.autoCompare"/></div><div class="inline-switch"><div><b>全量操作日志同步至安全审计</b><small>评估、调优、重训和发布操作统一留痕至模块 6</small></div><el-switch v-model="form.retention.auditSync"/></div>
        </section>
      </main>
    </div>
    <footer class="sticky-footer"><span><el-icon><InfoFilled /></el-icon>规则变更将在下一次任务执行时生效</span><div><el-button @click="router.push('/model-optimization')">取消</el-button><el-button type="primary" :loading="saving" :disabled="weightTotal !== 100" @click="save">保存并生效</el-button></div></footer>
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Aim, ArrowLeft, Bell, Check, Files, InfoFilled, MagicStick, Monitor, RefreshLeft, RefreshRight, TrendCharts } from '@element-plus/icons-vue'
import request from '../utils/request'

const router = useRouter()
const saving = ref(false)
const activeSection = ref('accuracy')
const sections = [
  { id: 'accuracy', title: '准确率评估规则', desc: '合格阈值与指标权重', icon: markRaw(TrendCharts) },
  { id: 'performance', title: '性能监控策略', desc: '告警阈值与退化判定', icon: markRaw(Monitor) },
  { id: 'bias', title: '预测偏差规则', desc: '偏差分级与根因归因', icon: markRaw(Aim) },
  { id: 'tuning', title: '自动调优范围', desc: '参数搜索区间约束', icon: markRaw(MagicStick) },
  { id: 'retraining', title: 'Retraining 策略', desc: '自动触发条件', icon: markRaw(RefreshRight) },
  { id: 'retention', title: '迭代留存规则', desc: '版本、报告与日志', icon: markRaw(Files) }
]
const defaults = {
  accuracy: { enabled: true, passThreshold: 85, excellentMape: 10, passMape: 20, weights: { accuracy: 35, precision: 15, recall: 15, mae: 10, mse: 10, mape: 15 } },
  performance: { enabled: true, latency: 200, cpu: 85, memory: 85, consecutiveFailures: 3, degradation: 15, interval: 5 },
  bias: { enabled: true, normal: 10, high: 20, critical: 35, attribution: { sample: 30, label: 20, fitting: 25, market: 25 } },
  tuning: { enabled: true, strategy: 'bayesian', maxTrials: 50, objective: 'mape', ranges: [
    { key: 'learningRate', label: '学习率', min: 0.001, max: 0.1, searchStep: 0.001, floor: 0.0001, ceiling: 1, step: 0.001 },
    { key: 'epochs', label: '迭代轮数', min: 100, max: 800, searchStep: 50, floor: 10, ceiling: 2000, step: 10 },
    { key: 'batchSize', label: 'Batch Size', min: 16, max: 128, searchStep: 16, floor: 8, ceiling: 512, step: 8 }
  ] },
  retraining: { enabled: true, accuracyEnabled: true, accuracyThreshold: 85, scheduleEnabled: true, schedule: 'monthly', biasEnabled: true, biasThreshold: 20, cooldownHours: 72 },
  retention: { versionCount: 20, logDays: 365, reportDays: 730, autoCompare: true, auditSync: true }
}
const clone = value => JSON.parse(JSON.stringify(value))
const stored = JSON.parse(localStorage.getItem('river-evaluation-config') || 'null')
const form = reactive(Object.assign(clone(defaults), stored || {}))
const weightFields = [{ key: 'accuracy', label: '综合准确率' }, { key: 'precision', label: '精准率' }, { key: 'recall', label: '召回率' }, { key: 'mae', label: 'MAE' }, { key: 'mse', label: 'MSE' }, { key: 'mape', label: 'MAPE' }]
const attributionFields = [{ key: 'sample', label: '数据样本问题' }, { key: 'label', label: '标注质量问题' }, { key: 'fitting', label: '模型拟合问题' }, { key: 'market', label: '市场波动问题' }]
const weightTotal = computed(() => Object.values(form.accuracy.weights).reduce((total, value) => total + Number(value || 0), 0))

function scrollTo(id) { activeSection.value = id; document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }
async function restore() { try { await ElMessageBox.confirm('确认将全部策略恢复为系统默认值？', '恢复默认配置', { type: 'warning' }) } catch (error) { return }; Object.assign(form, clone(defaults)); ElMessage.info('已恢复默认值，保存后生效') }
async function save() {
  if (weightTotal.value !== 100) return ElMessage.warning('评估指标权重合计必须为 100%')
  saving.value = true
  localStorage.setItem('river-evaluation-config', JSON.stringify(form))
  try { await request.put('/v1/system-config/prediction-evaluation', JSON.stringify(form), { headers: { 'Content-Type': 'application/json' } }); ElMessage.success('配置已保存并同步至系统') }
  catch (error) { ElMessage.error(error.message || '配置保存失败，未写入后台') }
  finally { saving.value = false }
}
onMounted(async () => { try { const saved = await request.get('/v1/system-config/prediction-evaluation'); if (saved?.configJson) Object.assign(form, JSON.parse(saved.configJson)) } catch (error) { ElMessage.warning(error.message || '评估配置加载失败，使用默认配置') } })
</script>

<style scoped>
.config-page{padding-top:20px;padding-bottom:88px;color:#1d2129}.page-header,.header-actions,.section-heading,.weight-grid>div>div,.weight-total,.inline-switch,.sticky-footer,.strategy-list>div{display:flex;align-items:center}.page-header{justify-content:space-between;margin-bottom:16px}.eyebrow{font-size:11px;color:#165dff;font-weight:700;letter-spacing:.08em}.page-header h1{font-size:24px;margin:5px 0}.page-header p{font-size:13px;color:#86909c;margin:0}.header-actions{gap:8px}.config-layout{display:grid;grid-template-columns:230px minmax(0,1fr);gap:16px;margin-top:16px}.config-nav{position:sticky;top:76px;align-self:start;border:1px solid #e5e6eb;background:#fff;border-radius:8px;padding:8px}.config-nav button{display:flex;align-items:center;gap:10px;width:100%;border:0;background:transparent;border-radius:6px;padding:11px 10px;text-align:left;color:#4e5969;cursor:pointer}.config-nav button:hover,.config-nav button.active{background:#e8f3ff;color:#165dff}.config-nav button>.el-icon{font-size:17px}.config-nav span,.config-nav small{display:block}.config-nav span{font-size:13px}.config-nav small{font-size:10px;color:#86909c;margin-top:4px}.config-content{display:grid;gap:16px}.config-card{scroll-margin-top:74px;background:#fff;border:1px solid #e5e6eb;border-radius:8px;padding:20px}.section-heading{gap:12px;margin-bottom:20px}.section-heading>div{flex:1}.section-heading h2{font-size:16px;margin:0 0 5px}.section-heading p{font-size:12px;color:#86909c;margin:0}.section-icon{display:grid;place-items:center;width:38px;height:38px;border-radius:6px;font-size:19px}.section-icon.blue{background:#e8f3ff;color:#165dff}.section-icon.green{background:#e8f8ec;color:#00a825}.section-icon.orange{background:#fff7e8;color:#ff7d00}.section-icon.purple{background:#f5edff;color:#722ed1}.section-icon.red{background:#ffece8;color:#f53f3f}.section-icon.gray{background:#f2f3f5;color:#4e5969}.form-grid{display:grid;gap:0 22px}.form-grid.three{grid-template-columns:repeat(3,1fr)}.config-card :deep(.el-form-item){display:block;margin-bottom:16px}.config-card :deep(.el-form-item__label){font-size:12px;color:#4e5969;margin-bottom:7px}.config-card :deep(.el-input-number),.config-card :deep(.el-select){width:calc(100% - 36px)}.unit{font-size:11px;color:#86909c;margin-left:7px}.weight-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px 28px}.weight-grid>div>div{justify-content:space-between;font-size:12px}.weight-grid b{color:#165dff}.weight-total{justify-content:flex-end;gap:12px;border-top:1px solid #f2f3f5;margin-top:10px;padding-top:12px;font-size:12px}.weight-total small{color:#00a825}.weight-total.error b,.weight-total.error small{color:#f53f3f}.compact-fields{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}.range-table{border:1px solid #e5e6eb;border-radius:6px;overflow:hidden}.range-head,.range-row{display:grid;grid-template-columns:1.2fr repeat(3,1fr);align-items:center;gap:16px;padding:10px 14px}.range-head{background:#f7f8fa;font-size:11px;color:#4e5969}.range-row{border-top:1px solid #f2f3f5}.range-row strong{font-size:12px}.range-row :deep(.el-input-number){width:100%}.top-space{margin-top:18px}.strategy-list{border:1px solid #e5e6eb;border-radius:6px;margin-bottom:18px}.strategy-list>div{min-height:68px;gap:14px;padding:10px 14px;border-bottom:1px solid #f2f3f5}.strategy-list>div:last-child{border-bottom:0}.strategy-list>div>span{flex:1}.strategy-list b,.strategy-list small{display:block}.strategy-list b{font-size:12px}.strategy-list small{font-size:10px;color:#86909c;margin-top:5px}.strategy-list :deep(.el-input-number),.strategy-list :deep(.el-select){width:160px}.strategy-list em{font-size:11px;color:#86909c;font-style:normal}.inline-switch{justify-content:space-between;border-top:1px solid #f2f3f5;padding:14px 0}.inline-switch b,.inline-switch small{display:block}.inline-switch b{font-size:12px}.inline-switch small{font-size:10px;color:#86909c;margin-top:4px}.sticky-footer{position:fixed;z-index:10;left:240px;right:0;bottom:0;justify-content:space-between;height:64px;padding:0 28px;background:rgba(255,255,255,.96);border-top:1px solid #e5e6eb;box-shadow:0 -2px 8px rgba(0,0,0,.04)}.sticky-footer>span{display:flex;align-items:center;gap:7px;color:#86909c;font-size:11px}.sticky-footer>div{display:flex;gap:8px}
@media(max-width:1000px){.config-layout{grid-template-columns:1fr}.config-nav{position:static;display:grid;grid-template-columns:repeat(3,1fr)}.form-grid.three,.weight-grid{grid-template-columns:repeat(2,1fr)}.sticky-footer{left:64px}.compact-fields{grid-template-columns:repeat(2,1fr)}}
@media(max-width:680px){.config-page{padding:16px 16px 88px}.page-header{align-items:flex-start;gap:14px}.page-header p,.header-actions .el-button:nth-child(2){display:none}.header-actions{flex-wrap:wrap;justify-content:flex-end}.config-nav{grid-template-columns:1fr 1fr}.form-grid.three,.weight-grid,.compact-fields{grid-template-columns:1fr}.range-head,.range-row{grid-template-columns:1fr 1fr}.range-head span:nth-child(3),.range-head span:nth-child(4){display:none}.sticky-footer{left:0;padding:0 16px}.sticky-footer>span{display:none}}
</style>
