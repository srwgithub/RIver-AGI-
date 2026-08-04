<template>
  <div class="module-page">
    <div class="module-heading">
      <div>
        <span class="eyebrow">MARKET DEMAND FORECASTING</span>
        <h1>市场需求预测引擎</h1>
        <p>当前页面用于创建时间序列、回归、分类、序列和深度学习预测任务。</p>
      </div>
      <el-button type="primary" @click="$router.push('/prediction-engine/models')">模型管理后台</el-button>
    </div>
    <Prediction />
  </div>
</template>

<script setup>
import Prediction from './Prediction.vue'
</script>

<style scoped>
.module-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:18px; }
.eyebrow { color:#6c49b8; font-size:12px; font-weight:700; letter-spacing:.08em; }
h1 { margin:6px 0; font-size:24px; color:var(--river-text,#1f2937); }
p { margin:0; color:var(--river-muted,#8c98a4); font-size:13px; }
@media (max-width: 700px) { .module-heading { align-items:flex-start; flex-direction:column; } }
</style>
