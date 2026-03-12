/**
 * Markdown 渲染器 (markdown-it + Prism.js)
 * 支持增量渲染，避免流式传输中的闪烁问题
 */

class MarkdownRenderer {
    constructor() {
        this._initMarkdownIt();
        this._initPrism();
        // 增量渲染状态缓存
        this._incrementalCache = {
            lastText: '',
            lastHtml: '',
            lastTokens: null,
            lastTextLength: 0
        };
    }

    /**
     * 初始化 markdown-it
     */
    _initMarkdownIt() {
        if (!window.markdownit) {
            console.warn('markdown-it not loaded, falling back to plain text');
            this.md = null;
            return;
        }

        this.md = window.markdownit({
            html: false,       // 禁用 HTML 标签，防止 XSS
            linkify: true,     // 自动链接识别（安全）
            typographer: true, // 智能标点替换
            breaks: true,      // 换行符转 <br>（安全）
            highlight: this._highlightCode.bind(this)
        });

        // 启用常用插件
        try {
            // 可选：根据需要加载插件
            // this.md.use(window.markdownitAbbr);
            // this.md.use(window.markdownitFootnote);
        } catch (e) {
            console.debug('Markdown-it plugins not available:', e.message);
        }
    }

    /**
     * 初始化 Prism.js
     */
    _initPrism() {
        if (!window.Prism) {
            console.warn('Prism.js not loaded, code highlighting disabled');
            this.prism = null;
            return;
        }
        this.prism = window.Prism;
    }

    /**
     * 代码高亮回调 (用于 markdown-it)
     * @param {string} code - 原始代码
     * @param {string} lang - 语言标识
     * @returns {string} 高亮后的 HTML
     */
    _highlightCode(code, lang) {
        if (!this.prism) {
            // 无 Prism 时，返回转义的代码块
            return `<pre><code class="language-${lang || 'text'}">${this._escapeHtml(code)}</code></pre>`;
        }

        // 标准化语言标识
        const normalizedLang = lang ? lang.toLowerCase() : 'text';

        // 检查是否支持该语言
        const language = this.prism.languages[normalizedLang]
            ? normalizedLang
            : (this.prism.languages.plaintext ? 'plaintext' : 'text');

        try {
            const highlighted = this.prism.highlight(
                code,
                this.prism.languages[language],
                language
            );
            return `<pre class="language-${language}"><code class="language-${language}">${highlighted}</code></pre>`;
        } catch (error) {
            console.debug(`Prism highlight failed for language "${language}":`, error.message);
            return `<pre><code class="language-${language}">${this._escapeHtml(code)}</code></pre>`;
        }
    }

