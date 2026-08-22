<script setup>
// 访问统计弹窗：在短链列表内查看单条短链的实时数据与历史趋势
import { computed, onMounted, ref, watch } from 'vue';
import { api } from '../api/index.js';
import { copyText } from '../stores/toast.js';
import Modal from './Modal.vue';
import Icon from './Icon.vue';
import Empty from './Empty.vue';
import AreaChart from './AreaChart.vue';

const props = defineProps({
  code: { type: String, required: true },
});
const emit = defineEmits(['close']);

const detail = ref(null);
const history = ref([]);
const range = ref('30');

const load = async () => {
  try {
    detail.value = await api.linkDetail(props.code);
    history.value = await api.historyStats(props.code, Number(range.value));
  } catch {
    /* 拦截器已提示 */
  }
};

watch(range, () => {
  api.historyStats(props.code, Number(range.value)).then((h) => {
    history.value = h;
  }).catch(() => {});
});

onMounted(load);

// 后端 Long 序列化为字符串（防精度丢失），此处统一转回数值参与计算
const chartData = computed(() =>
  [...history.value]
    .sort((a, b) => String(a.statDate).localeCompare(String(b.statDate)))
    .map((h) => ({ d: String(h.statDate).slice(5), v: Number(h.pv) || 0 })),
);

const historyPv = computed(() => chartData.value.reduce((s, d) => s + d.v, 0));

const stats = computed(() => [
  { label: '今日点击', value: Number(detail.value?.stats?.todayPv) || 0, note: '实时' },
  { label: '今日访客', value: Number(detail.value?.stats?.todayUv) || 0, note: 'UV 去重' },
  { label: '累计点击', value: Number(detail.value?.stats?.totalPv) || 0, note: '自创建起' },
  { label: '归档点击', value: historyPv.value, note: range.value === '30' ? '近 30 天' : '近 7 天' },
]);
</script>

<template>
  <Modal title="访问统计" :width="640" @close="emit('close')">
    <template v-if="detail">
      <div
        style="display:flex;align-items:center;gap:10px;padding:11px 14px;background:#FAFAF8;border:1px solid var(--hair);border-radius:10px;flex-wrap:wrap"
      >
        <span class="mono" style="font-size:14.5px;font-weight:700">{{ detail.shortUrl }}</span>
        <button class="icon-btn" style="width:24px;height:24px" title="复制短链" @click="copyText(detail.shortUrl)">
          <Icon name="copy" :size="13" />
        </button>
        <span class="tag">{{ detail.groupName || '未分组' }}</span>
        <span class="pill" :class="Number(detail.status) === 1 ? 'green' : 'gray'">
          {{ Number(detail.status) === 1 ? '启用' : '停用' }}
        </span>
        <span style="font-size:12px;color:var(--ink-3)">创建于 {{ String(detail.createTime).slice(0, 10) }}</span>
        <div style="flex:1" />
        <a
          :href="detail.longUrl"
          target="_blank"
          rel="noreferrer"
          style="font-size:12px;color:var(--ink-2);text-decoration:none;display:inline-flex;align-items:center;gap:5px;max-width:230px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"
        >
          {{ detail.longUrl.replace(/^https?:\/\//, '') }}<Icon name="external" :size="12" />
        </a>
      </div>

      <div class="grid4" style="margin-top:14px">
        <div v-for="s in stats" :key="s.label" style="padding:13px 15px;border:1px solid var(--hair);border-radius:10px">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value tnum" style="font-size:21px">{{ s.value.toLocaleString() }}</div>
          <div style="font-size:11px;color:var(--ink-3);margin-top:3px">{{ s.note }}</div>
        </div>
      </div>

      <div style="height:14px" />
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
        <div style="font-size:13px;font-weight:600">点击趋势</div>
        <div class="seg">
          <button :class="{ on: range === '7' }" @click="range = '7'">近 7 天</button>
          <button :class="{ on: range === '30' }" @click="range = '30'">近 30 天</button>
        </div>
      </div>
      <AreaChart v-if="chartData.length" :data="chartData" :height="190" />
      <div v-else style="padding:26px 0 8px">
        <Empty icon="chart" title="暂无归档数据" sub="统计数据每小时归档一次；今日实时数据见上方统计卡" />
      </div>
    </template>
    <div v-else style="padding:60px 0;text-align:center;font-size:13px;color:var(--ink-3)">加载中…</div>
  </Modal>
</template>
