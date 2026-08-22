<script setup>
// 极简平滑面积图（移植自原型：无网格线、渐变填充、悬浮提示）
import { computed, ref } from 'vue';

const props = defineProps({
  data: { type: Array, required: true }, // [{d:'7/23', v:123}]
  height: { type: Number, default: 230 },
});

const hover = ref(null);
const W = 720;
const H = computed(() => props.height);
const PAD = { t: 16, r: 8, b: 22, l: 4 };

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
  if (!p.length) {
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
  if (!p.length) {
    return '';
  }
  return `${line.value} L ${p[p.length - 1].x.toFixed(2)},${H.value - PAD.b} L ${p[0].x.toFixed(2)},${H.value - PAD.b} Z`;
});

const yTicks = computed(() => [
  { v: top.value, y: ys(top.value) },
  { v: (top.value + bottom.value) / 2, y: ys((top.value + bottom.value) / 2) },
  { v: bottom.value, y: ys(bottom.value) },
]);

const xLabels = computed(() => {
  const step = Math.max(1, Math.ceil(props.data.length / 8));
  return props.data.map((d, i) => ({ d, i })).filter(({ i }) => i % step === 0 || i === props.data.length - 1);
});

const onMove = (e) => {
  const rect = e.currentTarget.getBoundingClientRect();
  const x = ((e.clientX - rect.left) / rect.width) * W;
  let nearest = 0;
  let dist = Infinity;
  pts.value.forEach((p, i) => {
    const dd = Math.abs(p.x - x);
    if (dd < dist) {
      dist = dd;
      nearest = i;
    }
  });
  hover.value = nearest;
};
</script>

<template>
  <div style="position:relative">
    <svg
      :viewBox="`0 0 ${W} ${H}`"
      style="width:100%;height:auto;display:block"
      @mousemove="onMove"
      @mouseleave="hover = null"
    >
      <defs>
        <linearGradient id="areaFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#111827" stop-opacity="0.10" />
          <stop offset="1" stop-color="#111827" stop-opacity="0" />
        </linearGradient>
      </defs>
      <g v-for="t in yTicks" :key="t.v">
        <line :x1="PAD.l" :x2="W - PAD.r" :y1="t.y" :y2="t.y" stroke="#F0F0EC" stroke-dasharray="3 4" />
        <text :x="PAD.l + 2" :y="t.y - 5" font-size="10.5" fill="#9CA3AF">{{ Math.round(t.v).toLocaleString() }}</text>
      </g>
      <path :d="area" fill="url(#areaFill)" />
      <path :d="line" fill="none" stroke="#111827" stroke-width="1.8" stroke-linecap="round" />
      <g v-for="l in xLabels" :key="l.i">
        <text :x="l.i === data.length - 1 ? W - PAD.r : xs(l.i)" :y="H - 6" font-size="10.5" fill="#9CA3AF"
              :text-anchor="l.i === data.length - 1 ? 'end' : 'middle'">{{ l.d.d }}</text>
      </g>
      <g v-if="hover !== null">
        <line :x1="pts[hover].x" :x2="pts[hover].x" :y1="PAD.t - 6" :y2="H - PAD.b" stroke="#D1D5DB" />
        <circle :cx="pts[hover].x" :cy="pts[hover].y" r="3.6" fill="#111827" stroke="#fff" stroke-width="1.6" />
      </g>
    </svg>
    <div
      v-if="hover !== null"
      class="tooltip-badge"
      :style="{ left: `${(pts[hover].x / W) * 100}%`, top: `${(pts[hover].y / H) * 100}%` }"
    >
      {{ pts[hover].d.d }} · {{ pts[hover].d.v.toLocaleString() }} 次
    </div>
  </div>
</template>
