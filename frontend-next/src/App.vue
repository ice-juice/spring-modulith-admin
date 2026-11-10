<script setup lang="ts">
import { TooltipProvider } from '@/components/ui/tooltip'
import FeedbackHost from '@/components/business/FeedbackHost.vue'
</script>
<template><TooltipProvider><RouterView /><FeedbackHost /></TooltipProvider></template>
