import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { i18n, setLocale } from './locales'
import { permission } from './directives/permission'
import { useAuth } from './stores/auth'
import './styles/theme.css'
const app = createApp(App)
app.use(createPinia()).use(i18n).use(router)
app.directive('permission', permission)
setLocale(i18n.global.locale.value)
window.addEventListener('sma:unauthorized', () => { useAuth().reset(); if (router.currentRoute.value.path !== '/login') void router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } }) })
window.addEventListener('sma:locked', () => { const auth = useAuth(); if (auth.user) auth.user.locked = true })
app.mount('#app')
