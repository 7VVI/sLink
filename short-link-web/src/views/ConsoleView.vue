<script setup>
// 控制台外壳：侧边导航 + 顶栏 + 路由视图
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api } from '../api/index.js';
import { useAuthStore } from '../stores/auth.js';
import Icon from '../components/Icon.vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const sections = [
  {
    label: '管理',
    items: [
      { id: 'links', label: '短链接', icon: 'link' },
      { id: 'groups', label: '分组', icon: 'folder' },
      { id: 'domains', label: '域名', icon: 'globe' },
    ],
  },
  {
    label: '洞察',
    items: [{ id: 'analytics', label: '访问监控', icon: 'chart' }],
  },
  {
    label: '系统',
    items: [{ id: 'settings', label: '设置', icon: 'sliders' }],
  },
];

const titles = {
  links: '短链接',
  groups: '分组',
  domains: '域名管理',
  analytics: '访问监控',
  trash: '回收站',
  settings: '设置',
};

const menuOpen = ref(false);
const menuRef = ref(null);

const nav = (id) => router.push({ name: id });

const logout = async () => {
  try {
    await api.logout();
  } catch {
    /* 会话可能已失效 */
  }
  auth.clear();
  router.push({ name: 'login' });
};

const onClickOutside = (e) => {
  if (menuRef.value && !menuRef.value.contains(e.target)) {
    menuOpen.value = false;
  }
};
onMounted(() => document.addEventListener('mousedown', onClickOutside));
</script>

<template>
  <div class="shell">
    <aside class="nav">
      <div class="brand">
        <div class="brand-mark"><Icon name="link" :size="17" :sw="2" /></div>
        <div>
          <div class="brand-name">Slink</div>
          <div class="brand-sub">短链平台</div>
        </div>
      </div>
      <div v-for="s in sections" :key="s.label">
        <div class="nav-section">{{ s.label }}</div>
        <button
          v-for="it in s.items"
          :key="it.id"
          class="nav-item"
          :class="{ active: route.name === it.id }"
          @click="nav(it.id)"
        >
          <Icon :name="it.icon" :size="16" />
          <span>{{ it.label }}</span>
          <span class="grow" />
        </button>
      </div>
      <div class="nav-bottom">
        <button class="nav-item" :class="{ active: route.name === 'trash' }" @click="nav('trash')">
          <Icon name="trash" :size="16" />
          <span>回收站</span>
          <span class="grow" />
        </button>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div style="font-size:13px;font-weight:500;color:var(--ink-3);display:flex;align-items:center;gap:8px">
          <span>Slink</span>
          <Icon name="chevDown" :size="13" />
          <span style="color:var(--ink);font-weight:600">{{ titles[route.name] || '' }}</span>
        </div>
        <div class="spacer" />
        <div v-if="auth.user" ref="menuRef" class="top-avatar">
          <div
            class="avatar"
            style="width:30px;height:30px;font-size:12px"
            title="点击打开用户菜单"
            @click="menuOpen = !menuOpen"
          >
            {{ (auth.user.nickname || auth.user.username || '?').slice(0, 1).toUpperCase() }}
          </div>
          <div v-if="menuOpen" class="avatar-menu">
            <div class="avatar-menu-head">
              <div class="avatar-menu-name">{{ auth.user.nickname }}</div>
              <div class="avatar-menu-mail">{{ auth.user.username }} · {{ auth.user.role === 'ADMIN' ? '管理员' : '成员' }}</div>
            </div>
            <button class="avatar-menu-item" @click="menuOpen = false; logout()">
              <Icon name="logout" :size="14" />退出登录
            </button>
          </div>
        </div>
      </header>
      <router-view :key="route.fullPath" />
    </div>
  </div>
</template>
