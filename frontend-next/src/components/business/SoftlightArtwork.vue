<script setup lang="ts">
defineProps<{ large?: boolean }>()
</script>
<template><div class="softlight-art" :class="{ 'softlight-art-large': large }" aria-hidden="true"><div class="softlight-orbit" /><div class="softlight-orbital" /><div class="softlight-orbital second" /><div class="softlight-orbital third" /><div class="softlight-core" /><div class="softlight-art-tile"><span /><span /><span /></div></div></template>
