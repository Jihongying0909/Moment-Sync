<script setup>
defineProps({
  size: {
    type: Number,
    default: 46,
  },
})
</script>

<template>
  <span
    class="brand-logo"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
      <!-- 图标内轻微暖光底晕 -->
      <circle cx="33" cy="33" r="22" fill="#f2e4ad" opacity="0.32" />

      <!-- 鼠尾草绿硬偏移纸张投影 -->
      <rect
        class="brand-logo__shadow"
        x="14"
        y="19"
        width="31"
        height="34"
        rx="6"
        transform="rotate(-8 14 19)"
        fill="#d8decf"
      />

      <!-- 微倾拍立得手账卡片 -->
      <g class="brand-logo__card">
        <rect
          x="16"
          y="16"
          width="31"
          height="34"
          rx="5.5"
          transform="rotate(-8 16 16)"
          fill="#fffdf6"
          stroke="#4d5146"
          stroke-width="2.4"
          stroke-linejoin="round"
        />
        <!-- 卡片内暖阳天空与鼠尾草山丘 -->
        <g transform="rotate(-8 16 16)">
          <clipPath id="moment-photo-clip">
            <rect x="20.5" y="20.5" width="22" height="19.5" rx="2" />
          </clipPath>
          <g clip-path="url(#moment-photo-clip)">
            <rect x="20.5" y="20.5" width="22" height="19.5" fill="#f2e4ad" />
            <circle class="brand-logo__sun" cx="27" cy="26" r="2.4" fill="#e5987e" />
            <path d="M19 35.5 Q25.5 30.5 32 34.8 T44 33 L44 42 L19 42 Z" fill="#dce7cf" stroke="#4d5146" stroke-width="1.7" />
            <path d="M27 36.5 Q34 29.8 44 31.5 L44 42 L27 42 Z" fill="#c7d8b6" stroke="#4d5146" stroke-width="1.7" />
          </g>
          <rect
            x="20.5"
            y="20.5"
            width="22"
            height="19.5"
            rx="2"
            stroke="#4d5146"
            stroke-width="2"
          />
        </g>
      </g>

      <!-- 主四芒星（暖阳黄） -->
      <path
        class="brand-logo__sparkle brand-logo__sparkle--main"
        d="M42.5 9.5 L44.4 14.4 L49.3 16.3 L44.4 18.2 L42.5 23.1 L40.6 18.2 L35.7 16.3 L40.6 14.4 Z"
        fill="#f2e4ad"
        stroke="#4d5146"
        stroke-width="2"
        stroke-linejoin="round"
      />
      <!-- 次四芒星（浅陶土） -->
      <path
        class="brand-logo__sparkle brand-logo__sparkle--sub"
        d="M49.5 19.8 L50.8 22.8 L53.8 24.1 L50.8 25.4 L49.5 28.4 L48.2 25.4 L45.2 24.1 L48.2 22.8 Z"
        fill="#e5987e"
        stroke="#4d5146"
        stroke-width="1.8"
        stroke-linejoin="round"
      />

      <!-- 同频共振波纹 -->
      <path
        class="brand-logo__wave brand-logo__wave--1"
        d="M48.5 11.5 A7.5 7.5 0 0 1 55 18"
        stroke="#4d5146"
        stroke-width="2"
        stroke-linecap="round"
      />
      <path
        class="brand-logo__wave brand-logo__wave--2"
        d="M50.8 8 A11.5 11.5 0 0 1 58.2 16.5"
        stroke="#4d5146"
        stroke-width="1.8"
        stroke-linecap="round"
      />
    </svg>
  </span>
</template>
