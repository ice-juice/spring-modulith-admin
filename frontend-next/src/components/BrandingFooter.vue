<script setup lang="ts">
import { useBranding } from '@/stores/branding'
import TenantBrandFooter from '@/components/tenant/TenantBrandFooter.vue'
const branding = useBranding()
</script>
<template><TenantBrandFooter :name="branding.name" :legal="branding.entry?.branding.legal" :links="branding.entry?.branding.links" /></template>
