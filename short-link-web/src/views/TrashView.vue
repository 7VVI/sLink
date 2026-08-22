<script setup>
import { onMounted, ref } from 'vue';
import { api } from '../api/index.js';
import { useAuthStore } from '../stores/auth.js';
import { toast } from '../stores/toast.js';
import Icon from '../components/Icon.vue';
import Empty from '../components/Empty.vue';
import ConfirmModal from '../components/ConfirmModal.vue';

const auth = useAuthStore();
const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;
const purging = ref(null);
const emptying = ref(false);
const loading = ref(false);

const pages = () => Math.max(1, Math.ceil(total.value / pageSize));

const load = async () => {
  loading.value = true;
  try {
    const page = await api.pageRecycle({ pageNo: pageNo.value, pageSize });
    rows.value = page.records;
    total.value = page.total;
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false;
  }
};

const daysLeft = (r) => {
  const purge = new Date(r.purgeTime).getTime();
  const left = Math.ceil((purge - Date.now()) / 86400000);
  return Math.max(0, left);
};

const restore = async (r) => {
  try {
    await api.restore(r.code);
    toast(`短链 ${r.code} 已还原`);
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const purge = async () => {
  const code = purging.value.code;
  purging.value = null;
  try {
    await api.purgeOne(code);
    toast('已彻底删除');
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const emptyAll = async () => {
  emptying.value = false;
  let ok = 0;
  for (const r of [...rows.value]) {
    try {
      await api.purgeOne(r.code);
      ok++;
    } catch {
      /* 跳过失败条目 */
    }
  }
  toast(`已彻底删除本页 ${ok} 条短链`);
  pageNo.value = 1;
  await load();
};

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—');

onMounted(load);
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">回收站</div>
        <div class="page-sub">已删除的短链保留 30 天后自动彻底清除，期间可随时恢复</div>
      </div>
      <div class="head-actions">
        <button
          class="btn btn-danger-ghost"
          :disabled="!rows.length"
          @click="emptying = true"
        >
          <Icon name="trash" :size="15" />清空本页
        </button>
      </div>
    </div>

    <div class="card">
      <template v-if="rows.length">
        <div style="overflow-x:auto">
          <table class="tbl">
            <thead>
              <tr>
                <th>短码</th>
                <th>目标链接</th>
                <th>分组</th>
                <th>删除时间</th>
                <th>剩余保留</th>
                <th style="text-align:right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in rows" :key="r.code">
                <td>
                  <span class="shortlink mono" style="color:var(--ink-2);text-decoration:line-through">{{ r.code }}</span>
                </td>
                <td style="max-width:240px">
                  <span
                    style="color:var(--ink-2);font-size:12.5px;display:inline-block;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
                  >{{ (r.longUrl || '').replace(/^https?:\/\//, '') }}</span>
                </td>
                <td><span class="tag">{{ r.groupName || '未分组' }}</span></td>
                <td style="color:var(--ink-3);font-size:12.5px;white-space:nowrap">{{ fmtTime(r.deleteTime) }}</td>
                <td>
                  <span class="chip"><b>{{ daysLeft(r) }}</b> 天</span>
                  <div class="progress" style="width:110px;display:inline-block;vertical-align:middle;margin-left:8px">
                    <div :style="{ width: `${(daysLeft(r) / 30) * 100}%`, background: 'var(--ink-3)' }" />
                  </div>
                </td>
                <td>
                  <div style="display:flex;justify-content:flex-end;gap:8px">
                    <button class="btn btn-ghost btn-sm" @click="restore(r)">
                      <Icon name="refresh" :size="13" />恢复
                    </button>
                    <button class="btn btn-ghost btn-sm" style="color:var(--red)" @click="purging = r">彻底删除</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pager">
          <span style="font-size:12.5px;color:var(--ink-3)">第 {{ pageNo }} / {{ pages() }} 页 · 共 {{ total }} 条</span>
          <div style="display:flex;gap:4px">
            <button class="page-btn" :disabled="pageNo <= 1" @click="pageNo--; load()"><Icon name="chevLeft" :size="14" /></button>
            <button v-for="p in pages()" :key="p" class="page-btn" :class="{ on: p === pageNo }" @click="pageNo = p; load()">{{ p }}</button>
            <button class="page-btn" :disabled="pageNo >= pages()" @click="pageNo++; load()"><Icon name="chevRight" :size="14" /></button>
          </div>
        </div>
      </template>
      <Empty v-else-if="!loading" icon="trash" title="回收站是空的" sub="删除的短链会保留 30 天，这里将显示待清理的条目" />
      <div style="padding:14px 18px;border-top:1px solid #F2F2F0;display:flex;align-items:center;gap:8px">
        <Icon name="info" :size="14" style="color:var(--ink-3)" />
        <span style="font-size:12.5px;color:var(--ink-3)">
          彻底删除后短链地址立即失效且无法恢复；系统每天 03:30 自动清理超期条目<template v-if="auth.isAdmin">，也可在管理端手动触发</template>。
        </span>
      </div>
    </div>

    <ConfirmModal
      v-if="purging"
      title="彻底删除"
      :desc="`确定彻底删除短链 ${purging.code} 吗？删除后无法恢复，统计数据将一并清除。`"
      confirm-text="彻底删除"
      @close="purging = null"
      @confirm="purge()"
    />
    <ConfirmModal
      v-if="emptying"
      title="清空回收站"
      :desc="`将彻底删除当前页的 ${rows.length} 条短链，删除后无法恢复。`"
      confirm-text="全部彻底删除"
      @close="emptying = false"
      @confirm="emptyAll"
    />
  </div>
</template>
