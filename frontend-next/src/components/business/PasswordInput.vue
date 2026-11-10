<script setup lang="ts">
import { ref } from 'vue'
import { Eye, EyeOff } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
defineOptions({ inheritAttrs: false })
const value = defineModel<string>({ default: '' }), visible = ref(false), { t } = useI18n()
</script>
<template><div class="relative"><Input v-bind="$attrs" v-model="value" :type="visible ? 'text' : 'password'" class="pr-11" /><Button type="button" variant="ghost" size="icon-sm" class="absolute top-0.5 right-1" :aria-label="t(visible ? 'common.passwordHidden' : 'common.passwordVisible')" :aria-pressed="visible" @click="visible = !visible"><EyeOff v-if="visible" class="size-4" /><Eye v-else class="size-4" /></Button></div></template>
