import type { InjectionKey } from 'vue'
import type { NavigationFailure } from 'vue-router'
/** The shell resolves a saved page entry to its already-open work tab when present. */
export const shelfNavigate: InjectionKey<(path: string) => Promise<void | NavigationFailure | undefined>> = Symbol('shelf-navigation')
