import { createPinia } from 'pinia'
import { createApp } from 'vue'
import 'vant/lib/index.css'
import '@yunshu/shared/styles'
import './styles/main.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
