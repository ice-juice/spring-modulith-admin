<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { Bold, Italic, List, Link, Undo2, Redo2 } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import EditorDialog from '@/components/business/EditorDialog.vue'
import ErrorNotice from '@/components/business/ErrorNotice.vue'
const content = defineModel<string>({ default: '' }), { t, locale } = useI18n(), linkOpen = ref(false), linkValue = ref(''), error = ref('')
const editorAttributes = () => ({ class: 'min-h-40 px-4 py-3 outline-none text-sm', role: 'textbox', 'aria-multiline': 'true', 'aria-label': t('message.content') })
const editor = useEditor({ extensions: [StarterKit.configure({ link: { openOnClick: false, HTMLAttributes: { rel: 'noopener noreferrer' } } })], content: content.value, editorProps: { attributes: editorAttributes() }, onUpdate: ({ editor }) => { content.value = editor.getHTML() } })
watch(locale, () => editor.value?.setOptions({ editorProps: { attributes: editorAttributes() } }))
watch(content, value => { if (editor.value && value !== editor.value.getHTML()) editor.value.commands.setContent(value, { emitUpdate: false }) })
function link() { linkValue.value = editor.value?.getAttributes('link').href || ''; error.value = ''; linkOpen.value = true }
function saveLink() { const value = linkValue.value.trim(); if (value && !/^https?:\/\/[^\s]+$/i.test(value)) { error.value = t('message.editor.linkHint'); return }; const chain = editor.value?.chain().focus().extendMarkRange('link'); if (value) chain?.setLink({ href: value }).run(); else chain?.unsetLink().run(); linkOpen.value = false }
</script>
<template><div class="overflow-hidden rounded-md border bg-background"><div class="flex flex-wrap gap-1 border-b bg-muted/40 p-1.5"><Button type="button" variant="ghost" size="icon-sm" :aria-label="t('message.editor.bold')" :aria-pressed="editor?.isActive('bold')" :class="{ 'bg-accent': editor?.isActive('bold') }" @mousedown.prevent @click="editor?.chain().focus().toggleBold().run()"><Bold class="size-4" /></Button><Button type="button" variant="ghost" size="icon-sm" :aria-label="t('message.editor.italic')" :aria-pressed="editor?.isActive('italic')" @mousedown.prevent @click="editor?.chain().focus().toggleItalic().run()"><Italic class="size-4" /></Button><Button type="button" variant="ghost" size="icon-sm" :aria-label="t('message.editor.list')" :aria-pressed="editor?.isActive('bulletList')" @mousedown.prevent @click="editor?.chain().focus().toggleBulletList().run()"><List class="size-4" /></Button><Button type="button" variant="ghost" size="icon-sm" :aria-label="t('message.editor.link')" @mousedown.prevent @click="link"><Link class="size-4" /></Button><Button type="button" variant="ghost" size="icon-sm" :aria-label="t('message.editor.undo')" @mousedown.prevent @click="editor?.chain().focus().undo().run()"><Undo2 class="size-4" /></Button><Button type="button" variant="ghost" size="icon-sm" :aria-label="t('message.editor.redo')" @mousedown.prevent @click="editor?.chain().focus().redo().run()"><Redo2 class="size-4" /></Button></div><EditorContent :editor="editor" class="rich-content" /></div><EditorDialog v-model="linkOpen" :title="t('message.editor.link')" @save="saveLink"><ErrorNotice :message="error" /><div class="space-y-2"><Label for="editor-link">{{ t('message.editor.linkHint') }}</Label><Input id="editor-link" v-model="linkValue" type="url" @keyup.enter="saveLink" /></div></EditorDialog></template>
