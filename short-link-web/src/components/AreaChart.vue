<script setup>
// 平滑面积图：常驻 X/Y 坐标轴与刻度（纵轴数值、横轴时间）、数据点与关键数值直接可见
import { computed } from 'vue';

const props = defineProps({
  data: { type: Array, required: true }, // [{d:'7/23', v:123}]
  height: { type: Number, default: 230 },
});

const W = 720;
const H = computed(() => props.height);
const PAD = { t: 22, r: 14, b: 28, l: 40 };

const max = computed(() => Math.max(...props.data.map((d) => d.v)));
const min = computed(() => Math.min(...props.data.map((d) => d.v)));
const pad = computed(() => (max.value - min.value) * 0.14 || max.value * 0.1 || 1);
const top = computed(() => max.value + pad.value);
const bottom = computed(() => Math.max(0, min.value - pad.value));

const xs = (i) => PAD.l + (i * (W - PAD.l - PAD.r)) / Math.max(1, props.data.length - 1);
const ys = (v) => PAD.t + (1 - (v - bottom.value) / (top.value - bottom.value || 1)) * (H.value - PAD.t - PAD.b);

const pts = computed(() => props.data.map((d, i) => ({ x: xs(i), y: ys(d.v), d })));

const line = computed(() => {
  const p = pts.value;
  if (p.length < 2) {
    return '';
  }
  let path = `M ${p[0].x.toFixed(2)},${p[0].y.toFixed(2)}`;
  for (let i = 0; i < p.length - 1; i++) {
    const p0 = p[i - 1] || p[i];
    const p1 = p[i];
    const p2 = p[i + 1];
    const p3 = p[i + 2] || p2;
    const c1x = p1.x + (p2.x - p0.x) / 6;
    const c1y = p1.y + (p2.y - p0.y) / 6;
    const c2x = p2.x - (p3.x - p1.x) / 6;
    const c2y = p2.y - (p3.y - p1.y) / 6;
    path += ` C ${c1x.toFixed(2)},${c1y.toFixed(2)} ${c2x.toFixed(2)},${c2y.toFixed(2)} ${p2.x.toFixed(2)},${p2.y.toFixed(2)}`;
  }
  return path;
});

const area = computed(() => {
  const p = pts.value;
  if (p.length < 2) {
    return '';
  }
  return `${line.value} L ${p[p.length - 1].x.toFixed(2)},${H.value - PAD.b} L ${p[0].x.toFixed(2)},${H.value - PAD.b} Z`;
});

// 纵轴刻度：整数步长（1/2/5×10^n），避免小数刻度
const yTicks = computed(() => {
  const span = top.value - bottom.value || 1;
  const rawStep = span / 2;
  const pow = Math.pow(10, Math.floor(Math.log10(rawStep)));
  const unit = [1, 2, 5, 10].map((m) => m * pow).find((s) => s >= rawStep);
  const step = Math.max(1, Math.round(unit));
  const first = Math.ceil(bottom.value / step) * step;
  const ticks = [];
  for (let v = first; v <= top.value && ticks.length < 6; v += step) {
    ticks.push({ v, y: ys(v) });
  }
  return ticks;
});

// 横轴时间刻度：数据点 ≤10 个时逐日显示，更多时等距抽样（始终包含首尾）
const xTicks = computed(() => {
  const n = props.data.length;
  if (!n) {
    return [];
  }
  const step = n <= 10 ? 1 : Math.ceil(n / 10);
  return props.data
    .map((d, i) => ({ d: d.d, i }))
    .filter(({ i }) => i % step === 0 || i === n - 1);
});

// 常驻数值标签：约 6 个等距点 + 最后一天，数据点少时全部显示
const vLabels = computed(() => {
  const n = props.data.length;
  if (!n) {
    return [];
  }
  const step = n <= 6 ? 1 : Math.ceil(n / 6);
  const idx = new Set(props.data.map((_, i) => i).filter((i) => i % step === 0));
  idx.add(n - 1);
  return [...idx].sort((a, b) => a - b).map((i) => ({ i, x: pts.value[i].x, y: pts.value[i].y, v: props.data[i].v }));
});
</script>

<template>
  <div>
    <svg :viewBox="`0 0 ${W} ${H}`" style="width:100%;height:auto;display:block">
      <defs>
        <linearGradient id="areaFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#111827" stop-opacity="0.10" />
          <stop offset="1" stop-color="#111827" stop-opacity="0" />
        </linearGradient>
      </defs>

      <!-- 纵轴网格线 + 刻度值（常驻） -->
      <g v-for="t in yTicks" :key="t.v">
        <line :x1="PAD.l" :x2="W - PAD.r" :y1="t.y" :y2="t.y" stroke="#F0F0EC" stroke-dasharray="3 4" />
        <line :x1="PAD.l - 4" :x2="PAD.l" :y1="t.y" :y2="t.y" stroke="#D1D5DB" />
        <text :x="PAD.l - 7" :y="t.y + 3.5" font-size="10.5" fill="#9CA3AF" text-anchor="end">
          {{ Math.round(t.v).toLocaleString() }}
        </text>
      </g>

      <!-- 坐标轴（常驻）：Y 轴竖线 + X 轴横线 -->
      <line :x1="PAD.l" :x2="PAD.l" :y1="PAD.t - 8" :y2="H - PAD.b" stroke="#D1D5DB" stroke-width="1" />
      <line :x1="PAD.l" :x2="W - PAD.r" :y1="H - PAD.b" :y2="H - PAD.b" stroke="#D1D5DB" stroke-width="1" />

      <path :d="area" fill="url(#areaFill)" />
      <path v-if="line" :d="line" fill="none" stroke="#111827" stroke-width="1.8" stroke-linecap="round" />

      <!-- 横轴时间刻度（常驻）：短刻度线 + 日期标签 -->
      <g v-for="t in xTicks" :key="'xt' + t.i">
        <line :x1="xs(t.i)" :x2="xs(t.i)" :y1="H - PAD.b" :y2="H - PAD.b + 4" stroke="#D1D5DB" />
        <text
          :x="t.i === data.length - 1 ? Math.min(xs(t.i), W - PAD.r - 2) : t.i === 0 ? PAD.l + 6 : xs(t.i)"
          :y="H - PAD.b + 16"
          font-size="10.5"
          fill="#9CA3AF"
          :text-anchor="t.i === data.length - 1 ? 'end' : t.i === 0 ? 'start' : 'middle'"
        >
          {{ t.d }}
        </text>
      </g>

      <!-- 常驻数据点：直接可见折线位置 -->
      <circle v-for="(p, i) in pts" :key="'pt' + i" :cx="p.x" :cy="p.y" r="2.6" fill="#111827" stroke="#fff" stroke-width="1.2" />
      <!-- 常驻数值标签 -->
      <g v-for="l in vLabels" :key="'vl' + l.i">
        <text :x="l.i === data.length - 1 ? Math.min(l.x, W - PAD.r - 2) : l.x" :y="l.y - 9" font-size="10.5"
              font-weight="600" fill="#374151" :text-anchor="l.i === data.length - 1 ? 'end' : 'middle'">
          {{ l.v.toLocaleString() }}
        </text>
      </g>
    </svg>
  </div>
</template>
