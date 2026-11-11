import EditorWorker from "monaco-editor/editor/editor.worker?worker";
const workers = new Set<Worker>();
let users = 0;
export function retainEditorWorkers() {
  users++;
  globalThis.MonacoEnvironment = {
    getWorker: () => {
      const worker = new EditorWorker();
      workers.add(worker);
      return worker;
    },
  };
  let released = false;
  return () => {
    if (released) return;
    released = true;
    if (--users === 0) {
      workers.forEach((worker) => worker.terminate());
      workers.clear();
    }
  };
}
