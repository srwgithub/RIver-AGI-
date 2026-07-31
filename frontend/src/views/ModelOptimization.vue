<template>
  <div class="model-optimization">
    <section class="page-header">
      <div class="header-copy">
        <div class="eyebrow">MODEL OPTIMIZATION CENTER</div>
        <h1>预测评估中心 & 模型优化系统</h1>
        <p class="subtitle">准确率评估、性能监控、偏差分析、自动调优、Retraining 自动化</p>
      </div>
      <div class="header-actions">
        <el-tag effect="dark" type="success">在线运行</el-tag>
        <el-tag effect="dark" type="info">6 个功能面板</el-tag>
        <el-button type="primary" plain @click="activeTab = 'tracking'">查看效果追踪</el-button>
      </div>
    </section>

    <el-row :gutter="16" class="kpi-row">
      <el-col :span="4" v-for="(kpi, idx) in kpiCards" :key="idx">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-content">
            <div class="kpi-value" :style="{ color: kpi.color }">
              {{ kpi.value }}
              <span v-if="kpi.trend" class="kpi-trend" :class="kpi.trendClass">
                {{ kpi.trendIcon }} {{ kpi.trend }}
              </span>
            </div>
            <div class="kpi-label">{{ kpi.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab" class="main-tabs" tab-position="left" @tab-change="onTabChange">
      <el-tab-pane label="评估总览" name="overview">
        <template #label>
          <span class="tab-label"><el-icon><PieChart /></el-icon><span>评估总览</span></span>
        </template>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header><span>模型健康度</span></template>
              <div ref="healthGaugeRef" class="chart-container gauge-chart"></div>
            </el-card>
          </el-col>
          <el-col :span="16">
            <el-card shadow="hover">
              <template #header><span>各模型准确率排名</span></template>
              <div ref="accuracyRankRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="24">
            <el-card shadow="hover">
              <template #header><span>30天准确率趋势</span></template>
              <div ref="accuracyTrendRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="hover" style="margin-top: 16px;">
          <template #header><span>待优化问题模型</span></template>
          <el-table :data="problemModels" stripe size="small">
            <el-table-column prop="modelName" label="模型名" width="180" />
            <el-table-column prop="algorithm" label="算法" width="150" />
            <el-table-column prop="mape" label="MAPE" width="120">
              <template #default="{ row }">
                <el-tag :type="row.mape > 15 ? 'danger' : row.mape > 10 ? 'warning' : 'success'">{{ row.mape }}%</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="problem" label="问题" show-overflow-tooltip />
            <el-table-column prop="suggestion" label="建议" show-overflow-tooltip />
            <el-table-column label="操作" width="150">
              <template #default>
                <el-button type="primary" size="small" link>立即优化</el-button>
                <el-button size="small" link>查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="准确率评估" name="accuracy">
        <template #label>
          <span class="tab-label"><el-icon><TrendCharts /></el-icon><span>准确率评估</span></span>
        </template>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-card shadow="hover" class="metric-explain-card">
              <template #header><span>评估指标说明</span></template>
              <el-row :gutter="16">
                <el-col :span="4" v-for="m in metricExplanations" :key="m.key" class="metric-explain-item">
                  <div class="metric-name">{{ m.name }}</div>
                  <div class="metric-desc">{{ m.desc }}</div>
                  <div class="metric-formula">{{ m.formula }}</div>
                </el-col>
              </el-row>
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="hover" style="margin-top: 16px;">
          <template #header><span>模型评估对比</span></template>
          <el-table :data="modelEvaluations" stripe size="small">
            <el-table-column prop="modelName" label="模型名" width="150" fixed />
            <el-table-column prop="mae" label="MAE" width="100" />
            <el-table-column prop="rmse" label="RMSE" width="100" />
            <el-table-column prop="mape" label="MAPE" width="100">
              <template #default="{ row }">{{ row.mape }}%</template>
            </el-table-column>
            <el-table-column prop="r2" label="R²" width="100" />
            <el-table-column prop="bias" label="Bias" width="100" />
            <el-table-column prop="accuracy" label="准确率" width="120">
              <template #default="{ row }">
                <el-progress :percentage="row.accuracy" :color="accuracyColor(row.accuracy)" :stroke-width="12" />
              </template>
            </el-table-column>
            <el-table-column prop="rating" label="评级" width="100">
              <template #default="{ row }">
                <el-tag :type="ratingType(row.rating)">{{ row.rating }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header><span>预测 vs 实际值散点图</span></template>
              <div ref="scatterRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header><span>误差分布直方图</span></template>
              <div ref="errorDistRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="24">
            <el-card shadow="hover">
              <template #header><span>分类混淆矩阵热力图</span></template>
              <div ref="confusionRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="性能监控" name="performance">
        <template #label>
          <span class="tab-label"><el-icon><Monitor /></el-icon><span>性能监控</span></span>
        </template>
        <el-row :gutter="16">
          <el-col :span="24">
            <div class="model-status-grid">
              <el-card v-for="m in modelStatusCards" :key="m.id" shadow="hover" class="model-status-card">
                <div class="status-header">
                  <span class="status-dot" :class="m.status"></span>
                  <span class="model-name">{{ m.name }}</span>
                  <el-tag size="small" :type="statusTagType(m.status)">{{ statusText(m.status) }}</el-tag>
                </div>
                <el-row :gutter="8" class="status-metrics">
                  <el-col :span="12"><div class="mini-metric"><span class="val">{{ m.mape }}%</span><span class="lab">MAPE</span></div></el-col>
                  <el-col :span="12"><div class="mini-metric"><span class="val">{{ m.qps }}</span><span class="lab">QPS</span></div></el-col>
                  <el-col :span="12"><div class="mini-metric"><span class="val">{{ m.latency }}ms</span><span class="lab">延迟</span></div></el-col>
                  <el-col :span="12"><div class="mini-metric"><span class="val">{{ m.psi }}</span><span class="lab">PSI</span></div></el-col>
                </el-row>
              </el-card>
            </div>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header><span>性能衰减趋势 (MAPE)</span></template>
              <div ref="decayTrendRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header><span>数据漂移 PSI 指标</span></template>
              <div ref="psiChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="24">
            <el-card shadow="hover">
              <template #header><span>延迟 & QPS 监控</span></template>
              <div ref="latencyQpsRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="hover" style="margin-top: 16px;">
          <template #header>
            <div class="card-header-flex">
              <span>告警阈值配置</span>
              <el-switch v-model="alertConfigEnabled" active-text="启用告警" />
            </div>
          </template>
          <el-form :model="alertForm" label-width="140px" inline>
            <el-form-item label="MAPE告警阈值">
              <el-input-number v-model="alertForm.mapeThreshold" :min="1" :max="50" :step="0.5" />
              <span class="form-unit">%</span>
            </el-form-item>
            <el-form-item label="延迟告警阈值">
              <el-input-number v-model="alertForm.latencyThreshold" :min="50" :max="5000" :step="50" />
              <span class="form-unit">ms</span>
            </el-form-item>
            <el-form-item label="PSI告警阈值">
              <el-input-number v-model="alertForm.psiThreshold" :min="0.1" :max="1" :step="0.05" />
            </el-form-item>
            <el-form-item label="告警方式">
              <el-checkbox-group v-model="alertForm.channels">
                <el-checkbox label="email">邮件</el-checkbox>
                <el-checkbox label="sms">短信</el-checkbox>
                <el-checkbox label="webhook">Webhook</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveAlertConfig">保存配置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="预测偏差分析" name="bias">
        <template #label>
          <span class="tab-label"><el-icon><DataAnalysis /></el-icon><span>预测偏差分析</span></span>
        </template>
        <el-row :gutter="16">
          <el-col :span="16">
            <el-card shadow="hover">
              <template #header><span>偏差趋势（预测值 - 实际值）</span></template>
              <div ref="biasTrendRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header><span>偏差归因</span></template>
              <div ref="biasPieRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="24">
            <el-card shadow="hover">
              <template #header><span>偏差热力图（时间 × 维度）</span></template>
              <div ref="biasHeatmapRef" class="chart-container heatmap-chart"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header><span>大偏差样本列表</span></template>
              <el-table :data="largeBiasSamples" stripe size="small" max-height="350">
                <el-table-column prop="timestamp" label="时间" width="160" />
                <el-table-column prop="actual" label="实际值" width="100" />
                <el-table-column prop="predicted" label="预测值" width="100" />
                <el-table-column prop="bias" label="偏差" width="100">
                  <template #default="{ row }">
                    <span :style="{ color: Math.abs(row.biasPct) > 20 ? '#ef4444' : Math.abs(row.biasPct) > 10 ? '#f59e0b' : '#0f766e' }">
                      {{ row.biasPct > 0 ? '+' : '' }}{{ row.biasPct }}%
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="dimension" label="维度" show-overflow-tooltip />
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header><span>根因分析面板</span></template>
              <div class="root-cause-panel">
                <el-alert title="检测到显著偏差" type="warning" :closable="false" show-icon style="margin-bottom: 12px;">
                  <template #default>
                    <p>最近24小时内，周一早高峰时段需求预测偏差超过25%</p>
                  </template>
                </el-alert>
                <div class="cause-item" v-for="(cause, idx) in rootCauses" :key="idx">
                  <div class="cause-rank">{{ idx + 1 }}</div>
                  <div class="cause-content">
                    <div class="cause-title">{{ cause.title }}</div>
                    <div class="cause-desc">{{ cause.desc }}</div>
                    <el-progress :percentage="cause.contribution" :stroke-width="8" color="#0f766e" style="margin-top: 6px;" />
                    <div class="cause-impact">贡献度: {{ cause.contribution }}%</div>
                  </div>
                </div>
                <el-button type="primary" style="width: 100%; margin-top: 12px;">生成偏差分析报告</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="模型自动调优" name="tuning">
        <template #label>
          <span class="tab-label"><el-icon><Setting /></el-icon><span>模型自动调优</span></span>
        </template>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header><span>超参数配置</span></template>
              <el-form :model="tuningForm" label-width="120px" size="small">
                <el-form-item label="选择算法">
                  <el-select v-model="tuningForm.algorithm" placeholder="选择算法" style="width: 100%;">
                    <el-option v-for="a in algoOptions" :key="a.value" :label="a.label" :value="a.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="优化目标">
                  <el-radio-group v-model="tuningForm.objective">
                    <el-radio label="mape">MAPE最小</el-radio>
                    <el-radio label="rmse">RMSE最小</el-radio>
                    <el-radio label="r2">R²最大</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="搜索策略">
                  <el-radio-group v-model="tuningForm.strategy">
                    <el-radio label="grid">网格搜索</el-radio>
                    <el-radio label="random">随机搜索</el-radio>
                    <el-radio label="bayes">贝叶斯优化</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="最大迭代次数">
                  <el-input-number v-model="tuningForm.maxIter" :min="10" :max="500" :step="10" style="width: 100%;" />
                </el-form-item>
                <el-form-item label="交叉验证折数">
                  <el-input-number v-model="tuningForm.cvFolds" :min="2" :max="10" style="width: 100%;" />
                </el-form-item>
                <el-divider content-position="left">参数范围</el-divider>
                <div v-for="(p, idx) in tuningForm.params" :key="idx" class="param-range-item">
                  <span class="param-name">{{ p.name }}</span>
                  <el-input-number v-model="p.min" size="small" placeholder="最小" controls-position="right" style="width: 80px;" />
                  <span>~</span>
                  <el-input-number v-model="p.max" size="small" placeholder="最大" controls-position="right" style="width: 80px;" />
                </div>
                <el-button type="primary" style="width: 100%; margin-top: 12px;" :loading="tuningLoading" @click="startTuning">
                  {{ tuningLoading ? '调优进行中...' : '启动自动调优' }}
                </el-button>
              </el-form>
            </el-card>
          </el-col>
          <el-col :span="16">
            <el-card shadow="hover">
              <template #header><span>调优任务列表</span></template>
              <el-table :data="tuningTasks" stripe size="small">
                <el-table-column prop="taskName" label="任务名" width="150" />
                <el-table-column prop="algorithm" label="算法" width="120" />
                <el-table-column prop="bestScore" label="当前最佳" width="100" />
                <el-table-column prop="iterations" label="迭代次数" width="100">
                  <template #default="{ row }">{{ row.currentIter }}/{{ row.iterations }}</template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="taskStatusType(row.status)" size="small">{{ taskStatusText(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="eta" label="ETA" width="100" />
                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button v-if="row.status === 'running'" type="danger" size="small" link @click="stopTuning(row)">停止</el-button>
                    <el-button v-else-if="row.status === 'completed'" type="primary" size="small" link @click="applyTuningResult(row)">应用</el-button>
                    <el-button size="small" link>详情</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header><span>参数重要性</span></template>
              <div ref="paramImportanceRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header><span>调优过程散点图 (Pareto前沿)</span></template>
              <div ref="tuningScatterRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="hover" style="margin-top: 16px;">
          <template #header><span>调优前 vs 调优后对比</span></template>
          <el-table :data="tuningComparison" stripe size="small">
            <el-table-column prop="metric" label="指标" width="150" />
            <el-table-column prop="before" label="调优前" width="150" />
            <el-table-column prop="after" label="调优后" width="150" />
            <el-table-column label="提升" width="150">
              <template #default="{ row }">
                <el-tag :type="row.improvement > 0 ? 'success' : row.improvement < 0 ? 'danger' : 'info'" size="small">
                  {{ row.improvement > 0 ? '+' : '' }}{{ row.improvement }}%
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="说明" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="Retraining自动化" name="retraining">
        <template #label>
          <span class="tab-label"><el-icon><Document /></el-icon><span>Retraining 自动化</span></span>
        </template>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header>
                <div class="card-header-flex">
                  <span>触发条件配置</span>
                  <el-switch v-model="retrainConfig.enabled" active-text="启用自动重训练" />
                </div>
              </template>
              <el-form :model="retrainConfig" label-width="130px" size="small">
                <el-form-item label="性能阈值触发">
                  <el-switch v-model="retrainConfig.performanceTrigger" />
                  <span class="form-tip">MAPE超过阈值触发</span>
                </el-form-item>
                <el-form-item v-if="retrainConfig.performanceTrigger" label="MAPE阈值">
                  <el-input-number v-model="retrainConfig.mapeThreshold" :min="5" :max="50" :step="1" />
                  <span class="form-unit">%</span>
                </el-form-item>
                <el-form-item label="数据量阈值触发">
                  <el-switch v-model="retrainConfig.dataTrigger" />
                  <span class="form-tip">新增数据达到阈值触发</span>
                </el-form-item>
                <el-form-item v-if="retrainConfig.dataTrigger" label="新增数据量">
                  <el-input-number v-model="retrainConfig.dataThreshold" :min="100" :max="100000" :step="100" />
                  <span class="form-unit">条</span>
                </el-form-item>
                <el-form-item label="定时触发">
                  <el-switch v-model="retrainConfig.scheduleTrigger" />
                </el-form-item>
                <el-form-item v-if="retrainConfig.scheduleTrigger" label="Cron表达式">
                  <el-select v-model="retrainConfig.cronExpr" placeholder="选择周期" style="width: 100%;">
                    <el-option label="每天凌晨2点" value="0 2 * * *" />
                    <el-option label="每周一凌晨2点" value="0 2 * * 1" />
                    <el-option label="每月1号凌晨2点" value="0 2 1 * *" />
                  </el-select>
                </el-form-item>
                <el-form-item label="手动触发">
                  <el-button type="primary" size="small" @click="triggerManualRetrain">立即触发重训练</el-button>
                </el-form-item>
                <el-form-item label="自动部署">
                  <el-switch v-model="retrainConfig.autoDeploy" active-text="训练完成自动部署" />
                </el-form-item>
                <el-button type="primary" style="width: 100%;" @click="saveRetrainConfig">保存配置</el-button>
              </el-form>
            </el-card>
          </el-col>
          <el-col :span="16">
            <el-card shadow="hover">
              <template #header><span>重训练流水线</span></template>
              <div class="pipeline-container">
                <el-steps :active="pipelineActiveStep" finish-status="success" align-center>
                  <el-step v-for="(step, idx) in pipelineSteps" :key="idx" :title="step.title">
                    <template #icon>
                      <span class="step-indicator" :class="step.status">{{ step.status === 'running' ? '⟳' : idx + 1 }}</span>
                    </template>
                    <template #description>
                      <div class="step-desc">
                        <el-tag size="small" :type="pipelineStepType(step.status)">{{ pipelineStepText(step.status) }}</el-tag>
                        <div v-if="step.duration" class="step-duration">耗时: {{ step.duration }}</div>
                      </div>
                    </template>
                  </el-step>
                </el-steps>
                <div class="pipeline-log" v-if="pipelineLogs.length">
                  <div v-for="(log, idx) in pipelineLogs" :key="idx" class="log-line">
                    <span class="log-time">{{ log.time }}</span>
                    <span class="log-level" :class="log.level">[{{ log.level.toUpperCase() }}]</span>
                    <span class="log-msg">{{ log.msg }}</span>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="24">
            <el-card shadow="hover">
              <template #header><span>Champion-Challenger 模型对比</span></template>
              <el-row :gutter="24">
                <el-col :span="12">
                  <div class="cc-card champion">
                    <div class="cc-header">
                      <el-tag type="success" size="large">Champion (当前生产)</el-tag>
                      <span class="cc-model-name">{{ championModel.name }}</span>
                    </div>
                    <el-descriptions :column="2" border size="small" style="margin-top: 12px;">
                      <el-descriptions-item label="MAPE">{{ championModel.mape }}%</el-descriptions-item>
                      <el-descriptions-item label="RMSE">{{ championModel.rmse }}</el-descriptions-item>
                      <el-descriptions-item label="R²">{{ championModel.r2 }}</el-descriptions-item>
                      <el-descriptions-item label="准确率">{{ championModel.accuracy }}%</el-descriptions-item>
                      <el-descriptions-item label="训练时间">{{ championModel.trainTime }}</el-descriptions-item>
                      <el-descriptions-item label="部署时间">{{ championModel.deployTime }}</el-descriptions-item>
                    </el-descriptions>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="cc-card challenger">
                    <div class="cc-header">
                      <el-tag type="warning" size="large">Challenger (新训练)</el-tag>
                      <span class="cc-model-name">{{ challengerModel.name }}</span>
                      <el-button v-if="retrainConfig.autoDeploy" type="primary" size="small" @click="deployChallenger">部署此模型</el-button>
                    </div>
                    <el-descriptions :column="2" border size="small" style="margin-top: 12px;">
                      <el-descriptions-item label="MAPE">
                        <span :style="{ color: challengerModel.mape < championModel.mape ? '#10b981' : '#ef4444' }">{{ challengerModel.mape }}%</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="RMSE">{{ challengerModel.rmse }}</el-descriptions-item>
                      <el-descriptions-item label="R²">{{ challengerModel.r2 }}</el-descriptions-item>
                      <el-descriptions-item label="准确率">
                        <span :style="{ color: challengerModel.accuracy > championModel.accuracy ? '#10b981' : '#ef4444' }">{{ challengerModel.accuracy }}%</span>
                      </el-descriptions-item>
                      <el-descriptions-item label="训练时间">{{ challengerModel.trainTime }}</el-descriptions-item>
                      <el-descriptions-item label="状态">
                        <el-tag size="small" type="info">待审批</el-tag>
                      </el-descriptions-item>
                    </el-descriptions>
                  </div>
                </el-col>
              </el-row>
            </el-card>
          </el-col>
        </el-row>
        <el-card shadow="hover" style="margin-top: 16px;">
          <template #header><span>重训练历史</span></template>
          <el-table :data="retrainHistory" stripe size="small">
            <el-table-column prop="version" label="版本" width="100" />
            <el-table-column prop="triggerType" label="触发方式" width="120" />
            <el-table-column prop="algorithm" label="算法" width="130" />
            <el-table-column prop="mape" label="MAPE" width="100" />
            <el-table-column prop="accuracy" label="准确率" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="retrainStatusType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="trainDuration" label="训练耗时" width="100" />
            <el-table-column prop="createdAt" label="时间" width="170" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" link>回滚</el-button>
                <el-button size="small" link>报告</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="效果追踪" name="tracking">
        <template #label>
          <span class="tab-label"><el-icon><EditPen /></el-icon><span>效果追踪</span></span>
        </template>
        <el-row :gutter="16">
          <el-col :span="16">
            <el-card shadow="hover">
              <template #header><span>模型生命周期时间线</span></template>
              <div class="timeline-container">
                <el-timeline>
                  <el-timeline-item v-for="(item, idx) in lifecycleTimeline" :key="idx" :timestamp="item.time" :type="item.type" :color="item.color" placement="top">
                    <el-card shadow="never" class="timeline-card">
                      <h4>{{ item.title }}</h4>
                      <p>{{ item.desc }}</p>
                      <div v-if="item.metrics" class="timeline-metrics">
                        <el-tag v-for="(m, mi) in item.metrics" :key="mi" size="small" style="margin-right: 6px;">{{ m }}</el-tag>
                      </div>
                    </el-card>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <template #header><span>业务价值统计</span></template>
              <el-row :gutter="12">
                <el-col :span="24" v-for="(v, idx) in businessValues" :key="idx" class="value-stat-item">
                  <el-statistic :title="v.title" :value="v.value" :suffix="v.suffix">
                    <template #prefix v-if="v.prefix">{{ v.prefix }}</template>
                  </el-statistic>
                  <div class="value-trend" :class="v.trendClass">
                    {{ v.trendIcon }} 较上期 {{ v.trend }}%
                  </div>
                </el-col>
              </el-row>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="24">
            <el-card shadow="hover">
              <template #header><span>版本MAPE变化趋势</span></template>
              <div ref="versionMapeRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" style="margin-top: 16px;">
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header>
                <div class="card-header-flex">
                  <span>月度效果报告</span>
                  <el-button type="primary" size="small" @click="generateReport('monthly')">生成报告</el-button>
                </div>
              </template>
              <div class="report-preview">
                <div class="report-item" v-for="(r, idx) in monthlyReports" :key="idx">
                  <div class="report-month">{{ r.month }}</div>
                  <div class="report-metrics">
                    <span>MAPE: <b :style="{ color: r.mape < 10 ? '#10b981' : '#f59e0b' }">{{ r.mape }}%</b></span>
                    <span>准确率: <b>{{ r.accuracy }}%</b></span>
                  </div>
                  <el-button size="small" link>下载</el-button>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="hover">
              <template #header>
                <div class="card-header-flex">
                  <span>季度效果报告</span>
                  <el-button type="primary" size="small" @click="generateReport('quarterly')">生成报告</el-button>
                </div>
              </template>
              <div class="report-preview">
                <div class="report-item" v-for="(r, idx) in quarterlyReports" :key="idx">
                  <div class="report-month">{{ r.quarter }}</div>
                  <div class="report-metrics">
                    <span>平均MAPE: <b>{{ r.avgMape }}%</b></span>
                    <span>业务提升: <b style="color: #10b981;">+{{ r.lift }}%</b></span>
                  </div>
                  <el-button size="small" link>下载</el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="tuningResultVisible" title="调优结果详情" width="600px">
      <div v-if="selectedTuningResult">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务名">{{ selectedTuningResult.taskName }}</el-descriptions-item>
          <el-descriptions-item label="算法">{{ selectedTuningResult.algorithm }}</el-descriptions-item>
          <el-descriptions-item label="最佳MAPE">{{ selectedTuningResult.bestScore }}%</el-descriptions-item>
          <el-descriptions-item label="迭代次数">{{ selectedTuningResult.iterations }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top: 16px;">最佳参数</h4>
        <el-table :data="selectedTuningResult.bestParams" size="small" stripe>
          <el-table-column prop="name" label="参数名" />
          <el-table-column prop="value" label="参数值" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="tuningResultVisible = false">关闭</el-button>
        <el-button type="primary" @click="applySelectedTuning">应用此结果</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Document, EditPen, Monitor, PieChart, Setting, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import request from '../utils/request'

const brandColor = '#0f766e'
const route = useRoute()
const validTabs = ['overview', 'tuning', 'retraining', 'tracking']
const activeTab = ref(validTabs.includes(route.query.tab) ? route.query.tab : 'overview')
const tuningLoading = ref(false)
const alertConfigEnabled = ref(true)
const tuningResultVisible = ref(false)
const selectedTuningResult = ref(null)

const chartRefs = {}
const chartInstances = {}

const healthGaugeRef = ref(null)
const accuracyRankRef = ref(null)
const accuracyTrendRef = ref(null)
const scatterRef = ref(null)
const errorDistRef = ref(null)
const confusionRef = ref(null)
const decayTrendRef = ref(null)
const psiChartRef = ref(null)
const latencyQpsRef = ref(null)
const biasTrendRef = ref(null)
const biasPieRef = ref(null)
const biasHeatmapRef = ref(null)
const paramImportanceRef = ref(null)
const tuningScatterRef = ref(null)
const versionMapeRef = ref(null)

const kpiCards = ref([
  { label: '模型平均准确率', value: '92.3%', trend: '1.2', trendClass: 'up', trendIcon: '↑', color: brandColor },
  { label: '本周预测偏差率', value: '7.8%', trend: '0.5', trendClass: 'down', trendIcon: '↓', color: '#0f766e' },
  { label: '待优化模型数', value: '3', trend: null, color: '#f59e0b' },
  { label: '本月自动重训练次数', value: '28', trend: '12', trendClass: 'up', trendIcon: '↑', color: brandColor },
  { label: '性能监控告警数', value: '2', trend: '3', trendClass: 'down', trendIcon: '↓', color: '#ef4444' },
  { label: '模型迭代提升幅度', value: '+4.2%', trend: '0.8', trendClass: 'up', trendIcon: '↑', color: '#10b981' }
])

const metricExplanations = [
  { key: 'mae', name: 'MAE', desc: '平均绝对误差', formula: 'Σ|y-ŷ|/n' },
  { key: 'rmse', name: 'RMSE', desc: '均方根误差', formula: '√(Σ(y-ŷ)²/n)' },
  { key: 'mape', name: 'MAPE', desc: '平均绝对百分比误差', formula: 'Σ|(y-ŷ)/y|/n×100%' },
  { key: 'r2', name: 'R²', desc: '决定系数', formula: '1 - SSres/SStot' },
  { key: 'bias', name: 'Bias', desc: '平均偏差', formula: 'Σ(y-ŷ)/n' }
]

const problemModels = ref([])

const modelEvaluations = ref([])

const modelStatusCards = ref([])

const alertForm = reactive({
  mapeThreshold: 15,
  latencyThreshold: 500,
  psiThreshold: 0.25,
  channels: ['email']
})

const largeBiasSamples = ref([
  { timestamp: '2026-07-29 08:00', actual: 1250, predicted: 1680, bias: 430, biasPct: 34.4, dimension: '华东-早高峰' },
  { timestamp: '2026-07-29 09:00', actual: 1580, predicted: 1120, bias: -460, biasPct: -29.1, dimension: '华北-促销日' },
  { timestamp: '2026-07-28 12:00', actual: 2100, predicted: 2680, bias: 580, biasPct: 27.6, dimension: '华南-午高峰' },
  { timestamp: '2026-07-28 18:00', actual: 3200, predicted: 2450, bias: -750, biasPct: -23.4, dimension: '全国-晚高峰' },
  { timestamp: '2026-07-27 10:00', actual: 980, predicted: 1240, bias: 260, biasPct: 26.5, dimension: '西南-新店开业' }
])

const rootCauses = ref([
  { title: '节假日效应未充分建模', desc: '暑期促销活动期间，需求模式与日常差异较大，现有模型未包含促销特征', contribution: 38 },
  { title: '数据漂移 (PSI=0.35)', desc: '近两周用户消费习惯发生变化，训练数据分布已不能代表当前情况', contribution: 27 },
  { title: '外部因素影响', desc: '竞品促销活动、天气异常等外部因素未被纳入模型特征', contribution: 20 },
  { title: '特征滞后', desc: '部分实时特征更新延迟，导致预测时使用的信息不完整', contribution: 15 }
])

const algoOptions = [
  { value: 'xgboost', label: 'XGBoost' },
  { value: 'lightgbm', label: 'LightGBM' },
  { value: 'rf', label: '随机森林' },
  { value: 'lstm', label: 'LSTM' },
  { value: 'prophet', label: 'Prophet' },
  { value: 'arima', label: 'ARIMA' }
]

const tuningForm = reactive({
  algorithm: 'xgboost',
  objective: 'mape',
  strategy: 'bayes',
  maxIter: 100,
  cvFolds: 5,
  params: [
    { name: 'learning_rate', min: 0.01, max: 0.3 },
    { name: 'max_depth', min: 3, max: 15 },
    { name: 'n_estimators', min: 50, max: 500 },
    { name: 'subsample', min: 0.5, max: 1.0 }
  ]
})

const tuningTasks = ref([
  { taskName: '销售预测调优#12', algorithm: 'XGBoost', bestScore: '6.2%', currentIter: 100, iterations: 100, status: 'completed', eta: '-' },
  { taskName: '库存模型调优#08', algorithm: 'LightGBM', bestScore: '4.8%', currentIter: 65, iterations: 80, status: 'running', eta: '12分钟' },
  { taskName: '客流预测调优#05', algorithm: 'LSTM', bestScore: '9.1%', currentIter: 0, iterations: 50, status: 'pending', eta: '排队中' },
  { taskName: '补货模型调优#03', algorithm: 'RF', bestScore: '5.5%', currentIter: 50, iterations: 50, status: 'failed', eta: '-' }
])

const tuningComparison = ref([
  { metric: 'MAPE', before: '8.5%', after: '6.2%', improvement: 27.1, note: '显著提升' },
  { metric: 'RMSE', before: '24.56', after: '18.32', improvement: 25.4, note: '误差降低' },
  { metric: 'R²', before: '0.892', after: '0.942', improvement: 5.6, note: '拟合度提升' },
  { metric: '准确率', before: '91.5%', after: '93.8%', improvement: 2.5, note: '稳定提升' },
  { metric: '推理延迟', before: '28ms', after: '23ms', improvement: 17.9, note: '性能优化' }
])

const retrainConfig = reactive({
  enabled: true,
  performanceTrigger: true,
  mapeThreshold: 15,
  dataTrigger: true,
  dataThreshold: 5000,
  scheduleTrigger: true,
  cronExpr: '0 2 * * 1',
  autoDeploy: false
})

const pipelineActiveStep = ref(3)
const pipelineSteps = ref([
  { title: '数据源', status: 'completed', duration: '12s' },
  { title: '特征工程', status: 'completed', duration: '45s' },
  { title: '模型训练', status: 'completed', duration: '8m32s' },
  { title: '模型评估', status: 'running', duration: '-' },
  { title: '审批', status: 'pending', duration: '-' },
  { title: '部署', status: 'pending', duration: '-' }
])

const pipelineLogs = ref([
  { time: '14:32:18', level: 'info', msg: '重训练流水线启动，触发方式: 定时调度' },
  { time: '14:32:30', level: 'info', msg: '数据源连接成功，加载最近30天数据: 共128,560条记录' },
  { time: '14:33:15', level: 'info', msg: '特征工程完成，生成特征42个，其中数值特征35个，类别特征7个' },
  { time: '14:41:47', level: 'info', msg: 'XGBoost模型训练完成，迭代200轮，最佳迭代轮次: 158' },
  { time: '14:42:00', level: 'info', msg: '开始模型评估，使用5折交叉验证...' }
])

const championModel = ref({
  name: 'XGBoost v3.0 (生产)',
  mape: 6.8,
  rmse: 18.32,
  r2: 0.942,
  accuracy: 93.2,
  trainTime: '2026-07-22 02:15',
  deployTime: '2026-07-22 03:00'
})

const challengerModel = ref({
  name: 'XGBoost v3.1 (待部署)',
  mape: 6.1,
  rmse: 16.78,
  r2: 0.951,
  accuracy: 93.9,
  trainTime: '2026-07-29 02:08',
  deployTime: '-'
})

const retrainHistory = ref([
  { version: 'v3.1', triggerType: '定时调度', algorithm: 'XGBoost', mape: 6.1, accuracy: 93.9, status: '待审批', trainDuration: '8m32s', createdAt: '2026-07-29 02:08' },
  { version: 'v3.0', triggerType: '性能阈值', algorithm: 'XGBoost', mape: 6.8, accuracy: 93.2, status: '已部署', trainDuration: '7m45s', createdAt: '2026-07-22 03:00' },
  { version: 'v2.9', triggerType: '数据量', algorithm: 'LightGBM', mape: 7.5, accuracy: 92.5, status: '已回滚', trainDuration: '6m18s', createdAt: '2026-07-15 11:30' },
  { version: 'v2.8', triggerType: '手动', algorithm: 'XGBoost', mape: 7.2, accuracy: 92.8, status: '已归档', trainDuration: '9m12s', createdAt: '2026-07-08 15:45' },
  { version: 'v2.7', triggerType: '定时调度', algorithm: 'RF', mape: 8.1, accuracy: 91.9, status: '已归档', trainDuration: '5m30s', createdAt: '2026-07-01 02:00' }
])

const lifecycleTimeline = ref([
  { time: '2026-07-29 14:32', title: '自动重训练触发', desc: '定时调度触发v3.1版本训练，使用最新30天数据', type: 'primary', color: brandColor, metrics: ['Cron: 0 2 * * 1', '数据量: 128K'] },
  { time: '2026-07-22 03:00', title: 'v3.0版本部署上线', desc: '通过A/B测试验证，MAPE降低0.7%，正式替换v2.9', type: 'success', color: '#10b981', metrics: ['MAPE: 6.8%', '准确率: 93.2%', '提升: +0.7%'] },
  { time: '2026-07-22 02:15', title: 'v3.0版本训练完成', desc: '性能阈值触发重训练，新模型在测试集上表现优异', type: 'primary', color: brandColor, metrics: ['MAPE: 6.8%', 'R²: 0.942'] },
  { time: '2026-07-20 10:30', title: '性能告警触发', desc: 'v2.9模型MAPE连续3天超过8%，触发重训练流程', type: 'warning', color: '#f59e0b', metrics: ['MAPE: 8.3%', 'PSI: 0.21'] },
  { time: '2026-07-08 15:45', title: 'v2.8版本手动训练', desc: '数据科学家手动调优后训练，增加节假日特征', type: 'info', color: '#3b82f6', metrics: ['算法: XGBoost', '特征数: 42'] },
  { time: '2026-06-15 09:00', title: '系统上线', desc: '预测评估中心及模型优化系统正式投入使用', type: 'success', color: '#10b981', metrics: ['初始版本: v2.5'] }
])

const businessValues = ref([
  { title: '预测准确率提升', value: 12.5, suffix: '%', trend: 2.3, trendClass: 'up', trendIcon: '↑' },
  { title: '库存周转率提升', value: 18.2, suffix: '%', trend: 3.1, trendClass: 'up', trendIcon: '↑' },
  { title: '缺货率降低', value: 35.6, suffix: '%', trend: 5.2, trendClass: 'down', trendIcon: '↓' },
  { title: '预计成本节约', value: 286, prefix: '¥', suffix: '万', trend: 8.4, trendClass: 'up', trendIcon: '↑' }
])

const monthlyReports = ref([
  { month: '2026年7月', mape: 6.8, accuracy: 93.2 },
  { month: '2026年6月', mape: 7.5, accuracy: 92.5 },
  { month: '2026年5月', mape: 8.2, accuracy: 91.8 },
  { month: '2026年4月', mape: 9.1, accuracy: 90.9 }
])

const quarterlyReports = ref([
  { quarter: '2026 Q3', avgMape: 7.2, lift: 12.5 },
  { quarter: '2026 Q2', avgMape: 8.4, lift: 8.3 }
])

function generateDates(days) {
  const dates = []
  const today = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    dates.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  return dates
}

function accuracyColor(pct) {
  if (pct >= 93) return '#10b981'
  if (pct >= 88) return brandColor
  if (pct >= 82) return '#f59e0b'
  return '#ef4444'
}

function ratingType(rating) {
  const map = { '优秀': 'success', '良好': '', '一般': 'warning', '需优化': 'danger' }
  return map[rating] || 'info'
}

function statusType(status) { return status }
function statusTagType(status) { return { green: 'success', yellow: 'warning', red: 'danger' }[status] || 'info' }
function statusText(status) { return { green: '正常', yellow: '警告', red: '异常' }[status] || '未知' }
function taskStatusType(s) { return { completed: 'success', running: 'primary', pending: 'info', failed: 'danger' }[s] || 'info' }
function taskStatusText(s) { return { completed: '已完成', running: '运行中', pending: '等待中', failed: '失败' }[s] || s }
function pipelineStepType(s) { return { completed: 'success', running: 'primary', pending: 'info', failed: 'danger' }[s] || 'info' }
function pipelineStepText(s) { return { completed: '已完成', running: '进行中', pending: '等待中', failed: '失败' }[s] || s }
function retrainStatusType(s) { return { '已部署': 'success', '待审批': 'warning', '已回滚': 'danger', '已归档': 'info' }[s] || 'info' }

function initChart(refName, refValue) {
  chartRefs[refName] = refValue
}

function createChart(refName, option) {
  const el = chartRefs[refName]
  if (!el) return
  if (chartInstances[refName]) chartInstances[refName].dispose()
  const chart = echarts.init(el)
  chart.setOption(option)
  chartInstances[refName] = chart
  return chart
}

function getHealthGaugeOption() {
  return {
    series: [{
      type: 'gauge', startAngle: 200, endAngle: -20, min: 0, max: 100,
      itemStyle: { color: brandColor },
      progress: { show: true, width: 24 },
      pointer: { show: true, length: '60%', width: 6 },
      axisLine: { lineStyle: { width: 24, color: [[0.3, '#ef4444'], [0.7, '#f59e0b'], [1, '#10b981']] } },
      axisTick: { show: false }, splitLine: { length: 8, lineStyle: { width: 2 } },
      axisLabel: { distance: 24, fontSize: 12 },
      title: { offsetCenter: [0, '40%'], fontSize: 14, color: '#666' },
      detail: { fontSize: 36, offsetCenter: [0, '0%'], formatter: '{value}', color: brandColor, fontWeight: 'bold' },
      data: [{ value: 87, name: '健康度评分' }]
    }]
  }
}

function getAccuracyRankOption() {
  const models = ['补货建议v1.5', '库存需求v2.0', '销售预测v3.0', '价格弹性v1.2', '客流预测v2.1', '库存需求v1.3', '销售预测v2.1']
  const accs = [95.8, 94.8, 93.2, 92.9, 91.5, 87.7, 81.5]
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: p => `${p[0].name}<br/>准确率: ${p[0].value}%` },
    grid: { left: 120, right: 40, top: 20, bottom: 30 },
    xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    yAxis: { type: 'category', data: models.reverse(), axisLine: { show: false }, axisTick: { show: false } },
    series: [{
      type: 'bar', data: accs.reverse(), barWidth: 20,
      itemStyle: {
        color: params => {
          const v = params.value
          if (v >= 93) return '#10b981'
          if (v >= 88) return brandColor
          if (v >= 82) return '#f59e0b'
          return '#ef4444'
        },
        borderRadius: [0, 4, 4, 0]
      },
      label: { show: true, position: 'right', formatter: '{c}%', fontSize: 12 }
    }]
  }
}

function getAccuracyTrendOption() {
  const dates = generateDates(30)
  const base = 91
  const data = dates.map((_, i) => +(base + Math.sin(i / 4) * 1.5 + Math.random() * 1.2 + i * 0.05).toFixed(1))
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['平均准确率'], right: 20 },
    grid: { left: 50, right: 30, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', min: 85, max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{
      name: '平均准确率', type: 'line', data, smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { width: 3, color: brandColor }, itemStyle: { color: brandColor },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(15,118,110,0.3)' }, { offset: 1, color: 'rgba(15,118,110,0.02)' }]) }
    }]
  }
}

