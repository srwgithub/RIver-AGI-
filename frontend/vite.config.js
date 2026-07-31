import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/trend-dashboard.html': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/model-optimization.html': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/annotation-platform.html': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/annotation-quality.html': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/prediction-engine.html': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/prediction-evaluation.html': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/security-audit.html': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    cssCodeSplit: true,
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router'],
          'element-vendor': ['element-plus', '@element-plus/icons-vue'],
          'chart-vendor': ['echarts']
        }
      }
    }
  }
})
