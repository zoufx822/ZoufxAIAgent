/**
 * Sidebar 组件 - 侧边栏 UI 占位符
 */
export const Sidebar = {
    name: 'Sidebar',
    template: `
        <aside class="sidebar">
            <div class="sidebar-header">
                <h1>AI 对话</h1>
                <div class="sidebar-subtitle">专业AI助手</div>
            </div>
            <div class="sessions-list">
                <div class="session-item active">
                    <div class="session-icon">💬</div>
                    <div class="session-info">
                        <div class="session-title">当前会话</div>
                        <div class="session-time">刚刚</div>
                    </div>
                </div>
                <!-- 更多占位符会话项 -->
            </div>
            <div class="sidebar-footer">
                <div class="status-indicator">
                    <span class="status-dot online"></span>
                    <span>AI 在线</span>
                </div>
            </div>
        </aside>
    `
};