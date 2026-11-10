import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { i18n, setLocale } from './locales'
import { permission } from './directives/permission'
import { useAuth } from './stores/auth'

const app = createApp(App)
app.use(createPinia())
app.use(i18n)
app.use(router)
app.use(ElementPlus)
app.directive('permission', permission)
setLocale(i18n.global.locale.value)
window.addEventListener('sma:unauthorized', () => {
  useAuth().reset()
  if (router.currentRoute.value.path !== '/login') {
    void router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})
app.mount('#app')
