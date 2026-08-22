<script setup>
// 访问监控：实时统计（当日 PV/UV、累计 PV）+ 按日归档历史趋势
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { api } from '../api/index.js';
import { copyText } from '../stores/toast.js';
import Icon from '../components/Icon.vue';
import Empty from '../components/Empty.vue';
import AreaChart from '../components/AreaChart.vue';

const route = useRoute();
const links = ref([]);
const code = ref(route.query.code || '');
const detail = ref(null);
const realtime = ref(null);
const history = ref([]);
const range = ref('30');

const loadLinks = async () => {
  const page = await api.pageLinks({ pageNo: 1, pageSize: 100 });
  links.value = page.records;
  if (!code.value && links.value.length) {
    code.value = links.value[0].code;
  }
};

const loadStats = async () => {
  if (!code.value) {
    detail.value = null;
    realtime.value = null;
    history.value = [];
    return;
  }
  try {
    detail.value = await api.linkDetail(code.value);
    realtime.value = detail.value.stats;
    history.value = await api.historyStats(code.value, Number(range.value));
  } catch {
    /* 拦截器已提示 */
  }
};

watch(code, loadStats);
watch(range, loadStats);

// 后端 Long 序列化为字符串（防精度丢失），此处统一转回数值参与计算
const chartData = computed(() =>
  [...history.value]
    .sort((a, b) => String(a.statDate).localeCompare(String(b.statDate)))
    .map((h) => ({ d: String(h.statDate).slice(5), v: Number(h.pv) || 0 })),
);

const historyPv = computed(() => chartData.value.reduce((s, d) => s + d.v, 0));

const stats = computed(() => [
  { label: '今日点击', value: Number(realtime.value?.todayPv) || 0, note: '实时' },
  { label: '今日访客', value: Number(realtime.value?.todayUv) || 0, note: 'HyperLogLog 去重' },
  { label: '累计点击', value: Number(realtime.value?.totalPv) || 0, note: '自创建起' },
  { label: '历史归档天数', value: history.value.length, note: range.value === '30' ? '近 30 天' : '近 7 天' },
]);

onMounted(async () => {
  try {
    await loadLinks();
    await loadStats();
  } catch {
    /* 拦截器已提示 */
  }
});
</script>

<template>
  <div class="page">
    <div class="page-head" style="align-items:center">
      <div>
        <div class="page-title">访问监控</div>
        <div class="page-sub">选择短链查看实时与历史访问数据</div>
      </div>
      <div class="head-actions" style="align-items:center">
        <div style="display:flex;align-items:center;gap:8px">
          <Icon name="filter" :size="14" style="color:var(--ink-3)" />
          <select v-model="code" class="select" style="width:240px">
            <option v-for="l in links" :key="l.code" :value="l.code" class="mono">{{ l.shortUrl }}</option>
          </select>
        </div>
      </div>
    </div>

    <template v-if="detail">
      <div class="card" style="padding:15px 20px;display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap">
        <span class="mono" style="font-size:16px;font-weight:700">{{ detail.shortUrl }}</span>
        <button class="icon-btn" title="复制短链" @click="copyText(detail.shortUrl)"><Icon name="copy" :size="15" /></button>
        <span class="tag">{{ detail.groupName || '未分组' }}</span>
        <span class="pill" :class="detail.status === 1 ? 'green' : 'gray'">{{ detail.status === 1 ? '启用' : '停用' }}</span>
        <span style="font-size:12.5px;color:var(--ink-3)">创建于 {{ String(detail.createTime).slice(0, 10) }}</span>
        <div style="flex:1" />
        <div style="text-align:right">
          <div style="font-size:11.5px;color:var(--ink-3);margin-bottom:2px">目标链接</div>
          <a
            :href="detail.longUrl"
            target="_blank"
            rel="noreferrer"
            style="font-size:12.5px;color:var(--ink-2);text-decoration:none;display:inline-flex;align-items:center;gap:5px;max-width:280px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
          >
            {{ detail.longUrl.replace(/^https?:\/\//, '') }}<Icon name="external" :size="12" />
          </a>
        </div>
      </div>

      <div class="grid4">
        <div v-for="s in stats" :key="s.label" class="card" style="padding:18px 20px">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value tnum" style="font-size:24px">{{ s.value.toLocaleString() }}</div>
          <div class="stat-foot"><span>{{ s.note }}</span></div>
        </div>
      </div>

      <div style="height:16px" />
      <div class="card">
        <div class="card-head">
          <div>
            <div class="card-title">点击趋势</div>
            <div class="card-sub">按日归档（每小时汇总入库），共 {{ historyPv.toLocaleString() }} 次点击</div>
          </div>
          <div class="seg">
            <button :class="{ on: range === '7' }" @click="range = '7'">近 7 天</button>
            <button :class="{ on: range === '30' }" @click="range = '30'">近 30 天</button>
          </div>
        </div>
        <div class="card-body" style="padding-top:10px">
          <AreaChart v-if="chartData.length" :data="chartData" :height="214" />
          <Empty
            v-else
            icon="chart"
            title="暂无归档数据"
            sub="统计数据每小时归档一次，稍后再来看看；今日实时数据见上方统计卡"
          />
        </div>
      </div>
    </template>

    <div v-else class="card">
      <Empty icon="chart" title="暂无短链可监控" sub="先创建一条短链，获得点击后即可查看访问数据" />
    </div>
  </div>
</template>
