<script setup lang="ts">
import { watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { ElMessageBox } from 'element-plus'
const props = defineProps<{ modelValue: string }>(),
  emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const { t } = useI18n()
const editor = useEditor({
  extensions: [
    StarterKit.configure({
      link: { openOnClick: false, HTMLAttributes: { rel: 'noopener noreferrer' } },
    }),
  ],
  content: props.modelValue,
  onUpdate: ({ editor }) => emit('update:modelValue', editor.getHTML()),
})
watch(
  () => props.modelValue,
  (value) => {
    if (editor.value && value !== editor.value.getHTML())
      editor.value.commands.setContent(value, { emitUpdate: false })
  },
)
async function link() {
  try {
    const result = await ElMessageBox.prompt(
      t('message.editor.linkHint'),
      t('message.editor.link'),
      {
        inputValue: editor.value?.getAttributes('link').href || '',
        inputPattern: /^$|^https?:\/\/[^\s]+$/i,
        inputErrorMessage: t('message.editor.linkHint'),
      },
    )
    const chain = editor.value?.chain().focus().extendMarkRange('link')
    if (result.value) chain?.setLink({ href: result.value }).run()
    else chain?.unsetLink().run()
  } catch {
    /* Dismissed link dialog. */
  }
}
</script>
<template>
  <div class="rich-editor">
    <div class="rich-toolbar">
      <el-button size="small" @click="editor?.chain().focus().toggleBold().run()">{{
        t('message.editor.bold')
      }}</el-button
      ><el-button size="small" @click="editor?.chain().focus().toggleItalic().run()">{{
        t('message.editor.italic')
      }}</el-button
      ><el-button size="small" @click="editor?.chain().focus().toggleBulletList().run()">{{
        t('message.editor.list')
      }}</el-button
      ><el-button size="small" @click="link">{{ t('message.editor.link') }}</el-button
      ><el-button size="small" @click="editor?.chain().focus().undo().run()">{{
        t('message.editor.undo')
      }}</el-button
      ><el-button size="small" @click="editor?.chain().focus().redo().run()">{{
        t('message.editor.redo')
      }}</el-button>
    </div>
    <EditorContent :editor="editor" />
  </div>
</template>
