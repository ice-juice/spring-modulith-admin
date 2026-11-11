<script setup lang="ts">
import { useRoute } from "vue-router";
import { useAuth } from "@/stores/auth";
import TemplatePage from "./TemplatePage.vue";
const route = useRoute(),
  auth = useAuth();
</script>
<template>
  <TemplatePage
    :key="
      String(route.params.id || route.params.sceneId) +
      ':' +
      auth.user?.portal +
      ':' +
      auth.user?.id
    "
  />
</template>
