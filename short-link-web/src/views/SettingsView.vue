<script setup>
// 设置页：个人资料（后端当前无成员/通知/API密钥能力，按现状简化呈现）
import { onMounted, ref } from 'vue';
import { api } from '../api/index.js';
import { useAuthStore } from '../stores/auth.js';
import Icon from '../components/Icon.vue';

const auth = useAuthStore();
const profile = ref(null);

onMounted(async () => {
  try {
    profile.value = await api.me();
  } catch {
    /* 拦截器已提示 */
  }
});

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—');
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">设置</div>
        <div class="page-sub">账户信息与系统说明</div>
      </div>
    </div>

    <div class="card" style="max-width:640px">
      <div class="card-body" style="padding:24px">
        <div style="display:flex;align-items:center;gap:16px;margin-bottom:22px">
          <div class="avatar" style="width:56px;height:56px;font-size:22px">
            {{ (profile?.nickname || profile?.username || '?').slice(0, 1).toUpperCase() }}
          </div>
          <div>
            <div style="font-size:15px;font-weight:700">{{ profile?.nickname || '—' }}</div>
            <div style="font-size:12.5px;color:var(--ink-3);margin-top:2px">
              {{ profile?.username }} · {{ profile?.role === 'ADMIN' ? '管理员' : '成员' }}
            </div>
          </div>
          <span class="pill green" style="margin-left:auto">账户正常</span>
        </div>

        <div class="divider-h" />

        <div style="display:grid;grid-template-columns:110px 1fr;row-gap:14px;font-size:13px">
          <span style="color:var(--ink-3)">用户 ID</span>
          <span class="mono tnum">{{ profile?.userId ?? '—' }}</span>
          <span style="color:var(--ink-3)">用户名</span>
          <span>{{ profile?.username ?? '—' }}</span>
          <span style="color:var(--ink-3)">昵称</span>
          <span>{{ profile?.nickname ?? '—' }}</span>
          <span style="color:var(--ink-3)">角色</span>
          <span>{{ profile?.role === 'ADMIN' ? '管理员（可管理全部短链与域名）' : '成员' }}</span>
          <span style="color:var(--ink-3)">注册时间</span>
          <span class="tnum">{{ fmtTime(profile?.createTime) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
