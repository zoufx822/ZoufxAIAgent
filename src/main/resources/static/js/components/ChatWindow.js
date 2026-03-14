/**
 * ChatWindow 组件 - Vue 3
 */
import { sendMessageAPI } from '../api.js';
import MarkdownRenderer from '../utils/MarkdownRenderer.js';
import SmartScroll from '../utils/SmartScroll.js';

// ── Session ID ──────────────────────────────────────────────────────────────
// 每个浏览器会话生成一次，持久化到 sessionStorage，用于后端会话记忆
const SESSION_KEY = 'aiChatSessionId';
const sessionId = sessionStorage.getItem(SESSION_KEY) ?? (() => {
    const id = crypto.randomUUID();
    sessionStorage.setItem(SESSION_KEY, id);
    return id;
})();

// ── Markdown 渲染 ────────────────────────────────────────────────────────────
const escapeHtml = MarkdownRenderer.escapeHtml;

// Markdown 渲染器实例
const markdownRenderer = MarkdownRenderer.getInstance();

// 渲染 Markdown（完整渲染）
const renderMd = (text) => markdownRenderer.render(text);

// 检测是否包含代码块
const containsCodeBlock = (text) => markdownRenderer.containsCodeBlock(text);

// ── 组件 ─────────────────────────────────────────────────────────────────────
const WELCOME = '你好！我是 AI 助手，有什么可以帮助你的吗？';

