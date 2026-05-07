import { createApp } from 'vue'
import App from './App.vue'
import vuetify from './plugins/vuetify'
import './styles.css'

const app = createApp(App)
app.use(vuetify)
app.mount('#app')

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch((error) => {
    console.warn('Service worker registration failed:', error)
  })
}
