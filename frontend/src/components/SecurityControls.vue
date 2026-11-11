<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuth } from '@/stores/auth'
import { post, put } from '@/api/http'
import { validPassword } from '@/lib/password'
const { t } = useI18n()
const auth = useAuth()
const router = useRouter()
const locked = computed(() => auth.user?.locked === true)
const unlockPassword = ref('')
const error = ref('')
const busy = ref(false)
const passwordDialog = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
function openPasswordDialog() {
  passwordDialog.value = true
  error.value = ''
}
async function lock() {
  try {
    await post('/auth/lock')
    if (auth.user) auth.user.locked = true
  } catch (e) {
    error.value = (e as Error).message
  }
}
async function unlock() {
  busy.value = true
  error.value = ''
  try {
    await post('/auth/unlock', { password: unlockPassword.value })
    unlockPassword.value = ''
    if (auth.user) auth.user.locked = false
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
async function logout() {
  await auth.logout()
  await router.replace('/login')
}
async function changePassword() {
  error.value = ''
  if (!validPassword(newPassword.value)) { error.value = t('iam.passwordRule'); return }
  if (newPassword.value !== confirmPassword.value) {
    error.value = t('auth.passwordMismatch')
    return
  }
  busy.value = true
  try {
    await put('/iam/users/me/password', {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    })
    oldPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    passwordDialog.value = false
    ElMessage.success(t('auth.passwordChanged'))
    auth.reset()
    await router.replace('/login')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <el-button text @click="lock">{{ t('auth.lock') }}</el-button>
  <el-button text @click="openPasswordDialog">{{ t('auth.changePassword') }}</el-button>
  <el-dialog v-model="passwordDialog" :title="t('auth.changePassword')" width="450px">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top">
      <el-form-item :label="t('auth.oldPassword')"
        ><el-input
          v-model="oldPassword"
          type="password"
          show-password
          autocomplete="current-password"
      /></el-form-item>
      <el-form-item :label="t('auth.newPassword')"
        ><el-input v-model="newPassword" type="password" show-password autocomplete="new-password"
      /></el-form-item>
      <el-form-item :label="t('auth.confirmPassword')"
        ><el-input
          v-model="confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="passwordDialog = false">{{ t('common.cancel') }}</el-button
      ><el-button type="primary" :loading="busy" @click="changePassword">{{
        t('common.save')
      }}</el-button></template
    >
  </el-dialog>
  <Teleport to="body">
    <div v-if="locked" class="screen-lock">
      <el-card class="lock-card" shadow="never">
        <h2>{{ t('auth.locked') }}</h2>
        <p>{{ auth.user?.name }}</p>
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-input
          v-model="unlockPassword"
          type="password"
          show-password
          :placeholder="t('auth.password')"
          autocomplete="current-password"
          @keyup.enter="unlock"
        />
        <div class="toolbar" style="margin-top: 20px">
          <el-button type="primary" :loading="busy" @click="unlock">{{
            t('auth.unlock')
          }}</el-button
          ><el-button @click="logout">{{ t('common.signOut') }}</el-button>
        </div>
      </el-card>
    </div>
  </Teleport>
</template>