export const ChatWindow = {
    name: 'ChatWindow',
    template: `
    <div class="container">
        <div class="header">
            <h1>AI 对话</h1>
            <span style="display: none">.</span>
        </div>
        <div class="chat-container" ref="chatEl" role="log" aria-label="聊天消息">
            <div class="chat-content">
                <div v-for="(msg, i) in messages" :key="i" class="message" :class="msg.role">

                    <template v-if="msg.role === 'user'">
                        <div class="message-content" v-html="msg.html"></div>
                    </template>

                    <template v-else>
                        <div v-if="msg.thinking" class="message-thinking" aria-live="polite" aria-atomic="false">
                            <span class="thinking-label">思考中...</span>{{ msg.thinking }}
                        </div>
                        <div v-if="msg.streaming && !msg.content" class="loading-indicator" aria-live="polite">
                            <span class="loading-dot"></span>
                            <span class="loading-dot"></span>
                            <span class="loading-dot"></span>
                        </div>
                        <div v-if="msg.html" class="message-text"
                             :class="{ streaming: msg.streaming, 'contains-code': msg.containsCode }"
                             :aria-live="msg.streaming ? 'polite' : 'off'"
                             :aria-busy="msg.streaming">
                             <!-- 打字机容器 -->
                             <div class="typewriter-container" :data-msg-index="i" v-html="msg.html"></div>
                        </div>
                    </template>

                </div>
            </div>
        </div>
        <div class="input-container">
            <div class="input-wrapper">
                <input v-model="inputText"
                       @keydown="handleInputKeydown"
                       placeholder="输入你的问题..."
                       :disabled="isLoading"
                       type="text"
                       aria-label="消息输入框">
                <button @click="isLoading ? stop() : sendMessage()"
                        :class="{ 'stop-btn': isLoading }"
                        :aria-label="isLoading ? '停止生成' : '发送消息'">
                    {{ isLoading ? '停止' : '发送' }}
                </button>
            </div>
        </div>
    </div>
    `,
    setup() {
        const messages = Vue.ref([
            { role: 'ai', content: WELCOME, thinking: '', html: renderMd(WELCOME), streaming: false, containsCode: false }
        ]);
        const inputText = Vue.ref('');
        const isLoading = Vue.ref(false);
        const chatEl = Vue.ref(null);
        let ctrl = null;
        let smartScroll = null;

        // 初始化智能滚动
        Vue.onMounted(() => {
            if (chatEl.value) {
                smartScroll = new SmartScroll(chatEl.value, {
                    threshold: 150, // 距离底部150px内自动滚动
                    autoScrollDelay: 2000, // 用户手动滚动后2秒恢复
                    smoothScroll: true // 使用平滑滚动
                });
            }
            // 页面加载时聚焦输入框
            Vue.nextTick(() => {
                const inputEl = document.querySelector('.input-wrapper input');
                if (inputEl) {
                    inputEl.focus();
                }
            });
        });

        // 组件销毁时清理智能滚动实例
        Vue.onUnmounted(() => {
            if (smartScroll) {
                smartScroll.destroy();
                smartScroll = null;
            }
        });

        const scrollBottom = () => Vue.nextTick(() => {
            if (smartScroll) {
                smartScroll.scrollToBottom();
            } else if (chatEl.value) {
                chatEl.value.scrollTop = chatEl.value.scrollHeight;
            }
        });

        const sendMessage = async () => {
            const text = inputText.value.trim();
            if (!text || isLoading.value) return;

            ctrl = new AbortController();
            messages.value.push({ role: 'user', content: text, html: escapeHtml(text) });
            inputText.value = '';
            isLoading.value = true;
            scrollBottom();

            const idx = messages.value.length;
            messages.value.push({ role: 'ai', content: '', thinking: '', html: '', streaming: true, containsCode: false });

            await sendMessageAPI(text, sessionId, {
                onThinking: (chunk) => {
                    messages.value[idx].thinking += chunk;
                },
                onContent: (chunk) => {
                    const msg = messages.value[idx];
                    if (!msg.streaming) return; // 已 stop，忽略残余数据

                    // 累积内容
                    msg.content += chunk;

                    // 检测是否包含代码块
                    msg.containsCode = containsCodeBlock(msg.content);

                    // 使用增量渲染（SSE 流式本身就是打字机效果）
                    const renderedHtml = markdownRenderer.renderIncremental(msg.content);
                    Vue.nextTick(() => {
                        const containerRef = chatEl.value?.querySelector(`[data-msg-index="${idx}"]`);
                        if (containerRef) {
                            containerRef.innerHTML = renderedHtml;
                        }
                    });
                    // 同时更新 msg.html，确保 v-if 显示容器
                    msg.html = renderedHtml;

                    scrollBottom();
                },
                onComplete: () => {
                    const msg = messages.value[idx];
                    const finalHtml = renderMd(msg.content);
                    msg.html = finalHtml;
                    msg.streaming = false;
                    msg.containsCode = false;
                    isLoading.value = false;
                    ctrl = null;
                    // 完成时确保显示完整内容并聚焦输入框
                    const inputEl = document.querySelector('.input-wrapper input');
                    Vue.nextTick(() => {
                        const containerRef = chatEl.value?.querySelector(`[data-msg-index="${idx}"]`);
                        if (containerRef) {
                            containerRef.innerHTML = finalHtml;
                        }
                        scrollBottom();
                    });
                    // 单独延迟聚焦，不与其他 DOM 操作混在一起
                    setTimeout(() => {
                        if (inputEl) {
                            inputEl.focus();
                        }
                    }, 200);
                },
                onError: (err) => {
                    const msg = messages.value[idx];
                    msg.content = `请求出错: ${err.message}`;
                    const errorHtml = escapeHtml(msg.content);
                    Vue.nextTick(() => {
                        const containerRef = chatEl.value?.querySelector(`[data-msg-index="${idx}"]`);
                        if (containerRef) {
                            containerRef.innerHTML = errorHtml;
                        }
                    });
                    msg.html = errorHtml;
                    msg.streaming = false;
                    msg.containsCode = false;
                    isLoading.value = false;
                    ctrl = null;
                },
                signal: ctrl.signal,
            });
        };

        // 输入框键盘事件处理
        const handleInputKeydown = (e) => {
            // Enter发送（无修饰键）
            if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.altKey && !e.metaKey) {
                e.preventDefault();
                sendMessage();
                return;
            }

            // Shift+Enter或Ctrl+Enter换行
            if (e.key === 'Enter' && (e.shiftKey || e.ctrlKey)) {
                e.preventDefault();
                const input = e.target;
                const cursorPos = input.selectionStart;
                const text = input.value;
                input.value = text.slice(0, cursorPos) + '\n' + text.slice(cursorPos);
                input.selectionStart = input.selectionEnd = cursorPos + 1;
                return;
            }

            // Esc键停止生成
            if (e.key === 'Escape' && isLoading.value) {
                e.preventDefault();
                stop();
                return;
            }
        };

        const stop = () => {
            // 更新 UI（streaming=false），再 abort，防止残余数据写入
            const msg = messages.value[messages.value.length - 1];
            if (msg?.streaming) {
                const stoppedHtml = renderMd(msg.content);
                Vue.nextTick(() => {
                    const containerRef = chatEl.value?.querySelector(`[data-msg-index="${messages.value.length - 1}"]`);
                    if (containerRef) {
                        containerRef.innerHTML = stoppedHtml;
                    }
                });
                msg.html = stoppedHtml;
                msg.streaming = false;
                msg.containsCode = false;
            }
            isLoading.value = false;
            ctrl?.abort();
            ctrl = null;
        };

        return { messages, inputText, isLoading, chatEl, sendMessage, stop, handleInputKeydown };
    }
};
