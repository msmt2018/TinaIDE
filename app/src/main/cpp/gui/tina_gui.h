/**
 * TinaIDE GUI Runtime API
 *
 * 零依赖的轻量级图形 API。用户只需实现以下几个 C 函数，编译为 .so，
 * TinaIDE 的 GuiHostActivity 会加载并调用它们，提供渲染和输入支持。
 *
 * 最小示例：
 *
 *   #include "tina_gui.h"
 *
 *   int tina_gui_render_argb32(int w, int h, uint32_t* pixels, int stride) {
 *       tina_gui_fill_rect(pixels, stride, 0, 0, w, h, TINA_RGBA(30, 30, 30, 255));
 *       tina_gui_fill_rect(pixels, stride, 100, 100, 200, 150, TINA_RGBA(0, 120, 255, 255));
 *       return 0;
 *   }
 *
 * 编译（单文件）：
 *   输出模式选择 GUI，TinaIDE 会自动编译为 .so 并加载运行。
 *
 * ============================================================================
 * API 版本: 2
 * ============================================================================
 */

#ifndef TINA_GUI_H
#define TINA_GUI_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ========================================================================== */
/*  用户需要实现的函数（通过 dlsym 动态查找）                                   */
/* ========================================================================== */

/**
 * [必选] 每帧调用。用户将 ARGB32 像素写入 pixels 数组。
 *
 * @param width   画布宽度（像素）
 * @param height  画布高度（像素）
 * @param pixels  ARGB32 像素缓冲区，长度 >= width * height
 *                每个像素: 0xAARRGGBB
 * @param stride  每行像素数（通常 == width）
 * @return  0 = 正常继续, >0 = 正常但建议等待 N 毫秒, <0 = 出错/结束
 */
/* int tina_gui_render_argb32(int width, int height, uint32_t* pixels, int stride); */

/**
 * [可选] 库加载后、首次渲染前调用。用于初始化资源。
 *
 * @return 0 = 成功, 非零 = 失败（会阻止渲染启动）
 */
/* int tina_gui_init(void); */

/**
 * [可选] 库卸载前调用。用于释放资源。
 */
/* void tina_gui_shutdown(void); */

/**
 * [可选] 触摸事件回调。
 *
 * @param action      0 = 按下(DOWN), 1 = 抬起(UP), 2 = 移动(MOVE)
 * @param x           触摸点 x 坐标（像素，相对于渲染区域）
 * @param y           触摸点 y 坐标
 * @param pointer_id  触摸指针 ID（多点触控场景下区分不同手指）
 */
/* void tina_gui_on_touch(int action, float x, float y, int pointer_id); */

/**
 * [可选] 按键事件回调。
 *
 * @param keycode  Android KeyEvent.KEYCODE_* 值
 *                 常见：KEYCODE_BACK=4, KEYCODE_VOLUME_UP=24, KEYCODE_VOLUME_DOWN=25
 * @param action   0 = 按下(DOWN), 1 = 抬起(UP)
 */
/* void tina_gui_on_key(int keycode, int action); */


/* ========================================================================== */
/*  常量定义                                                                   */
/* ========================================================================== */

#define TINA_TOUCH_DOWN  0
#define TINA_TOUCH_UP    1
#define TINA_TOUCH_MOVE  2

#define TINA_KEY_DOWN    0
#define TINA_KEY_UP      1


/* ========================================================================== */
/*  颜色工具                                                                   */
/* ========================================================================== */

/** 从 RGBA 分量构造 ARGB32 颜色值 */
#define TINA_RGBA(r, g, b, a) \
    ((uint32_t)((((uint32_t)(a) & 0xFF) << 24) | \
                (((uint32_t)(r) & 0xFF) << 16) | \
                (((uint32_t)(g) & 0xFF) <<  8) | \
                (((uint32_t)(b) & 0xFF)      )))

#define TINA_RGB(r, g, b) TINA_RGBA(r, g, b, 255)

