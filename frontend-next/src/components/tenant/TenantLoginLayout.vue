<script setup lang="ts">
import { computed } from 'vue'
import SoftlightArtwork from '@/components/business/SoftlightArtwork.vue'
const props = defineProps<{
  template?: 'login1' | 'login2' | 'login3'; name: string; title?: string; description?: string
  logo?: string; background?: string; blur?: boolean; accent?: string; preview?: boolean
}>()
const layout = computed(() => props.template || 'login1')
const decoration = computed(() => props.accent && /^#[a-fA-F0-9]{6}$/.test(props.accent) ? { '--brand-accent': props.accent } : undefined)
const imageStyle = computed(() => ({ backgroundImage: props.background ? 'url(' + JSON.stringify(props.background) + ')' : undefined, filter: props.blur ? 'blur(8px)' : undefined }))
</script>
<template>
  <div class="tenant-login-scope" :class="{ 'is-preview': preview }">
    <div class="tenant-login" :class="layout" :data-login-template="layout" :style="decoration">
      <div v-if="background && layout !== 'login1'" class="screen-image" :style="imageStyle" />
      <div class="tenant-login-tools"><slot name="tools" /></div>
      <section v-if="layout !== 'login2'" class="tenant-login-story">
        <div v-if="background && layout === 'login1'" class="story-image" :style="imageStyle" />
        <div class="tenant-login-wordmark"><img v-if="logo" :src="logo" :alt="name" class="tenant-login-logo" /><span>{{ name }}</span></div>
        <div class="tenant-login-copy"><span class="tenant-login-accent" aria-hidden="true" /><h1>{{ title || name }}</h1><p v-if="description">{{ description }}</p></div>
        <SoftlightArtwork v-if="layout === 'login1' && !background" large />
      </section>
      <main class="tenant-login-main">
        <div class="tenant-login-panel">
          <header v-if="layout === 'login2'" class="tenant-login-centered-brand"><img v-if="logo" :src="logo" :alt="name" class="tenant-login-logo" /><h1>{{ title || name }}</h1><p v-if="description">{{ description }}</p></header>
          <slot />
          <div class="tenant-login-footer"><slot name="footer" /></div>
        </div>
      </main>
    </div>
  </div>
</template>
<style scoped>
.tenant-login-scope { container-type: inline-size; width: 100%; }
.tenant-login { --brand-accent: var(--primary); position: relative; isolation: isolate; display: grid; min-height: 100dvh; color: var(--foreground); background: var(--background); overflow: clip; }
.is-preview .tenant-login { min-height: 32rem; }
.tenant-login-tools { position: absolute; z-index: 4; top: 1rem; right: 1rem; max-width: calc(100% - 2rem); }
.tenant-login-story { position: relative; overflow: hidden; display: flex; flex-direction: column; gap: 2rem; min-width: 0; padding: 5rem 1.5rem 2rem; background: color-mix(in srgb, var(--brand-accent) 9%, var(--background)); }
.tenant-login-wordmark { position: relative; z-index: 2; display: flex; align-items: center; gap: .75rem; overflow-wrap: anywhere; font-weight: 650; }
.tenant-login-logo { max-width: 10rem; height: 2.5rem; object-fit: contain; flex: 0 1 auto; }
.tenant-login-copy { position: relative; z-index: 2; max-width: 34rem; }
.tenant-login-copy h1 { margin: .9rem 0 0; font-size: clamp(1.8rem, 4cqi, 3rem); font-weight: 650; line-height: 1.3; overflow-wrap: anywhere; white-space: pre-line; }
.tenant-login-copy p, .tenant-login-centered-brand p { margin-top: 1rem; line-height: 1.8; font-size: .95rem; overflow-wrap: anywhere; white-space: pre-line; }
.tenant-login-accent { display: block; width: 2.5rem; height: .25rem; border-radius: 1rem; background: var(--brand-accent); }
.tenant-login-main { position: relative; z-index: 2; display: flex; align-items: center; justify-content: center; min-width: 0; padding: 2rem 1.25rem; }
.tenant-login-panel { width: 100%; max-width: 25rem; min-width: 0; }
.tenant-login-footer { margin-top: 2.5rem; }
.story-image, .screen-image { position: absolute; inset: -12px; background-position: center; background-size: cover; pointer-events: none; }
.story-image { opacity: .15; }
.login1 .tenant-login-main { background: var(--card); }
.login1 .softlight-art { display: none; }
.login2 { place-items: center; padding: 5rem 1rem 2rem; background: radial-gradient(ellipse at 15% 10%, color-mix(in srgb, var(--brand-accent) 15%, transparent), transparent 58%), var(--background); }
.login2 .screen-image { opacity: .12; }
.login2 .tenant-login-main { width: 100%; padding: 0; }
.login2 .tenant-login-panel, .login3 .tenant-login-panel { max-width: 29rem; padding: clamp(1.25rem, 4cqi, 2.5rem); border: 1px solid var(--border); border-radius: 1.5rem; background: var(--card); box-shadow: var(--shadow-floating); }
.tenant-login-centered-brand { margin-bottom: 2rem; text-align: center; }
.tenant-login-centered-brand .tenant-login-logo { margin: 0 auto 1rem; }
.tenant-login-centered-brand h1 { font-size: 1.5rem; font-weight: 650; overflow-wrap: anywhere; }
.login3 { background: linear-gradient(135deg, #142536, #243c50); }
.login3::before { position: absolute; z-index: 1; inset: 0; content: ''; background: linear-gradient(100deg, #101827db, #101827b3); pointer-events: none; }
.login3 .tenant-login-story { z-index: 2; color: #f9fafb; background: transparent; }
.login3 .tenant-login-tools { padding: .2rem; border-radius: .75rem; background: var(--card); }
@container (min-width: 64rem) {
  .login1, .login3 { grid-template-columns: 1.05fr 1fr; }
  .tenant-login-story { min-height: 100dvh; padding: 3rem; gap: 4rem; }
  .is-preview .tenant-login-story { min-height: 32rem; }
  .tenant-login-copy { margin: 3rem 0 14rem; }
  .login1 .softlight-art { display: block; }
  .tenant-login-main { padding: 6rem 3rem 3rem; }
  .login3 .tenant-login-story { justify-content: center; gap: 3rem; }
  .login3 .tenant-login-copy { margin: 0; }
}
</style>
