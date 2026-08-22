import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 输出到 target/dist/static，由 maven-jar-plugin 打进 jar 的 static/ 目录，
// short-link-server 依赖后经 classpath:/static 直接提供服务。
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: 'target/dist/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      // 本地开发：前端 5173，接口代理到后端 8080
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
