<script setup>
// 图标集：lucide 风格 SVG path（移植自原型）
const PATHS = {
  dashboard: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  link: 'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71',
  folder: 'M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z',
  globe: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20ZM2 12h20M12 2a14.5 14.5 0 0 1 0 20 14.5 14.5 0 0 1 0-20',
  chart: 'M3 3v18h18M7 15l4-4 3 3 5-6',
  trash: 'M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M10 11v6M14 11v6',
  sliders: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6',
  search: 'M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16ZM21 21l-4.3-4.3',
  plus: 'M12 5v14M5 12h14',
  copy: 'M11 9h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2ZM5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1',
  qr: 'M4 3h7v7H4zM13 3h7v7h-7zM4 13h7v7H4zM13 13h3v3h-3zM19 16h1v1h-1zM13 19h1v1h-1zM17 19h3v1h-3zM20 13h1',
  edit: 'M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z',
  more: 'M12 4a1 1 0 1 0 0 2 1 1 0 0 0 0-2ZM12 11a1 1 0 1 0 0 2 1 1 0 0 0 0-2ZM12 18a1 1 0 1 0 0 2 1 1 0 0 0 0-2Z',
  external: 'M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6M15 3h6v6M10 14 21 3',
  arrowRight: 'M5 12h14M12 5l7 7-7 7',
  chevDown: 'm6 9 6 6 6-6',
  chevLeft: 'm15 18-6-6 6-6',
  chevRight: 'm9 18 6-6-6-6',
  check: 'M20 6 9 17l-5-5',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  key: 'M7.5 10a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11ZM21 2l-9.6 9.6M15.5 7.5l3 3L22 7l-3-3',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  shield: 'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z',
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7ZM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z',
  calendar: 'M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2ZM16 2v4M8 2v4M3 10h18',
  filter: 'M22 3H2l8 9.46V19l4 2v-8.54Z',
  refresh: 'M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16M3 21v-5h5',
  x: 'M18 6 6 18M6 6l12 12',
  alert: 'M21.73 18 13.73 4a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3ZM12 9v4M12 17h.01',
  info: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20ZM12 16v-4M12 8h.01',
  clock: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20ZM12 6v6l4 2',
  arrowUpRight: 'M7 17 17 7M7 7h10v10',
  send: 'm22 2-7 20-4-9-9-4ZM22 2 11 13',
};

defineProps({
  name: { type: String, required: true },
  size: { type: Number, default: 16 },
  sw: { type: Number, default: 1.6 },
});
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="sw"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path :d="PATHS[name] || ''" />
  </svg>
</template>