function getScatterOption() {
  const data = []
  for (let i = 0; i < 200; i++) {
    const actual = Math.random() * 100 + 20
    const noise = (Math.random() - 0.5) * 20
    const predicted = actual + noise * (actual / 100)
    data.push([+actual.toFixed(1), +predicted.toFixed(1)])
  }
  return {
    tooltip: { formatter: p => `实际: ${p.data[0]}<br/>预测: ${p.data[1]}` },
    grid: { left: 60, right: 30, top: 30, bottom: 50 },
    xAxis: { name: '实际值', type: 'value', min: 0, max: 130, nameLocation: 'center', nameGap: 30 },
    yAxis: { name: '预测值', type: 'value', min: 0, max: 130, nameLocation: 'center', nameGap: 40 },
    series: [
      {
        type: 'scatter', data, symbolSize: 8,
        itemStyle: { color: brandColor, opacity: 0.6 }
      },
      {
        type: 'line', data: [[0, 0], [130, 130]], lineStyle: { color: '#ef4444', type: 'dashed', width: 2 },
        symbol: 'none', tooltip: { show: false }
      }
    ]
  }
}

function getErrorDistOption() {
  const bins = []
  const counts = []
  for (let i = -30; i <= 30; i += 4) {
    bins.push(`${i}%`)
    const dist = Math.exp(-Math.pow(i / 10, 2) / 2)
    counts.push(Math.round(dist * 80 + Math.random() * 10))
  }
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: bins, axisLabel: { rotate: 45 } },
    yAxis: { type: 'value', name: '样本数' },
    series: [{
      type: 'bar', data: counts, barWidth: '80%',
      itemStyle: {
        color: params => {
          const idx = params.dataIndex
          const center = Math.floor(counts.length / 2)
          const dist = Math.abs(idx - center)
          if (dist <= 2) return '#10b981'
          if (dist <= 5) return brandColor
          if (dist <= 8) return '#f59e0b'
          return '#ef4444'
        },
        borderRadius: [3, 3, 0, 0]
      }
    }]
  }
}

