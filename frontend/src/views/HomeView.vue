<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'

const status = ref<'checking' | 'UP' | 'DOWN' | 'unreachable'>('checking')

onMounted(async () => {
  try {
    const { data } = await axios.get<{ status: 'UP' | 'DOWN' }>('/actuator/health')
    status.value = data.status
  } catch {
    status.value = 'unreachable'
  }
})
</script>

<template>
  <el-container class="page">
    <el-main>
      <el-card>
        <template #header>Springboot Modulith Admin 开发环境</template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="前端"
            >Vue 3 + Vite + TypeScript + Element Plus</el-descriptions-item
          >
          <el-descriptions-item label="后端"
            >Spring Boot 4.1 + Spring Modulith 2.1（端口 8080）</el-descriptions-item
          >
          <el-descriptions-item label="后端健康检查">
            <el-tag v-if="status === 'UP'" type="success">UP</el-tag>
            <el-tag v-else-if="status === 'checking'" type="info">检查中</el-tag>
            <el-tag v-else type="danger">{{ status }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </el-main>
  </el-container>
</template>

<style scoped>
.page {
  max-width: 880px;
  margin: 48px auto;
}
</style>
