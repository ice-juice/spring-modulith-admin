import type { MotionOptions, MotionController } from "./motion-runtime";
export type CollectionLayoutMorphOptions = MotionOptions & { view?: "list" | "card" | "pack" };
export function mountCollectionLayoutMorph(target: HTMLElement, options?: CollectionLayoutMorphOptions): Omit<MotionController, "update"> & { update(options: CollectionLayoutMorphOptions): void };
