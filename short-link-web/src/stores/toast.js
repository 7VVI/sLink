import { reactive } from 'vue';

const state = reactive({ message: '', type: 'ok', timer: null });

export function toast(message, type = 'ok') {
  state.message = message;
  state.type = type;
  if (state.timer) {
    clearTimeout(state.timer);
  }
  state.timer = setTimeout(() => {
    state.message = '';
  }, 2400);
}

export function useToast() {
  return state;
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    toast('已复制到剪贴板');
  } catch {
    toast('复制失败，请手动复制', 'err');
  }
}