function getConfusionOption() {
  const classes = ['A类', 'B类', 'C类', 'D类', 'E类']
  const n = classes.length
  const data = []
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      let v
      if (i === j) v = Math.floor(Math.random() * 30 + 80)
      else v = Math.floor(Math.random() * 20)
      data.push([j, i, v])
    }
  }
  return {
    tooltip: { position: 'top', formatter: p => `真实: ${classes[p.data[1]]}<br/>预测: ${classes[p.data[0]]}<br/>数量: ${p.data[2]}` },
    grid: { left: 80, right: 60, top: 20, bottom: 60 },
    xAxis: { type: 'category', data: classes, splitArea: { show: true }, name: '预测类别', nameLocation: 'center', nameGap: 30 },
    yAxis: { type: 'category', data: classes, splitArea: { show: true }, name: '真实类别', nameLocation: 'center', nameGap: 50 },
    visualMap: { min: 0, max: 110, calculable: true, orient: 'horizontal', left: 'center', bottom: 0, inRange: { color: ['#ecfdf5', brandColor, '#065f46'] } },
    series: [{
      type: 'heatmap', data, label: { show: true, fontSize: 12 },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.3)' } }
    }]
  }
}

function getDecayTrendOption() {
  const dates = generateDates(30)
  const mapeData = dates.map((_, i) => +(6 + Math.sin(i / 5) * 0.8 + i * 0.15 + Math.random() * 0.5).toFixed(2))
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['MAPE', '阈值'], right: 20 },
    grid: { left: 50, right: 30, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' }, min: 0 },
    series: [
      {
        name: 'MAPE', type: 'line', data: mapeData, smooth: true, symbol: 'circle', symbolSize: 5,
        lineStyle: { width: 2, color: brandColor }, itemStyle: { color: brandColor },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(15,118,110,0.2)' }, { offset: 1, color: 'rgba(15,118,110,0.02)' }]) },
        markLine: { silent: true, data: [{ yAxis: 15, label: { formatter: '阈值 15%', position: 'end' }, lineStyle: { color: '#ef4444', type: 'dashed' } }] }
      }
    ]
  }
}

