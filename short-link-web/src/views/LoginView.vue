<script setup>
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
  <div style="min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;background:var(--bg)">
    <div class="card" style="width:408px;padding:38px 36px 30px">
      <div style="display:flex;align-items:center;gap:11px;margin-bottom:30px;justify-content:center">
        <div class="brand-mark" style="width:36px;height:36px;border-radius:10px">
          <Icon name="link" :size="19" :sw="2" />
        </div>
        <div>
          <div class="brand-name" style="font-size:17px">Slink</div>
          <div class="brand-sub" style="text-align:left">短链平台</div>
        </div>
      </div>

      <h1 style="font-size:20px;font-weight:800;letter-spacing:-.02em;margin-bottom:6px">
        {{ mode === 'login' ? '登录到 Slink' : '创建账号' }}
      </h1>
      <p style="font-size:13px;color:var(--ink-3);margin-bottom:24px">
        {{ mode === 'login' ? '管理短链接、域名与访问数据' : '注册后即可使用短链服务' }}
      </p>

      <div class="field">
        <label>用户名</label>
        <input v-model="username" class="input" placeholder="3-32 位字母、数字、下划线" @keydown.enter="submit" />
      </div>
      <div v-if="mode === 'register'" class="field">
        <label>昵称（可选）</label>
        <input v-model="regNickname" class="input" placeholder="展示昵称" @keydown.enter="submit" />
      </div>
      <div class="field">
        <label>密码</label>
        <input
          v-model="password"
          class="input"
          type="password"
          placeholder="••••••••"
          @keydown.enter="submit"
        />
      </div>

      <button class="btn btn-primary btn-block" style="height:38px;margin-top:6px" :disabled="loading" @click="submit">
        {{ loading ? '请稍候…' : mode === 'login' ? '登 录' : '注 册' }}
      </button>

      <div style="text-align:center;margin-top:22px;padding-top:16px;border-top:1px dashed var(--hair)">
        <span style="font-size:12.5px;color:var(--ink-3)">
          {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
        </span>
        <button class="link-underline" style="font-size:12.5px" @click="mode = mode === 'login' ? 'register' : 'login'">
          {{ mode === 'login' ? '注册账号' : '去登录' }}
        </button>
      </div>
    </div>
  </div>
</template>
