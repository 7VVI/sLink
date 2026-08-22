<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '../api/index.js';
import { copyText, toast } from '../stores/toast.js';
import Icon from '../components/Icon.vue';
import Empty from '../components/Empty.vue';
import ConfirmModal from '../components/ConfirmModal.vue';
import CreateLinkModal from '../components/CreateLinkModal.vue';
import EditLinkModal from '../components/EditLinkModal.vue';
import QrPopover from '../components/QrPopover.vue';
import LinkStatsModal from '../components/LinkStatsModal.vue';

const route = useRoute();

const rows = ref([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = 10;
const loading = ref(false);

const groups = ref([]);
const groupFilter = ref(route.query.groupId ?? 'all');
const statusFilter = ref('all');
const keyword = ref('');

const showCreate = ref(false);
const editing = ref(null);
const qr = ref(null);
const statsCode = ref('');
const deleting = ref(null);

const filtered = computed(() =>
  rows.value.filter((r) => {
    if (statusFilter.value !== 'all' && Number(r.status) !== Number(statusFilter.value)) {
      return false;
    }
    if (keyword.value) {
      const q = keyword.value.toLowerCase();
      const hit =
        r.code.toLowerCase().includes(q) ||
        (r.longUrl || '').toLowerCase().includes(q) ||
        (r.groupName || '').includes(keyword.value) ||
        (r.title || '').includes(keyword.value);
      if (!hit) {
        return false;
      }
    }
    return true;
  }),
);

const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

const load = async () => {
  loading.value = true;
  try {
    const params = { pageNo: pageNo.value, pageSize };
    if (groupFilter.value !== 'all') {
      params.groupId = groupFilter.value;
    }
    const page = await api.pageLinks(params);
    rows.value = page.records;
    total.value = page.total;
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false;
  }
};

const loadGroups = async () => {
  try {
    groups.value = await api.listGroups();
  } catch {
    /* 忽略 */
  }
};

const onGroupFilter = () => {
  pageNo.value = 1;
  load();
};

const toggleStatus = async (row) => {
  const enabled = Number(row.status) !== 1;
  try {
    await api.changeStatus(row.code, enabled);
    row.status = enabled ? 1 : 0;
    toast(enabled ? '短链已上线' : '短链已下线，秒级生效');
  } catch {
    /* 拦截器已提示 */
  }
};

const remove = async () => {
  const code = deleting.value.code;
  deleting.value = null;
  try {
    await api.removeLink(code);
    toast('已移入回收站，30 天内可恢复');
    await load();
  } catch {
    /* 拦截器已提示 */
  }
};

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—');

onMounted(() => {
  load();
  loadGroups();
});
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="page-title">短链接</div>
        <div class="page-sub">创建、管理与分享你的短链接；点击任意一行查看访问统计</div>
      </div>
      <div class="head-actions">
        <button class="btn btn-primary" @click="showCreate = true">
          <Icon name="plus" :size="15" :sw="2" />新建短链接
        </button>
      </div>
    </div>

    <div class="card">
      <div class="table-toolbar">
        <div class="search" style="width:250px">
          <Icon name="search" :size="14" />
          <input v-model="keyword" placeholder="搜索本页短码 / 目标链接…" />
        </div>
        <select v-model="groupFilter" class="select" style="width:140px" @change="onGroupFilter">
          <option value="all">全部分组</option>
          <option value="0">未分组</option>
          <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
        </select>
        <select v-model="statusFilter" class="select" style="width:110px">
          <option value="all">全部状态</option>
          <option value="1">启用</option>
          <option value="0">停用</option>
        </select>
        <button class="icon-btn" style="width:34px;height:34px" title="刷新" @click="load">
          <Icon name="refresh" :size="15" />
        </button>
        <div style="flex:1" />
        <span style="font-size:12.5px;color:var(--ink-3)">
          共 <b class="tnum" style="color:var(--ink)">{{ total.toLocaleString() }}</b> 条
        </span>
      </div>

      <div style="overflow-x:auto">
        <table class="tbl">
          <thead>
            <tr>
              <th>短链接</th>
              <th>目标链接</th>
              <th>分组</th>
              <th>状态</th>
              <th>创建时间</th>
              <th style="text-align:right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in filtered" :key="r.code" class="clickable" @click="statsCode = r.code">
              <td>
                <span class="shortlink">
                  <span class="mono">{{ r.shortUrl }}</span>
                  <button
                    class="icon-btn copy-mini"
                    style="width:22px;height:22px"
                    title="复制短链"
                    @click.stop="copyText(r.shortUrl)"
                  >
                    <Icon name="copy" :size="13" />
                  </button>
                </span>
              </td>
              <td style="max-width:230px">
                <span
                  style="display:inline-flex;align-items:center;gap:7px;color:var(--ink-2);font-size:12.5px;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
                  :title="r.title || r.longUrl"
                >
                  <span class="fav">{{ (r.code || '?').slice(0, 1).toUpperCase() }}</span>
                  {{ (r.longUrl || '').replace(/^https?:\/\//, '') }}
                </span>
              </td>
              <td>
                <span class="tag">{{ r.groupName || '未分组' }}</span>
              </td>
              <td>
                <span class="pill" :class="Number(r.status) === 1 ? 'green' : 'gray'">
                  {{ Number(r.status) === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td style="color:var(--ink-3);font-size:12.5px;white-space:nowrap">{{ fmtTime(r.createTime) }}</td>
              <td @click.stop>
                <div style="display:flex;justify-content:flex-end;gap:2px">
                  <button class="icon-btn" title="复制" @click="copyText(r.shortUrl)"><Icon name="copy" :size="14" /></button>
                  <button class="icon-btn" title="二维码" @click="qr = { row: r, anchor: $event.currentTarget }">
                    <Icon name="qr" :size="14" />
                  </button>
                  <button class="icon-btn" title="编辑" @click="editing = r"><Icon name="edit" :size="14" /></button>
                  <button class="icon-btn" title="访问统计" @click="statsCode = r.code"><Icon name="chart" :size="14" /></button>
                  <button class="icon-btn" :title="Number(r.status) === 1 ? '下线' : '上线'" @click="toggleStatus(r)">
                    <Icon :name="Number(r.status) === 1 ? 'eye' : 'refresh'" :size="14" />
                  </button>
                  <button class="icon-btn danger" title="移入回收站" @click="deleting = r"><Icon name="trash" :size="14" /></button>
                </div>
              </td>
            </tr>
            <tr v-if="!filtered.length && !loading">
              <td colspan="6">
                <Empty icon="link" title="没有匹配的短链接" sub="试试调整搜索关键词或筛选条件，或点击右上角新建" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <span style="font-size:12.5px;color:var(--ink-3)">
          第 {{ pageNo }} / {{ pages }} 页 · 共 {{ total.toLocaleString() }} 条
        </span>
        <div style="display:flex;gap:4px">
          <button class="page-btn" :disabled="pageNo <= 1" @click="pageNo--; load()">
            <Icon name="chevLeft" :size="14" />
          </button>
          <button
            v-for="p in pages"
            :key="p"
            class="page-btn"
            :class="{ on: p === pageNo }"
            @click="pageNo = p; load()"
          >
            {{ p }}
          </button>
          <button class="page-btn" :disabled="pageNo >= pages" @click="pageNo++; load()">
            <Icon name="chevRight" :size="14" />
          </button>
        </div>
      </div>
    </div>

    <CreateLinkModal
      v-if="showCreate"
      :groups="groups"
      @close="showCreate = false"
      @created="showCreate = false; pageNo = 1; load(); loadGroups()"
    />
    <EditLinkModal
      v-if="editing"
      :link="editing"
      :groups="groups"
      @close="editing = null"
      @updated="editing = null; load()"
    />
    <QrPopover
      v-if="qr"
      :short-url="qr.row.shortUrl"
      :long-url="qr.row.longUrl"
      :anchor-el="qr.anchor"
      @close="qr = null"
    />
    <LinkStatsModal v-if="statsCode" :code="statsCode" @close="statsCode = ''" />
    <ConfirmModal
      v-if="deleting"
      title="移入回收站"
      :desc="`确定将短链 ${deleting.code} 移入回收站吗？移入后立即停止跳转，30 天内可恢复。`"
      confirm-text="移入回收站"
      @close="deleting = null"
      @confirm="remove"
    />
  </div>
</template>