function getPsiOption() {
  const dates = generateDates(30)
  const psiData = dates.map((_, i) => +(0.05 + Math.sin(i / 6) * 0.05 + i * 0.008 + Math.random() * 0.03).toFixed(3))
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 30, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', min: 0, max: 0.5 },
    series: [{
      name: 'PSI', type: 'line', data: psiData, smooth: true, symbol: 'none',
      lineStyle: { width: 2, color: '#8b5cf6' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(139,92,246,0.3)' }, { offset: 1, color: 'rgba(139,92,246,0.02)' }]) },
      markLine: { silent: true, data: [
        { yAxis: 0.1, label: { formatter: '轻微漂移', position: 'insideStartTop' }, lineStyle: { color: '#f59e0b', type: 'dashed' } },
        { yAxis: 0.25, label: { formatter: '显著漂移', position: 'insideStartTop' }, lineStyle: { color: '#ef4444', type: 'dashed' } }
      ]}
    }]
  }
}

function getLatencyQpsOption() {
  const hours = []
  for (let h = 0; h < 24; h++) hours.push(`${h}:00`)
  const latency = hours.map((_, h) => Math.round(20 + Math.sin(h / 3) * 10 + (h >= 9 && h <= 18 ? Math.random() * 30 + 15 : Math.random() * 10)))
  const qps = hours.map((_, h) => Math.round(800 + Math.sin(h / 3) * 400 + (h >= 9 && h <= 18 ? Math.random() * 800 + 500 : Math.random() * 200)))
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['延迟(ms)', 'QPS'], right: 20 },
    grid: { left: 60, right: 60, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: hours, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '延迟(ms)', position: 'left', axisLabel: { formatter: '{value}ms' } },
      { type: 'value', name: 'QPS', position: 'right', axisLabel: { formatter: '{value}' } }
    ],
    series: [
      { name: '延迟(ms)', type: 'line', data: latency, smooth: true, symbol: 'none', lineStyle: { width: 2, color: '#ef4444' }, itemStyle: { color: '#ef4444' } },
      { name: 'QPS', type: 'line', yAxisIndex: 1, data: qps, smooth: true, symbol: 'none', lineStyle: { width: 2, color: brandColor }, itemStyle: { color: brandColor }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(15,118,110,0.15)' }, { offset: 1, color: 'rgba(15,118,110,0.01)' }]) } }
    ]
  }
}

