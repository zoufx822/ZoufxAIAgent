/**
 * Vue 3 应用入口
 */
import { ChatWindow } from './components/ChatWindow.js';

const app = Vue.createApp({});

// 注册 ChatWindow 组件
app.component('ChatWindow', ChatWindow);

// 挂载应用
app.mount('#app');
