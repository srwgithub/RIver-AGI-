<template>
  <div class="module-page">
    <div class="module-heading">
      <div>
        <span class="eyebrow">ANNOTATION QUALITY CENTER</span>
        <h1>标注质量管理中心</h1>
        <p>在当前页面完成抽检、审核、一致性检查、自动校验、纠偏、多轮标注、仲裁和绩效评估。</p>
      </div>
      <el-button type="primary" @click="$router.push('/annotation-quality/rules')">规则配置后台</el-button>
    </div>
    <Annotation />
  </div>
</template>

<script setup>
import Annotation from './Annotation.vue'
</script>

<style scoped>
.module-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:18px; }
.eyebrow { color:var(--river-brand,#0f766e); font-size:12px; font-weight:700; letter-spacing:.08em; }
h1 { margin:6px 0; font-size:24px; color:var(--river-text,#1f2937); }
p { margin:0; color:var(--river-muted,#8c98a4); font-size:13px; }
@media (max-width: 700px) { .module-heading { align-items:flex-start; flex-direction:column; } }
</style>