function getBiasTrendOption() {
  const dates = generateDates(30)
  const bias = dates.map((_, i) => +(Math.sin(i / 3) * 5 + Math.cos(i / 7) * 3 + (Math.random() - 0.5) * 4).toFixed(2))
  return {
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>偏差: ${p[0].value > 0 ? '+' : ''}${p[0].value}%` },
    legend: { data: ['偏差率'], right: 20 },
    grid: { left: 50, right: 30, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
    series: [{
      name: '偏差率', type: 'line', data: bias, smooth: true, symbol: 'circle', symbolSize: 5,
      lineStyle: { width: 2 }, itemStyle: { color: brandColor },
      areaStyle: {
        color: params => {
          const v = params.data
          if (v > 0) return new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(245,158,11,0.3)' }, { offset: 1, color: 'rgba(245,158,11,0.02)' }])
          return new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(59,130,246,0.3)' }, { offset: 1, color: 'rgba(59,130,246,0.02)' }])
        }
      },
      markLine: { silent: true, data: [{ yAxis: 0, lineStyle: { color: '#999', type: 'solid' } }] }
    }]
  }
}

function getBiasPieOption() {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}% ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['35%', '50%'], avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false }, emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: [
        { value: 38, name: '节假日效应', itemStyle: { color: '#ef4444' } },
        { value: 27, name: '数据漂移', itemStyle: { color: '#f59e0b' } },
        { value: 20, name: '外部因素', itemStyle: { color: brandColor } },
        { value: 15, name: '特征滞后', itemStyle: { color: '#3b82f6' } }
      ]
    }]
  }
}

function getBiasHeatmapOption() {
  const hours = ['00', '02', '04', '06', '08', '10', '12', '14', '16', '18', '20', '22']
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const data = []
  for (let d = 0; d < days.length; d++) {
    for (let h = 0; h < hours.length; h++) {
      let base = 5
      if ((d === 0 || d === 4) && h >= 3 && h <= 6) base = 18 + Math.random() * 10
      else if (d >= 5 && (h <= 2 || h >= 9)) base = 12 + Math.random() * 8
      else base = 4 + Math.random() * 6
      data.push([h, d, +base.toFixed(1)])
    }
  }
  return {
    tooltip: { position: 'top', formatter: p => `${days[p.data[1]]} ${hours[p.data[0]]}:00<br/>偏差: ${p.data[2]}%` },
    grid: { left: 60, right: 80, top: 20, bottom: 60 },
    xAxis: { type: 'category', data: hours.map(h => h + ':00'), splitArea: { show: true } },
    yAxis: { type: 'category', data: days, splitArea: { show: true } },
    visualMap: { min: 0, max: 30, calculable: true, orient: 'horizontal', left: 'center', bottom: 0, inRange: { color: ['#ecfdf5', brandColor, '#ef4444'] } },
    series: [{ type: 'heatmap', data, label: { show: true, fontSize: 10 }, emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)' } } }]
  }
}

function getParamImportanceOption() {
  const params = ['learning_rate', 'max_depth', 'n_estimators', 'subsample', 'colsample_bytree', 'min_child_weight', 'gamma', 'reg_alpha']
  const importance = [0.28, 0.22, 0.18, 0.12, 0.08, 0.06, 0.04, 0.02].map(v => +(v * 100).toFixed(1))
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 130, right: 40, top: 20, bottom: 30 },
    xAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
    yAxis: { type: 'category', data: params.reverse() },
    series: [{
      type: 'bar', data: importance.reverse(), barWidth: 18,
      itemStyle: { color: brandColor, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', formatter: '{c}%', fontSize: 11 }
    }]
  }
}

function getTuningScatterOption() {
  const data = []
  const pareto = []
  for (let i = 0; i < 80; i++) {
    const complexity = Math.random() * 100
    const error = 15 - Math.log(complexity + 1) * 2 + Math.random() * 4
    data.push([+complexity.toFixed(1), +Math.max(3, error).toFixed(2)])
  }
  const sorted = [...data].sort((a, b) => a[0] - b[0])
  let minErr = Infinity
  for (const p of sorted) {
    if (p[1] < minErr) {
      minErr = p[1]
      pareto.push(p)
    }
  }
  return {
    tooltip: { formatter: p => p.seriesName === 'Pareto前沿' ? `复杂度: ${p.data[0]}<br/>误差: ${p.data[1]}%` : `迭代点<br/>复杂度: ${p.data[0]}<br/>误差: ${p.data[1]}%` },
    legend: { data: ['迭代搜索点', 'Pareto前沿'], right: 20 },
    grid: { left: 60, right: 30, top: 40, bottom: 50 },
    xAxis: { name: '模型复杂度', type: 'value', nameLocation: 'center', nameGap: 30 },
    yAxis: { name: 'MAPE (%)', type: 'value', nameLocation: 'center', nameGap: 40 },
    series: [
      { name: '迭代搜索点', type: 'scatter', data, symbolSize: 7, itemStyle: { color: 'rgba(15,118,110,0.5)' } },
      { name: 'Pareto前沿', type: 'line', data: pareto, smooth: true, symbol: 'circle', symbolSize: 8, lineStyle: { color: '#ef4444', width: 2 }, itemStyle: { color: '#ef4444' } }
    ]
  }
}

function getVersionMapeOption() {
  const versions = ['v2.5', 'v2.6', 'v2.7', 'v2.8', 'v2.9', 'v3.0', 'v3.1']
  const mapes = [12.5, 10.8, 9.5, 8.8, 8.3, 6.8, 6.1]
  return {
    tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>MAPE: ${p[0].value}%` },
    grid: { left: 50, right: 30, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: versions },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' }, min: 0 },
    series: [{
      type: 'line', data: mapes, smooth: true, symbol: 'circle', symbolSize: 10,
      lineStyle: { width: 3, color: brandColor }, itemStyle: { color: brandColor, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, position: 'top', formatter: '{c}%', fontSize: 12, fontWeight: 'bold', color: brandColor },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(15,118,110,0.25)' }, { offset: 1, color: 'rgba(15,118,110,0.02)' }]) },
      markPoint: { data: [{ type: 'min', name: '最佳' }] }
    }]
  }
}

