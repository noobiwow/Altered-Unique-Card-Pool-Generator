import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import AlteredVuePlugin from 'altered-tcg';

const app =  createApp(App).use(AlteredVuePlugin).use(router);

app.mount('#app')
