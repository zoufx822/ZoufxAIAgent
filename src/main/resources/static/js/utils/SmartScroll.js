/**
 * 智能滚动
 * 规则：
 *  - 用户向上滚（wheel/touch）→ 立即暂停自动跟随
 *  - 滚回底部附近 → 恢复自动跟随
 *  - 程序滚动不触发暂停逻辑
 */
class SmartScroll {
    #el;
    #paused = false;
    #programmatic = false;
    #touchStartY = 0;
    #onScroll;
    #onWheel;
    #onTouchStart;
    #onTouchMove;

    constructor(el, {threshold = 120} = {}) {
        this.#el = el;

        // 用户向上滚滚轮 → 立即暂停
        this.#onWheel = (e) => {
            if (e.deltaY < 0) this.#paused = true;
        };

        // 触摸：记录起点
        this.#onTouchStart = (e) => {
            this.#touchStartY = e.touches[0].clientY;
        };

        // 触摸：向上滑 → 立即暂停
        this.#onTouchMove = (e) => {
            if (e.touches[0].clientY > this.#touchStartY) this.#paused = true;
        };

        // scroll 事件：仅用于检测"是否回到底部"以恢复跟随
        this.#onScroll = () => {
            if (this.#programmatic) return;
            if (this.#isNearBottom(threshold)) this.#paused = false;
        };

        el.addEventListener('wheel', this.#onWheel, {passive: true});
        el.addEventListener('touchstart', this.#onTouchStart, {passive: true});
        el.addEventListener('touchmove', this.#onTouchMove, {passive: true});
        el.addEventListener('scroll', this.#onScroll, {passive: true});
    }

    #isNearBottom(threshold) {
        const {scrollTop, scrollHeight, clientHeight} = this.#el;
        return scrollHeight - scrollTop - clientHeight <= threshold;
    }

    /** 若未暂停则滚到底部（流式输出时调用） */
    scrollToBottom() {
        if (!this.#paused) {
            this.#programmatic = true;
            this.#el.scrollTop = this.#el.scrollHeight;
            requestAnimationFrame(() => {
                this.#programmatic = false;
            });
        }
    }

    /** 强制滚到底部并恢复自动跟随（发送新消息时调用） */
    forceScrollToBottom() {
        this.#paused = false;
        this.#programmatic = true;
        this.#el.scrollTop = this.#el.scrollHeight;
        requestAnimationFrame(() => {
            this.#programmatic = false;
        });
    }

    destroy() {
        this.#el.removeEventListener('wheel', this.#onWheel);
        this.#el.removeEventListener('touchstart', this.#onTouchStart);
        this.#el.removeEventListener('touchmove', this.#onTouchMove);
        this.#el.removeEventListener('scroll', this.#onScroll);
    }
}

export default SmartScroll;
