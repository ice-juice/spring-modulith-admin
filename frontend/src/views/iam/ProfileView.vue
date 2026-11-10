<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import { get, put } from '@/api/http'
import { useAuth } from '@/stores/auth'
import { upload } from '@/features/file/files'
import AvatarImage from '@/components/AvatarImage.vue'
interface Profile {
  id: string
  username: string
  name: string
  email: string | null
  mobile: string | null
  gender: string | null
  avatarFileId: string | null
}
const { t } = useI18n(),
  auth = useAuth()
const form = reactive<Profile>({
  id: '',
  username: '',
  name: '',
  email: null,
  mobile: null,
  gender: null,
  avatarFileId: null,
})
const busy = ref(false),
  uploading = ref(false),
  error = ref('')
onMounted(async () => {
  try {
    Object.assign(form, await get('/iam/profile'))
  } catch (e) {
    error.value = (e as Error).message
  }
})
async function image(options: UploadRequestOptions) {
  uploading.value = true
  error.value = ''
  try {
    form.avatarFileId = (await upload(options.file, true)).id
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    uploading.value = false
  }
}
async function save() {
  if (!form.name.trim()) {
    error.value = t('iam.profileNameRequired')
    return
  }
  busy.value = true
  error.value = ''
  try {
    await put('/iam/profile', {
      name: form.name,
      email: form.email || null,
      mobile: form.mobile || null,
      gender: form.gender || null,
      avatarFileId: form.avatarFileId,
    })
    await auth.load(false)
    ElMessage.success(t('common.saved'))
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <el-card shadow="never" style="max-width: 760px">
    <h2>{{ t('common.profile') }}</h2>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-width="150px" @submit.prevent="save">
      <el-form-item :label="t('iam.avatar')"
        ><AvatarImage :file-id="form.avatarFileId" :size="72" /><el-upload
          :show-file-list="false"
          :http-request="image"
          accept="image/png,image/jpeg,image/gif"
          ><el-button :loading="uploading">{{ t('iam.uploadAvatar') }}</el-button></el-upload
        ><el-button v-if="form.avatarFileId" text @click="form.avatarFileId = null">{{
          t('iam.removeAvatar')
        }}</el-button></el-form-item
      >
      <el-form-item :label="t('iam.fields.username')"
        ><el-input v-model="form.username" disabled
      /></el-form-item>
      <el-form-item :label="t('common.name')" required
        ><el-input v-model="form.name" maxlength="128"
      /></el-form-item>
      <template v-if="auth.user?.features?.extendedProfileEnabled !== false">
        <el-form-item :label="t('iam.fields.email')"
          ><el-input v-model="form.email" type="email" maxlength="254"
        /></el-form-item>
        <el-form-item :label="t('iam.fields.mobile')"
          ><el-input v-model="form.mobile" maxlength="32"
        /></el-form-item>
        <el-form-item :label="t('iam.fields.gender')"
          ><el-select v-model="form.gender" clearable
            ><el-option
              v-for="value in ['MALE', 'FEMALE', 'UNSPECIFIED']"
              :key="value"
              :value="value"
              :label="t('iam.choices.' + value)" /></el-select
        ></el-form-item>
      </template>
      <el-form-item
        ><el-button type="primary" native-type="submit" :loading="busy" :disabled="uploading">{{
          t('common.save')
        }}</el-button></el-form-item
      >
    </el-form>
  </el-card>
</template>
