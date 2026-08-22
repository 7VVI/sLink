<script setup>
// 登录页：左右分栏（左品牌展示 / 右表单），窄屏自动收起左侧
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api/index.js';
import { useAuthStore } from '../stores/auth.js';
import { toast } from '../stores/toast.js';
import Icon from '../components/Icon.vue';

const router = useRouter();
const auth = useAuthStore();

const username = ref('');
const password = ref('');
const loading = ref(false);
const mode = ref('login'); // login | register
const regNickname = ref('');

const submit = async () => {
  if (!username.value.trim() || !password.value.trim()) {
    toast('请输入用户名和密码', 'err');
    return;
  }
  loading.value = true;
  try {
    if (mode.value === 'register') {
      if (password.value.length < 6) {
        toast('密码至少 6 位', 'err');
        return;
      }
      await api.register({
        username: username.value.trim(),
        password: password.value,
        nickname: regNickname.value.trim() || undefined,
      });
      toast('注册成功，请登录');
      mode.value = 'login';
      return;
    }
    const login = await api.login({ username: username.value.trim(), password: password.value });
    auth.setSession(login.tokenValue, {
      userId: login.userId,
      nickname: login.nickname,
      role: login.role,
      username: username.value.trim(),
    });
    toast(`欢迎回来，${login.nickname}`);
    router.push({ name: 'links' });
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌展示区 -->
    <div class="login-hero">
      <div class="hero-brand">
        <div class="brand-mark" style="width:34px;height:34px;border-radius:10px">
          <Icon name="link" :size="18" :sw="2" />
        </div>
        <span style="font-size:16px;font-weight:800;letter-spacing:-.01em">Slink</span>
      </div>

      <div class="hero-body">
        <div class="hero-title">
          简单、可靠的<br />企业级短链服务
        </div>
        <p class="hero-sub">
          号段发号、三级缓存、分库分表与异步统计，一个服务承载高并发短链跳转与管理控制台。
        </p>
        <div class="hero-features">
          <div class="hero-feature">
            <span class="hero-feature-icon"><Icon name="link" :size="15" /></span>
            <div>
              <div class="hero-feature-title">毫秒级跳转</div>
              <div class="hero-feature-sub">Caffeine + Redis + MySQL 三级缓存</div>
            </div>
          </div>
          <div class="hero-feature">
            <span class="hero-feature-icon"><Icon name="chart" :size="15" /></span>
            <div>
              <div class="hero-feature-title">实时访问统计</div>
              <div class="hero-feature-sub">PV / UV 去重，按日归档趋势</div>
            </div>
          </div>
          <div class="hero-feature">
            <span class="hero-feature-icon"><Icon name="folder" :size="15" /></span>
            <div>
              <div class="hero-feature-title">分组 · 域名 · 回收站</div>
              <div class="hero-feature-sub">多维管理，删除 30 天内可恢复</div>
            </div>
          </div>
        </div>
      </div>

      <div class="hero-foot tnum">
        <span>64 分片</span><i />
        <span>100 QPS/IP 限流</span><i />
        <span>30 天回收站</span>
      </div>

      <!-- 装饰：大号链接水印 -->
      <Icon name="link" :size="420" class="hero-watermark" />
    </div>

    <!-- 右侧表单区 -->
    <div class="login-panel">
      <div class="login-box">
        <h1 style="font-size:22px;font-weight:800;letter-spacing:-.02em;margin-bottom:6px">
          {{ mode === 'login' ? '登录到 Slink' : '创建账号' }}
        </h1>
        <p style="font-size:13px;color:var(--ink-3);margin-bottom:26px">
          {{ mode === 'login' ? '管理短链接、域名与访问数据' : '注册后即可使用短链服务' }}
        </p>

        <input
          v-model="username"
          class="input login-input"
          placeholder="用户名"
          @keydown.enter="submit"
        />
        <input
          v-if="mode === 'register'"
          v-model="regNickname"
          class="input login-input"
          placeholder="昵称（可选）"
          @keydown.enter="submit"
        />
        <input
          v-model="password"
          class="input login-input"
          type="password"
          placeholder="密码"
          @keydown.enter="submit"
        />

        <button class="btn btn-primary btn-block login-input" style="font-weight:600" :disabled="loading" @click="submit">
          {{ loading ? '请稍候…' : mode === 'login' ? '登 录' : '注 册' }}
        </button>

        <div style="text-align:center;margin-top:26px;padding-top:18px;border-top:1px dashed var(--hair)">
          <span style="font-size:12.5px;color:var(--ink-3)">
            {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
          </span>
          <button class="link-underline" style="font-size:12.5px" @click="mode = mode === 'login' ? 'register' : 'login'">
            {{ mode === 'login' ? '注册账号' : '去登录' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: var(--bg);
}

/* ---- 左侧品牌区 ---- */
.login-hero {
  position: relative;
  flex: 1.35;
  min-width: 0;
  background: #111827;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 40px 52px;
  overflow: hidden;
}
.hero-brand {
  display: flex;
  align-items: center;
  gap: 11px;
  position: relative;
  z-index: 1;
}
.hero-brand .brand-mark {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}
.hero-body {
  position: relative;
  z-index: 1;
  max-width: 480px;
  margin-bottom: 20vh;
}
.hero-title {
  font-size: 34px;
  font-weight: 800;
  line-height: 1.28;
  letter-spacing: -0.02em;
  margin-bottom: 14px;
}
.hero-sub {
  font-size: 14px;
  line-height: 1.75;
  color: rgba(255, 255, 255, 0.66);
  margin-bottom: 34px;
}
.hero-features {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.hero-feature {
  display: flex;
  align-items: center;
  gap: 13px;
}
.hero-feature-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.10);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.hero-feature-title {
  font-size: 13.5px;
  font-weight: 600;
}
.hero-feature-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  margin-top: 1px;
}
.hero-foot {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}
.hero-foot i {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
}
.hero-watermark {
  position: absolute;
  right: -90px;
  bottom: -110px;
  opacity: 0.07;
  pointer-events: none;
}

/* ---- 右侧表单区 ---- */
.login-panel {
  flex: 1;
  min-width: 0;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
}
.login-box {
  width: 100%;
  max-width: 344px;
}
.login-input {
  height: 44px;
  font-size: 13.5px;
}
.login-box .input {
  margin-bottom: 14px;
}

/* 窄屏：隐藏品牌区，仅保留表单 */
@media (max-width: 880px) {
  .login-hero {
    display: none;
  }
}
</style>