function initAllCharts() {
  initChart('health', healthGaugeRef.value)
  initChart('accuracyRank', accuracyRankRef.value)
  initChart('accuracyTrend', accuracyTrendRef.value)
  initChart('scatter', scatterRef.value)
  initChart('errorDist', errorDistRef.value)
  initChart('confusion', confusionRef.value)
  initChart('decayTrend', decayTrendRef.value)
  initChart('psi', psiChartRef.value)
  initChart('latencyQps', latencyQpsRef.value)
  initChart('biasTrend', biasTrendRef.value)
  initChart('biasPie', biasPieRef.value)
  initChart('biasHeatmap', biasHeatmapRef.value)
  initChart('paramImportance', paramImportanceRef.value)
  initChart('tuningScatter', tuningScatterRef.value)
  initChart('versionMape', versionMapeRef.value)

  createChart('health', getHealthGaugeOption())
  createChart('accuracyRank', getAccuracyRankOption())
  createChart('accuracyTrend', getAccuracyTrendOption())
  createChart('scatter', getScatterOption())
  createChart('errorDist', getErrorDistOption())
  createChart('confusion', getConfusionOption())
  createChart('decayTrend', getDecayTrendOption())
  createChart('psi', getPsiOption())
  createChart('latencyQps', getLatencyQpsOption())
  createChart('biasTrend', getBiasTrendOption())
  createChart('biasPie', getBiasPieOption())
  createChart('biasHeatmap', getBiasHeatmapOption())
  createChart('paramImportance', getParamImportanceOption())
  createChart('tuningScatter', getTuningScatterOption())
  createChart('versionMape', getVersionMapeOption())
}