    /**
     * HTML 转义
     */
    _escapeHtml(text) {
        if (!text) return '';
        return String(text).replace(/[&<>"']/g,
            c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c])
        );
    }

    /**
     * 静态HTML转义方法（用于外部调用）
     */
    static escapeHtml(text) {
        if (!text) return '';
        return String(text).replace(/[&<>"']/g,
            c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c])
        );
    }

    /**
     * 完整渲染 Markdown 文本
     * @param {string} text - Markdown 文本
     * @returns {string} 渲染后的 HTML
     */
    render(text) {
        if (!this.md) return this._escapeHtml(text);
        if (!text) return '';

        try {
            return this.md.render(text);
        } catch (error) {
            console.error('Markdown rendering failed:', error);
            return this._escapeHtml(text);
        }
    }

    /**
     * 增量渲染（优化流式传输体验）
     * 缓存渲染状态，避免每次完全重新渲染
     *
     * 改进版本 v2:
     * 1. 缓存上次渲染结果，避免重复渲染相同内容
     * 2. 检测是否只是追加内容（流式传输典型情况）
     * 3. 对短追加内容尝试增量更新，避免完整重新渲染
     * 4. 检测Markdown结构变化，必要时回退到完整渲染
     *
     * @param {string} text - 当前累积的完整文本
     * @returns {string} 渲染后的 HTML
     */
    renderIncremental(text) {
        if (!this.md) return this._escapeHtml(text);
        if (!text) return '';

        const cache = this._incrementalCache;

        // 如果是第一次渲染或文本比缓存短（用户删除了内容），重新渲染
        if (!cache.lastText || text.length < cache.lastTextLength) {
            const html = this.render(text);
            cache.lastText = text;
            cache.lastHtml = html;
            cache.lastTextLength = text.length;
            cache.lastTokens = null;
            return html;
        }

        // 检查是否只是追加内容（流式传输的典型情况）
        const isAppendOnly = text.startsWith(cache.lastText);
        const addedText = isAppendOnly ? text.slice(cache.lastText.length) : '';

        // 如果是追加且新增内容较短（< 200字符），尝试增量更新
        // 注意：对于Markdown，增量更新是近似的，可能不完美
        if (isAppendOnly && addedText.length < 200) {
            // 检查新增内容是否可能改变Markdown结构
            const needsFullRender = this._needsFullRender(addedText, cache.lastText);

            if (!needsFullRender) {
                // 尝试增量更新：渲染新增部分并追加
                const incrementalHtml = this._renderAppendedText(addedText, cache.lastText, cache.lastHtml);
                if (incrementalHtml !== null) {
                    cache.lastText = text;
                    cache.lastHtml = incrementalHtml;
                    cache.lastTextLength = text.length;
                    return incrementalHtml;
                }
            }
        }

        // 回退到完整渲染
        const html = this.render(text);
        cache.lastText = text;
        cache.lastHtml = html;
        cache.lastTextLength = text.length;
        cache.lastTokens = null;
        return html;
    }

    /**
     * 检查新增内容是否需要完整重新渲染
     * @private
     */
    _needsFullRender(addedText, previousText) {
        // 如果新增内容包含可能改变Markdown结构的字符
        const structurePatterns = [
            /^```/,               // 新代码块开始
            /^\s*```/,            // 前面有空白的新代码块开始
            /^\s*-\s+/,           // 新列表项开始
            /^\s*\*\s+/,          // 新列表项开始（星号）
            /^\s*\d+\.\s+/,       // 新有序列表项开始
            /^\s*#+\s+/,          // 新标题
            /^\s*>\s+/,           // 新引用块
            /^\s*`[^`\n]*`$/,     // 行内代码（可能影响上下文）
        ];

        // 检查新增文本本身
        for (const pattern of structurePatterns) {
            if (pattern.test(addedText)) {
                return true;
            }
        }

        // 检查新增内容是否可能完成一个未闭合的结构
        // 例如：之前的文本有未闭合的代码块，新增内容包含结束符
        const previousHasOpenCodeBlock = /```[^`]*$/.test(previousText) && addedText.includes('```');
        const previousHasOpenList = /^\s*[-*]\s+.*$/m.test(previousText) && addedText.trim() === '';

        return previousHasOpenCodeBlock || previousHasOpenList;
    }

    /**
     * 检查文本是否是一个完整的Markdown块
     * 例如：代码块、引用块、标题等
     * @private
     */
    _isCompleteMarkdownBlock(text) {
        const trimmed = text.trim();
        if (!trimmed) return false;

        // 检查是否是代码块（以```开始和结束）
        const codeBlockRegex = /^```[\s\S]*?```$/;
        if (codeBlockRegex.test(trimmed)) return true;

        // 检查是否是标题（以1-6个#开头，后跟空格）
        const headingRegex = /^#{1,6}\s+/;
        if (headingRegex.test(trimmed)) return true;

        // 检查是否是引用块（以>开头）
        const blockquoteRegex = /^>\s+/m;
        if (blockquoteRegex.test(trimmed) && !trimmed.includes('\n') || trimmed.split('\n').every(line => /^>\s/.test(line))) {
            return true;
        }

        // 检查是否是列表项（以-、*或数字.开头）
        const listItemRegex = /^(\s*[-*]\s+|\s*\d+\.\s+)/;
        if (listItemRegex.test(trimmed) && !trimmed.includes('\n')) {
            return true;
        }

        // 检查是否是水平线
        const horizontalRuleRegex = /^([-*_]){3,}\s*$/;
        if (horizontalRuleRegex.test(trimmed)) return true;

        return false;
    }

    /**
     * 尝试渲染追加的文本并合并到现有HTML
     * @private
     */
    _renderAppendedText(addedText, previousText, previousHtml) {
        try {
            // 如果新增内容为空或只有空白字符，返回原HTML
            if (!addedText.trim()) {
                return previousHtml;
            }

            // 如果新增内容只是普通文本（没有Markdown格式）
            const hasMarkdownFormat = /[`*_[#![\]]/.test(addedText);
            if (!hasMarkdownFormat) {
                // 简单转义并追加到HTML
                const escaped = this._escapeHtml(addedText);
                return previousHtml + escaped;
            }

            // 检查新增文本是否以换行符开头（可能开始新的段落）
            const startsWithNewline = addedText.startsWith('\n') || addedText.startsWith('\r\n');

            // 检查新增文本是否可以作为独立块渲染
            // 例如：以换行符开头，然后是Markdown元素（标题、列表等）
            if (startsWithNewline) {
                // 去除开头的换行符，尝试渲染剩余部分
                const trimmedAddedText = addedText.replace(/^\r?\n/, '');
                if (trimmedAddedText.trim()) {
                    // 尝试渲染新增文本作为独立块
                    const addedHtml = this.render(trimmedAddedText);
                    // 将新增HTML追加到原HTML（添加段落分隔）
                    return previousHtml + addedHtml;
                }
            }

            // 检查新增文本是否是一个完整的Markdown块（如代码块、引用块等）
            const isCompleteBlock = this._isCompleteMarkdownBlock(addedText);
            if (isCompleteBlock) {
                // 渲染新增块并追加
                const addedHtml = this.render(addedText);
                return previousHtml + addedHtml;
            }

            // 回退到渲染完整的新文本
            const newText = previousText + addedText;
            const newHtml = this.render(newText);
            return newHtml;
        } catch (error) {
            console.debug('增量渲染失败，回退到完整渲染:', error.message);
            return null;
        }
    }

    /**
     * 检测文本是否包含代码块（用于流式 CSS 优化）
     * @param {string} text - Markdown 文本
     * @returns {boolean}
     */
    containsCodeBlock(text) {
        if (!text) return false;
        // 检测 Markdown 代码块
        return /```[\s\S]*?```|~~~[\s\S]*?~~~|<pre><code>[\s\S]*?<\/code><\/pre>/i.test(text);
    }

    /**
     * 获取渲染器实例（单例模式）
     */
    static getInstance() {
        if (!MarkdownRenderer._instance) {
            MarkdownRenderer._instance = new MarkdownRenderer();
        }
        return MarkdownRenderer._instance;
    }
}

// 全局实例
MarkdownRenderer._instance = null;

export default MarkdownRenderer;