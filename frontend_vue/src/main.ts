import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import AlteredVuePlugin from 'altered-tcg';

const app =  createApp(App).use(AlteredVuePlugin);

app.mount('#app')