/** 预定义颜色 */
#define TINA_COLOR_BLACK       TINA_RGB(0, 0, 0)
#define TINA_COLOR_WHITE       TINA_RGB(255, 255, 255)
#define TINA_COLOR_RED         TINA_RGB(255, 0, 0)
#define TINA_COLOR_GREEN       TINA_RGB(0, 255, 0)
#define TINA_COLOR_BLUE        TINA_RGB(0, 0, 255)
#define TINA_COLOR_YELLOW      TINA_RGB(255, 255, 0)
#define TINA_COLOR_CYAN        TINA_RGB(0, 255, 255)
#define TINA_COLOR_MAGENTA     TINA_RGB(255, 0, 255)
#define TINA_COLOR_TRANSPARENT TINA_RGBA(0, 0, 0, 0)


/* ========================================================================== */
/*  内联绘图辅助函数                                                           */
/* ========================================================================== */

/** 设置单个像素（自动边界检查） */
static inline void tina_gui_set_pixel(
    uint32_t* pixels, int stride, int x, int y, int w, int h, uint32_t color
) {
    if (x >= 0 && x < w && y >= 0 && y < h) {
        pixels[y * stride + x] = color;
    }
}

/** 填充矩形 */
static inline void tina_gui_fill_rect(
    uint32_t* pixels, int stride,
    int x, int y, int rect_w, int rect_h,
    uint32_t color
) {
    int x0 = x < 0 ? 0 : x;
    int y0 = y < 0 ? 0 : y;
    /* stride 同时也是画布宽度的上限 */
    int x1 = (x + rect_w > stride) ? stride : (x + rect_w);
    /* 这里需要外部传入画布高度，但为了简洁用 stride 作为安全上限。
     * 实际调用时请确保 y + rect_h 不超过画布高度。 */
    int y1 = y + rect_h;

    for (int row = y0; row < y1; ++row) {
        for (int col = x0; col < x1; ++col) {
            pixels[row * stride + col] = color;
        }
    }
}

/** 画水平线 */
static inline void tina_gui_hline(
    uint32_t* pixels, int stride,
    int x, int y, int length, int canvas_h,
    uint32_t color
) {
    if (y < 0 || y >= canvas_h) return;
    int x0 = x < 0 ? 0 : x;
    int x1 = (x + length > stride) ? stride : (x + length);
    for (int col = x0; col < x1; ++col) {
        pixels[y * stride + col] = color;
    }
}

/** 画垂直线 */
static inline void tina_gui_vline(
    uint32_t* pixels, int stride,
    int x, int y, int length, int canvas_h,
    uint32_t color
) {
    if (x < 0 || x >= stride) return;
    int y0 = y < 0 ? 0 : y;
    int y1 = (y + length > canvas_h) ? canvas_h : (y + length);
    for (int row = y0; row < y1; ++row) {
        pixels[row * stride + x] = color;
    }
}

/** 画矩形边框（不填充） */
static inline void tina_gui_draw_rect(
    uint32_t* pixels, int stride, int canvas_h,
    int x, int y, int rect_w, int rect_h,
    uint32_t color
) {
    tina_gui_hline(pixels, stride, x, y, rect_w, canvas_h, color);
    tina_gui_hline(pixels, stride, x, y + rect_h - 1, rect_w, canvas_h, color);
    tina_gui_vline(pixels, stride, x, y, rect_h, canvas_h, color);
    tina_gui_vline(pixels, stride, x + rect_w - 1, y, rect_h, canvas_h, color);
}

/** 清屏（填充整个画布） */
static inline void tina_gui_clear(
    uint32_t* pixels, int stride, int canvas_h, uint32_t color
) {
    tina_gui_fill_rect(pixels, stride, 0, 0, stride, canvas_h, color);
}

#ifdef __cplusplus
}
#endif

#endif /* TINA_GUI_H */
