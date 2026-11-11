<script setup lang="ts">
import { computed, inject, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { editor, languages } from "monaco-editor/editor/editor.api.js";
import "monaco-editor/editor/contrib/suggest/browser/suggestController.js";
import { retainEditorWorkers } from "../editorWorkers";
import { usePreferences } from "@/stores/preferences";
import { ruleReferencesKey } from "../references";
const props = withDefaults(
  defineProps<{
    label: string;
    language?: "rule" | "json";
    readonly?: boolean;
    line?: number | null;
    column?: number | null;
  }>(),
  { language: "rule" },
);
const value = defineModel<string>({ required: true }),
  host = ref<HTMLElement>(),
  preferences = usePreferences();
let instance: editor.IStandaloneCodeEditor | undefined,
  model: editor.ITextModel | undefined,
  resize: ResizeObserver | undefined;
let changed: { dispose(): void } | undefined,
  disposed = false,
  applying = false;
let releaseWorkers: (() => void) | undefined;
let completion: { dispose(): void } | undefined;
const references = inject(
  ruleReferencesKey,
  computed(() => []),
);
if (!languages.getLanguages().some((language) => language.id === "sma-rule")) {
  languages.register({ id: "sma-rule" });
  languages.setMonarchTokensProvider("sma-rule", {
    tokenizer: {
      root: [
        [/\b(input|params|context)\b/, "variable"],
        [/\b(min|max|abs|round|size|contains|intervalValue)\b/, "predefined"],
        [/\b(true|false|null)\b/, "keyword"],
        [/'[^']*'|"[^"]*"/, "string"],
        [/\d+(\.\d+)?/, "number"],
        [/[+*/<>=!&|?:%-]+/, "operator"],
      ],
    },
  });
}
function theme() {
  const dark = document.documentElement.classList.contains("dark"),
    styles = getComputedStyle(document.documentElement);
  const token = styles.getPropertyValue("--card").trim();
  const background = /^#[0-9a-f]{3}$/i.test(token)
    ? "#" + [...token.slice(1)].map((char) => char + char).join("")
    : /^#[0-9a-f]{6}(?:[0-9a-f]{2})?$/i.test(token)
      ? token
      : dark
        ? "#1D1E28"
        : "#FFFFFF";
  editor.defineTheme("sma-rule-theme", {
    base: dark ? "vs-dark" : "vs",
    inherit: true,
    rules: [],
    colors: {
      "editor.background": background,
      "editorLineNumber.foreground": dark ? "#B0AFBF" : "#646575",
    },
  });
  instance?.updateOptions({ theme: "sma-rule-theme" });
}
function reveal() {
  if (props.line && instance) {
    instance.revealLineInCenter(props.line);
    instance.setPosition({ lineNumber: props.line, column: props.column || 1 });
  }
}
onMounted(() => {
  if (!host.value || disposed) return;
  releaseWorkers = retainEditorWorkers();
  model = editor.createModel(
    value.value,
    props.language === "rule" ? "sma-rule" : "plaintext",
  );
  instance = editor.create(host.value, {
    model,
    ariaLabel: props.label,
    readOnly: props.readonly,
    minimap: { enabled: false },
    fontSize: 13,
    scrollBeyondLastLine: false,
    wordWrap: "on",
    automaticLayout: false,
    fixedOverflowWidgets: true,
    tabSize: 2,
    accessibilitySupport: "on",
    renderLineHighlight: "line",
    padding: { top: 12 },
  });
  if (props.language === "rule")
    completion = languages.registerCompletionItemProvider("sma-rule", {
      triggerCharacters: ["."],
      provideCompletionItems(currentModel, position) {
        if (currentModel !== model || props.readonly)
          return { suggestions: [] };
        const prefix =
          currentModel
            .getLineContent(position.lineNumber)
            .slice(0, position.column - 1)
            .match(/[a-zA-Z0-9_.]*$/)?.[0] || "";
        return {
          suggestions: references.value.map((item) => ({
            label: item.label,
            detail: item.detail,
            kind:
              item.kind === "function"
                ? languages.CompletionItemKind.Function
                : languages.CompletionItemKind.Field,
            insertText: item.insertText,
            insertTextRules:
              languages.CompletionItemInsertTextRule.InsertAsSnippet,
            range: {
              startLineNumber: position.lineNumber,
              endLineNumber: position.lineNumber,
              startColumn: position.column - prefix.length,
              endColumn: position.column,
            },
          })),
        };
      },
    });
  changed = instance.onDidChangeModelContent(() => {
    if (!applying) value.value = instance?.getValue() || "";
  });
  resize = new ResizeObserver(() => instance?.layout());
  resize.observe(host.value);
  theme();
  reveal();
});
watch(value, (text) => {
  if (model && model.getValue() !== text) {
    applying = true;
    model.setValue(text);
    applying = false;
  }
});
watch(
  () => props.readonly,
  (readonly) => instance?.updateOptions({ readOnly: readonly }),
);
watch(
  () => props.label,
  (label) => instance?.updateOptions({ ariaLabel: label }),
);
watch(() => [props.line, props.column], reveal);
watch(() => preferences.theme, theme);
const observer = new MutationObserver(theme);
onMounted(() =>
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class"],
  }),
);
onBeforeUnmount(() => {
  disposed = true;
  resize?.disconnect();
  observer.disconnect();
  changed?.dispose();
  completion?.dispose();
  instance?.dispose();
  model?.dispose();
  releaseWorkers?.();
});
</script>
<template>
  <div
    ref="host"
    class="rule-code h-64 min-h-48 w-full overflow-hidden rounded-xl border bg-card"
    :aria-label="label"
    role="group"
  />
</template>
