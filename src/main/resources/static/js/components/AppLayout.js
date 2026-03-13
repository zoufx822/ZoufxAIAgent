/**
 * AppLayout 组件 - 整体布局容器
 */
import { Sidebar } from './Sidebar.js';
import { ChatWindow } from './ChatWindow.js';

export const AppLayout = {
    name: 'AppLayout',
    components: {
        Sidebar,
        ChatWindow
    },
    template: `
        <div class="app-layout">
            <Sidebar />
            <main class="main-content">
                <ChatWindow />
            </main>
        </div>
    `
};