async function loadData() {
  try {
    const models = await request.get('/v1/predictions/models')
    const rows = Array.isArray(models) ? models : []
    const toNumber = value => value == null ? null : Number(value)
    const accuracy = model => model.mape == null ? null : Math.max(0, 100 - Number(model.mape))
    modelEvaluations.value = rows.map(model => ({
      id: model.id,
      modelName: `${model.modelName || '未命名模型'} v${model.versionNumber || 1}`,
      algorithm: model.algorithmType || model.modelType || '未知',
      mae: toNumber(model.mae),
      rmse: toNumber(model.rmse),
      mape: toNumber(model.mape),
      r2: toNumber(model.r2),
      bias: toNumber(model.bias),
      accuracy: accuracy(model),
      rating: model.isProduction ? '生产中' : (model.status || '未发布')
    }))
    modelStatusCards.value = rows.map(model => ({
      id: model.id,
      name: `${model.modelName || '未命名模型'} v${model.versionNumber || 1}`,
      status: model.isProduction ? 'green' : 'yellow',
      mape: toNumber(model.mape),
      qps: null,
      latency: null,
      psi: null
    }))
    problemModels.value = modelEvaluations.value
      .filter(model => model.mape != null && model.mape > alertForm.mapeThreshold)
      .map(model => ({ ...model, problem: 'MAPE 超过当前阈值', suggestion: '请执行偏差分析或启动自动调优' }))
    if (rows.length) {
      const production = rows.find(model => model.isProduction) || rows[0]
      kpiCards.value[0].value = production.mape == null ? '—' : `${Math.max(0, 100 - Number(production.mape)).toFixed(1)}%`
      kpiCards.value[2].value = String(problemModels.value.length)
    }
  } catch (e) {
    modelEvaluations.value = []
    modelStatusCards.value = []
    problemModels.value = []
    ElMessage.error(`模型数据加载失败：${e.message || '后端接口不可用'}`)
  }
}

function onTabChange() {
  nextTick(() => {
    Object.values(chartInstances).forEach(c => c && c.resize())
  })
}

watch(() => route.query.tab, tab => {
  activeTab.value = validTabs.includes(tab) ? tab : 'overview'
})

function saveAlertConfig() {
  ElMessage.success('告警阈值配置已保存')
}

function startTuning() {
  tuningLoading.value = true
  ElMessage.info('自动调优任务已启动，正在初始化...')
  setTimeout(() => {
    tuningLoading.value = false
    tuningTasks.value.unshift({
      taskName: `${tuningForm.algorithm.toUpperCase()}调优#${Math.floor(Math.random() * 20)}`,
      algorithm: tuningForm.algorithm.toUpperCase(),
      bestScore: '--',
      currentIter: 0,
      iterations: tuningForm.maxIter,
      status: 'running',
      eta: '约25分钟'
    })
    ElMessage.success('调优任务已加入队列并开始执行')
  }, 2000)
}

function stopTuning(row) {
  row.status = 'failed'
  row.eta = '-'
  ElMessage.warning(`任务 "${row.taskName}" 已停止`)
}

function applyTuningResult(row) {
  selectedTuningResult.value = {
    ...row,
    bestParams: [
      { name: 'learning_rate', value: '0.08' },
      { name: 'max_depth', value: '7' },
      { name: 'n_estimators', value: '256' },
      { name: 'subsample', value: '0.85' }
    ]
  }
  tuningResultVisible.value = true
}

function applySelectedTuning() {
  tuningResultVisible.value = false
  ElMessage.success('已应用调优结果，新模型参数已生效')
}

function saveRetrainConfig() {
  ElMessage.success('重训练配置已保存')
}

function triggerManualRetrain() {
  ElMessage.success('手动重训练已触发，请查看流水线进度')
  pipelineActiveStep.value = 0
  pipelineSteps.value.forEach((s, i) => {
    s.status = i === 0 ? 'running' : 'pending'
    s.duration = i === 0 ? '-' : undefined
  })
  pipelineLogs.value = [{ time: new Date().toLocaleTimeString(), level: 'info', msg: '手动触发重训练流水线启动' }]
}

function deployChallenger() {
  ElMessage.success('Challenger模型已部署为新的Champion模型')
  championModel.value = { ...challengerModel.value, name: 'XGBoost v3.1 (生产)', deployTime: new Date().toLocaleString() }
}

function generateReport(type) {
  ElMessage.success(`${type === 'monthly' ? '月度' : '季度'}报告正在生成，稍后可在列表中下载`)
}

function handleResize() {
  Object.values(chartInstances).forEach(c => c && c.resize())
}

onMounted(() => {
  nextTick(() => {
    initAllCharts()
    loadData()
  })
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  Object.values(chartInstances).forEach(c => c && c.dispose())
})
</script>

