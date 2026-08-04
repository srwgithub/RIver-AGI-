<template>
  <div class="workbench-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">DATA ANNOTATION WORKBENCH</span>
        <h1>标注工作台</h1>
        <p>选择数据集和标签体系，创建标注任务，完成标注提交、审核、仲裁和质量评估。</p>
      </div>
      <el-button @click="$router.push('/collection-annotation')">返回数据采集与清洗</el-button>
    </div>
    <Annotation />
  </div>
</template>

<script setup>
import Annotation from './Annotation.vue'
</script>

<style scoped>
.page-heading { display:flex; align-items:center; justify-content:space-between; gap:20px; margin-bottom:18px; }
.eyebrow { color:#1664d9; font-size:12px; font-weight:700; letter-spacing:.08em; }
h1 { margin:6px 0; font-size:24px; color:var(--river-text,#1f2937); }
p { margin:0; color:var(--river-muted,#8c98a4); font-size:13px; }
@media (max-width:700px) { .page-heading { align-items:flex-start; flex-direction:column; } }
</style>