<style scoped>
.model-optimization {
  min-height: calc(100vh - 60px);
  padding: 20px 22px 28px;
  color: #1f2937;
  background:
    radial-gradient(circle at top left, rgba(15, 118, 110, 0.08), transparent 32%),
    linear-gradient(180deg, #f8fafc 0%, #f3f6f7 100%);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  margin-bottom: 16px;
  border: 1px solid #e3e9ee;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.header-copy {
  min-width: 0;
}

.eyebrow {
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  margin-bottom: 8px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  color: #18212d;
}

.subtitle {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  border: 1px solid #e3e9ee;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
  transition: all 0.18s ease;
}

.kpi-card:hover {
  transform: translateY(-2px);
  border-color: #b8dfd8;
  box-shadow: 0 16px 30px rgba(15, 118, 110, 0.10);
}

.kpi-content {
  padding: 8px 0 2px;
}

.kpi-value {
  font-size: 27px;
  font-weight: 800;
  line-height: 1.2;
  color: #0f766e;
}

.kpi-label {
  margin-top: 7px;
  font-size: 12px;
  color: #64748b;
}

.kpi-trend {
  font-size: 12px;
  font-weight: 700;
  margin-left: 8px;
}

.kpi-trend.up {
  color: #34d399;
}

.kpi-trend.down {
  color: #fb7185;
}

.main-tabs {
  padding: 16px 12px 18px 0;
  border-radius: 18px;
  background: #fff;
  border: 1px solid #e3e9ee;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.06);
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  white-space: nowrap;
}

.tab-label :deep(.el-icon) {
  font-size: 15px;
}

:deep(.main-tabs .el-tabs__header.is-left) {
  width: 220px;
  margin-right: 0;
  border-right: 1px solid #e6edf3;
  background: #fbfdfe;
  border-radius: 16px 0 0 16px;
}

:deep(.main-tabs .el-tabs__nav-wrap.is-left::after) {
  display: none;
}

:deep(.main-tabs .el-tabs__nav.is-left) {
  width: 100%;
}

:deep(.main-tabs .el-tabs__item.is-left) {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: 54px;
  padding: 0 18px;
  margin: 8px 10px;
  border-radius: 12px;
  color: #64748b;
  transition: all 0.18s ease;
}

:deep(.main-tabs .el-tabs__item.is-left:hover) {
  color: #0f766e;
  background: #eef9f7;
}

:deep(.main-tabs .el-tabs__item.is-active) {
  color: #0f766e;
  font-weight: 800;
  background: #e2f3f0;
  box-shadow: 0 8px 18px rgba(15, 118, 110, 0.12);
}

:deep(.main-tabs .el-tabs__active-bar) {
  display: none;
}

:deep(.main-tabs .el-tabs__content) {
  flex: 1;
  padding: 0 12px 0 22px;
}

:deep(.main-tabs .el-tab-pane) {
  color: #334155;
}

.chart-container {
  width: 100%;
  height: 316px;
}

.gauge-chart {
  height: 280px;
}

.heatmap-chart {
  height: 380px;
}

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.form-unit {
  margin-left: 6px;
  color: #64748b;
  font-size: 13px;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #64748b;
}

:deep(.main-tabs .el-card) {
  margin-bottom: 16px;
  border: 1px solid #e3e9ee;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
  transition: all 0.18s ease;
}

:deep(.main-tabs .el-card:hover) {
  border-color: #b8dfd8;
  box-shadow: 0 16px 30px rgba(15, 118, 110, 0.09);
}

:deep(.main-tabs .el-card__header) {
  padding: 14px 16px;
  border-bottom: 1px solid #edf2f6;
  color: #18212d;
  background: #fbfdfe;
}

:deep(.main-tabs .el-card__body) {
  color: #334155;
}

.metric-explain-card .metric-explain-item {
  text-align: center;
  padding: 12px 8px;
  border-right: 1px solid #edf2f6;
}

.metric-explain-item:last-child {
  border-right: none;
}

.metric-name {
  font-size: 18px;
  font-weight: 800;
  color: #0f766e;
  margin-bottom: 4px;
}

.metric-desc {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.metric-formula {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  color: #0f766e;
  background: #e2f3f0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.model-status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.model-status-card {
  border-radius: 12px;
}

.status-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-dot.green {
  background: #34d399;
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.45);
}

.status-dot.yellow {
  background: #fbbf24;
  box-shadow: 0 0 8px rgba(251, 191, 36, 0.45);
}

.status-dot.red {
  background: #fb7185;
  box-shadow: 0 0 8px rgba(251, 113, 133, 0.45);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}

.model-name {
  font-weight: 700;
  font-size: 14px;
  flex: 1;
}

.status-metrics {
  margin-top: 8px;
}

.mini-metric {
  text-align: center;
  padding: 5px 0;
}

.mini-metric .val {
  font-size: 16px;
  font-weight: 800;
  color: #18212d;
  display: block;
}

.mini-metric .lab {
  font-size: 11px;
  color: #64748b;
}

.root-cause-panel {
  padding: 4px 0;
}

.cause-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #edf2f6;
}

.cause-item:last-child {
  border-bottom: none;
}

.cause-rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e2f3f0;
  color: #0f766e;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 14px;
  flex-shrink: 0;
}

.cause-content {
  flex: 1;
}

.cause-title {
  font-weight: 700;
  font-size: 14px;
  color: #18212d;
  margin-bottom: 4px;
}

.cause-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.55;
}

.cause-impact {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}

.param-range-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.param-name {
  width: 120px;
  font-size: 12px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.pipeline-container {
  padding: 20px 0;
}

.step-indicator {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}

.step-indicator.completed {
  background: #0f766e;
}

.step-indicator.running {
  background: #3b82f6;
  animation: spin 1.5s linear infinite;
}

.step-indicator.pending {
  background: #cbd5e1;
}

@keyframes spin {
  from {
    transform: rotate(0);
  }
  to {
    transform: rotate(360deg);
  }
}

.step-desc {
  text-align: center;
  font-size: 12px;
}

.step-duration {
  color: #94a3b8;
  margin-top: 2px;
}

.pipeline-log {
  margin-top: 24px;
  background: #f8fbfd;
  border: 1px solid #e6edf3;
  border-radius: 12px;
  padding: 14px 18px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
}

.log-line {
  margin-bottom: 4px;
  line-height: 1.6;
}

.log-time {
  color: #64748b;
  margin-right: 8px;
}

.log-level.info {
  color: #38bdf8;
}

.log-level.warn {
  color: #fbbf24;
}

.log-level.error {
  color: #f87171;
}

.log-msg {
  color: #334155;
}

.cc-card {
  padding: 16px;
  border-radius: 14px;
  border: 1px solid #e6edf3;
}

.cc-card.champion {
  background: #f3fbfa;
}

.cc-card.challenger {
  background: #fffaf1;
}

.cc-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.cc-model-name {
  font-size: 16px;
  font-weight: 800;
  color: #18212d;
  flex: 1;
}

.timeline-container {
  max-height: 600px;
  overflow-y: auto;
  padding-right: 8px;
}

.timeline-card {
  margin: 0;
  background: #fff;
}

.timeline-card h4 {
  margin: 0 0 6px 0;
  font-size: 14px;
  color: #18212d;
}

.timeline-card p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.timeline-metrics {
  margin-top: 8px;
}

.value-stat-item {
  padding: 14px 0;
  border-bottom: 1px solid #edf2f6;
}

.value-stat-item:last-child {
  border-bottom: none;
}

.value-trend {
  font-size: 12px;
  margin-top: 4px;
}

.value-trend.up {
  color: #34d399;
}

.value-trend.down {
  color: #fb7185;
}

.report-preview {
  padding: 4px 0;
}

.report-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #edf2f6;
}

.report-item:last-child {
  border-bottom: none;
}

.report-month {
  font-weight: 700;
  font-size: 14px;
  color: #18212d;
}

.report-metrics {
  font-size: 13px;
  color: #64748b;
  display: flex;
  gap: 16px;
}

.report-metrics b {
  color: #18212d;
}

:deep(.main-tabs .el-table) {
  --el-table-border-color: #e6edf3;
  --el-table-header-bg-color: #f8fbfd;
  --el-table-tr-bg-color: #fff;
  --el-table-row-hover-bg-color: #f3fbfa;
  color: #334155;
  background: transparent;
}

:deep(.main-tabs .el-table th.el-table__cell) {
  color: #64748b;
  font-weight: 700;
}

:deep(.main-tabs .el-table td.el-table__cell) {
  background: transparent;
}

:deep(.main-tabs .el-table .el-table__body tr:nth-child(even) td.el-table__cell) {
  background: #fbfdfe;
}

:deep(.main-tabs .el-table .el-table__body tr:hover > td.el-table__cell) {
  background: #eef9f7 !important;
}

:deep(.main-tabs .el-progress__text),
:deep(.main-tabs .el-descriptions__label),
:deep(.main-tabs .el-descriptions__content) {
  color: #334155;
}

:deep(.main-tabs .el-steps--horizontal) {
  --el-text-color-regular: #334155;
}

@media (max-width: 1200px) {
  .page-header {
    flex-direction: column;
  }

  :deep(.main-tabs) {
    padding: 12px;
  }

  :deep(.main-tabs .el-tabs__header.is-left) {
    width: 180px;
  }
}

@media (max-width: 960px) {
  :deep(.main-tabs) {
    display: block;
  }

  :deep(.main-tabs .el-tabs__header.is-left) {
    width: 100%;
    border-radius: 16px;
    margin-bottom: 14px;
  }

  :deep(.main-tabs .el-tabs__content) {
    padding: 0;
  }
}

@media (max-width: 760px) {
  .model-optimization {
    padding: 14px;
  }

  .kpi-row :deep(.el-col) {
    margin-bottom: 12px;
  }

  .chart-container {
    height: 260px;
  }

  .gauge-chart {
    height: 240px;
  }

  .heatmap-chart {
    height: 300px;
  }

  .header-actions {
    justify-content: flex-start;
  }
}
</style>
