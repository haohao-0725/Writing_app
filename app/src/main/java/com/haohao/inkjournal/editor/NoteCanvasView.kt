package com.haohao.inkjournal.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.haohao.inkjournal.data.PageSpec
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * 筆記畫布：底下是 [DryInkView]（已完成的筆跡），上面是 Jetpack Ink 的 [InProgressStrokesView]
 * （正在寫的筆跡，用前緩衝直接畫到螢幕上，延遲最低）。
 *
 * 輸入規則（以 Galaxy Tab S7 FE + S Pen 為準）：
 *  - S Pen 寫字；按住 S Pen 側鍵（或工具列選橡皮擦）時是筆劃橡皮擦。
 *  - 一指捲動、兩指縮放、兩下點擊回到符合頁寬。
 *  - S Pen 靠近螢幕（懸停）或剛寫完的一小段時間內，手指/手掌的觸碰一律忽略，避免手掌捲動或畫出線。
 */
@SuppressLint("ViewConstructor")
class NoteCanvasView(
    context: Context,
    private val session: NoteSession,
    private val fingerDrawing: () -> Boolean,
) : FrameLayout(context), NoteSession.Listener {

    private val density = resources.displayMetrics.density
    val viewport = Viewport(sideMarginPx = 14f * density)
    private val dry = DryInkView(context, session, viewport)
    private val wet = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val scroller = OverScroller(context)
    private var initialized = false

    init {
        addView(dry, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        wet.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) = handOff(strokes)
        })
        session.listener = this
        isFocusable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 先把前緩衝準備好，第一筆就不會慢
        post { wet.eagerInit() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewport.pageCount = session.pages.size
        viewport.setSize(w.toFloat(), h.toFloat())
        if (!initialized && viewport.hasSize) {
            initialized = true
            viewport.scrollToPage(session.currentPage)
        }
        viewportChanged()
    }

    // ---------------- NoteSession.Listener ----------------

    override fun onPageContentChanged(pageIndex: Int, fromStroke: Int) = dry.onPageContentChanged(pageIndex, fromStroke)

    override fun onPageListChanged() {
        viewport.pageCount = session.pages.size
        dry.onPageListChanged()
        viewportChanged()
    }

    override fun onSettingsChanged() = dry.invalidate()

    // ---------------- 交接：正在寫 → 已完成 ----------------

    private val strokePages = HashMap<InProgressStrokeId, String>()

    private fun handOff(strokes: Map<InProgressStrokeId, Stroke>) {
        for ((id, stroke) in strokes) {
            val pageId = strokePages.remove(id) ?: continue
            val pi = session.indexOfPage(pageId)
            if (pi >= 0) session.addStroke(pi, InkConvert.fromInk(stroke))
        }
        dry.invalidate()
        wet.removeFinishedStrokes(strokes.keys)
    }

    /** 離開畫面/進背景前：把還沒交接的筆劃收進來。 */
    fun collectPendingStrokes() {
        val pending = wet.getFinishedStrokes()
        if (pending.isNotEmpty()) handOff(pending)
    }

    fun release() {
        collectPendingStrokes()
        scroller.forceFinished(true)
        session.listener = null
        dry.release()
    }

    // ---------------- 外部操作 ----------------

    fun addPageAndScroll() {
        val i = session.addPage()
        viewport.scrollToPage(i)
        viewportChanged()
    }

    fun scrollToPage(i: Int) {
        viewport.scrollToPage(i)
        viewportChanged()
    }

    /** 浮動工具列蓋住的上下高度（px）。 */
    fun setInsets(top: Float, bottom: Float) {
        if (top == viewport.insetTop && bottom == viewport.insetBottom) return
        viewport.setInsets(top, bottom)
        if (viewport.hasSize) viewportChanged()
    }

    private val overlays = HashMap<String, RectF>()

    /** 浮在畫布上的介面（工具列、頁碼）位置，畫布座標。正在寫的筆跡經過這些地方時會被藏起來。 */
    fun setOverlay(key: String, rect: RectF?) {
        val old = overlays[key]
        if (rect == null) {
            if (overlays.remove(key) == null) return
        } else {
            if (old == rect) return
            overlays[key] = RectF(rect)
        }
        if (penPointerId == NONE) updateWetMask()
    }

    private fun viewportChanged() {
        session.updateCurrentPage(viewport.currentPage())
        if (penPointerId == NONE) updateWetMask()
        dry.invalidate()
    }

    /** 正在寫的筆跡只在紙面上顯示（跟乾掉後被頁面裁切的樣子一致）。 */
    private fun updateWetMask() {
        if (!viewport.hasSize) return
        val mask = Path().apply { addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW) }
        val pages = Path()
        val s = viewport.scale
        for (i in viewport.visiblePages()) {
            val l = viewport.pageLeftPx()
            val t = viewport.pageTopPx(i)
            pages.addRect(l, t, l + PageSpec.WIDTH_MM * s, t + PageSpec.HEIGHT_MM * s, Path.Direction.CW)
        }
        mask.op(pages, Path.Op.DIFFERENCE)
        if (overlays.isNotEmpty()) {
            val over = Path()
            for (r in overlays.values) over.addRoundRect(r, OVERLAY_RADIUS * density, OVERLAY_RADIUS * density, Path.Direction.CW)
            mask.op(over, Path.Op.UNION)
        }
        wet.maskPath = mask
    }

    // ---------------- 觸控分派 ----------------

    private var penPointerId = NONE
    private var penStrokeId: InProgressStrokeId? = null
    private var penPage = -1
    private var penMode = PenMode.NONE
    private var lastPenTime = 0L
    private var penHovering = false
    private val hit = FloatArray(3)
    private var eraseLastX = 0f
    private var eraseLastY = 0f

    private enum class PenMode { NONE, INK, ERASE, BUTTON, IGNORE }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (isPenTool(event.getToolType(0))) {
            lastPenTime = SystemClock.uptimeMillis()
            penHovering = event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
        }
        return super.dispatchHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = normalizedAction(event)
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = if (isSamsungPenAction(event)) 0 else event.actionIndex
                val tool = event.getToolType(idx)
                val pid = event.getPointerId(idx)
                val asPen = isPenTool(tool) ||
                    (tool == MotionEvent.TOOL_TYPE_FINGER && fingerDrawing() && penPointerId == NONE &&
                        fingers.isIdle && event.pointerCount == 1)
                if (asPen) {
                    if (penPointerId == NONE) penDown(event, idx, pid)
                } else if (tool == MotionEvent.TOOL_TYPE_FINGER) {
                    if (penPointerId != NONE && penIsFinger) {
                        // 手指寫字模式下第二根手指放下 → 取消這一筆，改成捲動/縮放
                        cancelPen(event)
                        fingers.onPointerDown(event, event.findPointerIndex(firstFingerId), firstFingerId)
                    }
                    if (penPointerId != NONE || palmGuardActive()) {
                        fingers.ignore(pid)
                    } else {
                        fingers.onPointerDown(event, idx, pid)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (penPointerId != NONE) penMove(event)
                fingers.onMove(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = if (isSamsungPenAction(event)) 0 else event.actionIndex
                val pid = event.getPointerId(idx)
                if (pid == penPointerId) penUp(event, idx) else fingers.onPointerUp(event, idx, pid)
                if (action == MotionEvent.ACTION_UP) fingers.reset()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (penPointerId != NONE) cancelPen(event)
                fingers.reset()
            }
        }
        return true
    }

    private fun palmGuardActive(): Boolean =
        penHovering || SystemClock.uptimeMillis() - lastPenTime < PALM_GUARD_MS

    // ---------------- 筆 ----------------

    private var penIsFinger = false
    private var firstFingerId = NONE

    private fun penDown(event: MotionEvent, idx: Int, pid: Int) {
        val tool = event.getToolType(idx)
        penIsFinger = tool == MotionEvent.TOOL_TYPE_FINGER
        firstFingerId = if (penIsFinger) pid else NONE
        penPointerId = pid
        lastPenTime = SystemClock.uptimeMillis()
        scroller.forceFinished(true)
        fingers.reset()
        requestUnbufferedDispatch(event)

        val x = event.getX(idx)
        val y = event.getY(idx)
        if (viewport.isOnAddPageButton(x, y)) {
            penMode = PenMode.BUTTON
            return
        }
        if (!viewport.hitPage(x, y, hit)) {
            penMode = PenMode.IGNORE
            return
        }
        penPage = hit[0].toInt()
        val erase = session.eraserMode ||
            tool == MotionEvent.TOOL_TYPE_ERASER ||
            (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
            isSamsungPenAction(event)
        if (erase) {
            penMode = PenMode.ERASE
            session.beginErase(penPage)
            eraseLastX = hit[1]
            eraseLastY = hit[2]
            eraseSegment(hit[1], hit[2], hit[1], hit[2])
            showEraser(x, y)
        } else {
            penMode = PenMode.INK
            val s = viewport.scale
            val toPage = Matrix().apply {
                setTranslate(-viewport.pageLeftPx(), -viewport.pageTopPx(penPage))
                postScale(1f / s, 1f / s)
            }
            val st = session.settings
            predictor.record(event)
            penStrokeId = wet.startStroke(event, pid, InkConvert.brush(st.penColor, st.penSizeMm), toPage)
            strokePages[penStrokeId!!] = session.pages[penPage].id
        }
    }

    private fun penMove(event: MotionEvent) {
        lastPenTime = SystemClock.uptimeMillis()
        when (penMode) {
            PenMode.INK -> {
                val id = penStrokeId ?: return
                predictor.record(event)
                val predicted = predictor.predict()
                try {
                    wet.addToStroke(event, penPointerId, id, predicted)
                } finally {
                    predicted?.recycle()
                }
            }
            PenMode.ERASE -> {
                val idx = event.findPointerIndex(penPointerId).takeIf { it >= 0 } ?: return
                val s = viewport.scale
                val l = viewport.pageLeftPx()
                val t = viewport.pageTopPx(penPage)
                for (h in 0 until event.historySize) {
                    eraseTo((event.getHistoricalX(idx, h) - l) / s, (event.getHistoricalY(idx, h) - t) / s)
                }
                eraseTo((event.getX(idx) - l) / s, (event.getY(idx) - t) / s)
                showEraser(event.getX(idx), event.getY(idx))
            }
            else -> Unit
        }
    }

    private fun penUp(event: MotionEvent, idx: Int) {
        when (penMode) {
            PenMode.INK -> penStrokeId?.let { wet.finishStroke(event, penPointerId, it) }
            PenMode.ERASE -> {
                session.endErase()
                hideEraser()
            }
            PenMode.BUTTON -> if (viewport.isOnAddPageButton(event.getX(idx), event.getY(idx))) addPageAndScroll()
            else -> Unit
        }
        endPen()
    }

    private fun cancelPen(event: MotionEvent) {
        when (penMode) {
            PenMode.INK -> penStrokeId?.let { id ->
                wet.cancelStroke(id, event)
                strokePages.remove(id)
            }
            PenMode.ERASE -> {
                session.endErase()
                hideEraser()
            }
            else -> Unit
        }
        endPen()
    }

    private fun endPen() {
        penPointerId = NONE
        penStrokeId = null
        penMode = PenMode.NONE
        penPage = -1
        lastPenTime = if (penIsFinger) 0L else SystemClock.uptimeMillis()
        penIsFinger = false
        updateWetMask()
    }

    private fun eraseTo(x: Float, y: Float) {
        eraseSegment(eraseLastX, eraseLastY, x, y)
        eraseLastX = x
        eraseLastY = y
    }

    private fun eraseSegment(ax: Float, ay: Float, bx: Float, by: Float) {
        val page = session.pages.getOrNull(penPage) ?: return
        val r = ERASER_RADIUS_DP * density / viewport.scale
        val hits = EraserHit.hits(page.strokes, ax, ay, bx, by, r)
        if (hits.isNotEmpty()) session.eraseStrokes(penPage, hits)
    }

    private fun showEraser(x: Float, y: Float) {
        dry.eraserVisible = true
        dry.eraserX = x
        dry.eraserY = y
        dry.eraserRadiusPx = ERASER_RADIUS_DP * density
        dry.invalidate()
    }

    private fun hideEraser() {
        dry.eraserVisible = false
        dry.invalidate()
    }

    // ---------------- 手指：捲動、縮放、點兩下 ----------------

    private val fingers = FingerGestures()

    private inner class FingerGestures {
        private val active = LinkedHashMap<Int, Boolean>() // pointerId -> 是否忽略（手掌）
        private var panning = false
        private var lastCx = 0f
        private var lastCy = 0f
        private var lastSpan = 0f
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var lastTapTime = 0L
        private var lastTapX = 0f
        private var lastTapY = 0f
        private var velocity: VelocityTracker? = null
        private var zoomed = false

        val isIdle get() = active.isEmpty()

        fun ignore(pid: Int) {
            active[pid] = true
        }

        fun onPointerDown(event: MotionEvent, idx: Int, pid: Int) {
            if (idx < 0) return
            scroller.forceFinished(true)
            if (active.isEmpty()) {
                downX = event.getX(idx)
                downY = event.getY(idx)
                downTime = event.eventTime
                panning = false
                zoomed = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain()
            }
            active[pid] = false
            velocity?.addMovement(event)
            rebase(event, excludeId = NONE)
        }

        fun onMove(event: MotionEvent) {
            if (active.values.none { !it }) return
            velocity?.addMovement(event)
            val (cx, cy, span, count) = centroid(event, NONE)
            if (count == 0) return
            if (!panning) {
                val moved = hypot(cx - downX, cy - downY)
                if (moved > touchSlop || (count >= 2 && abs(span - lastSpan) > touchSlop)) {
                    panning = true
                    lastCx = cx
                    lastCy = cy
                    lastSpan = span
                }
                return
            }
            var changed = false
            if (count >= 2 && lastSpan > 0f && span > 0f) {
                val f = span / lastSpan
                if (abs(f - 1f) > 0.0005f) {
                    if (!zoomed) {
                        zoomed = true
                        dry.zooming = true
                    }
                    viewport.zoomBy(f, cx, cy)
                    changed = true
                }
            }
            val dx = cx - lastCx
            val dy = cy - lastCy
            if (dx != 0f || dy != 0f) {
                viewport.panBy(dx, dy)
                changed = true
            }
            lastCx = cx
            lastCy = cy
            lastSpan = span
            if (changed) viewportChanged()
        }

        fun onPointerUp(event: MotionEvent, idx: Int, pid: Int) {
            val wasIgnored = active[pid] ?: return
            velocity?.addMovement(event)
            val remainingReal = active.count { (id, ign) -> id != pid && !ign }
            if (!wasIgnored && remainingReal == 0) {
                if (!panning) {
                    if (event.eventTime - downTime < TAP_MAX_MS) onTap(event.getX(idx), event.getY(idx), event.eventTime)
                } else if (!zoomed) {
                    velocity?.let { vt ->
                        vt.computeCurrentVelocity(1000)
                        fling(vt.getXVelocity(pid), vt.getYVelocity(pid))
                    }
                }
                if (zoomed) zoomEnded()
                panning = false
            }
            active.remove(pid)
            rebase(event, excludeId = pid)
        }

        fun reset() {
            if (zoomed) zoomEnded()
            active.clear()
            panning = false
            zoomed = false
            velocity?.recycle()
            velocity = null
        }

        private fun zoomEnded() {
            zoomed = false
            dry.zooming = false
            dry.invalidate()
        }

        private fun onTap(x: Float, y: Float, time: Long) {
            val isDouble = time - lastTapTime < doubleTapTimeout && hypot(x - lastTapX, y - lastTapY) < touchSlop * 4
            if (isDouble) {
                lastTapTime = 0L
                viewport.resetZoom(x, y)
                viewportChanged()
                return
            }
            lastTapTime = time
            lastTapX = x
            lastTapY = y
            if (viewport.isOnAddPageButton(x, y)) addPageAndScroll()
        }

        private fun rebase(event: MotionEvent, excludeId: Int) {
            val (cx, cy, span, _) = centroid(event, excludeId)
            lastCx = cx
            lastCy = cy
            lastSpan = span
        }

        private fun centroid(event: MotionEvent, excludeId: Int): Quad {
            var sx = 0f
            var sy = 0f
            var n = 0
            for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                if (id == excludeId || active[id] != false) continue
                sx += event.getX(i)
                sy += event.getY(i)
                n++
            }
            if (n == 0) return Quad(0f, 0f, 0f, 0)
            val cx = sx / n
            val cy = sy / n
            var dev = 0f
            for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                if (id == excludeId || active[id] != false) continue
                dev += hypot(event.getX(i) - cx, event.getY(i) - cy)
            }
            return Quad(cx, cy, if (n >= 2) dev / n * 2f else 0f, n)
        }
    }

    private data class Quad(val cx: Float, val cy: Float, val span: Float, val count: Int)

    private var lastFlingX = 0
    private var lastFlingY = 0

    private fun fling(vx: Float, vy: Float) {
        if (max(abs(vx), abs(vy)) < ViewConfiguration.get(context).scaledMinimumFlingVelocity) return
        lastFlingX = 0
        lastFlingY = 0
        scroller.fling(0, 0, vx.toInt(), vy.toInt(), Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE)
        postOnAnimation(flingStep)
    }

    private val flingStep = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) return
            val x = scroller.currX
            val y = scroller.currY
            viewport.panBy((x - lastFlingX).toFloat(), (y - lastFlingY).toFloat())
            lastFlingX = x
            lastFlingY = y
            viewportChanged()
            postOnAnimation(this)
        }
    }

    companion object {
        private const val NONE = -1
        private const val PALM_GUARD_MS = 450L
        private const val TAP_MAX_MS = 300L
        private const val ERASER_RADIUS_DP = 12f
        private const val OVERLAY_RADIUS = 26f

        // 舊款 Samsung 裝置按住 S Pen 側鍵時會送出自己的動作代碼
        private const val SAMSUNG_PEN_DOWN = 211
        private const val SAMSUNG_PEN_UP = 212
        private const val SAMSUNG_PEN_MOVE = 213

        fun isPenTool(tool: Int) = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER

        fun isSamsungPenAction(e: MotionEvent) = e.actionMasked in SAMSUNG_PEN_DOWN..SAMSUNG_PEN_MOVE

        fun normalizedAction(e: MotionEvent): Int = when (e.actionMasked) {
            SAMSUNG_PEN_DOWN -> MotionEvent.ACTION_DOWN
            SAMSUNG_PEN_UP -> MotionEvent.ACTION_UP
            SAMSUNG_PEN_MOVE -> MotionEvent.ACTION_MOVE
            else -> e.actionMasked
        }
    }
}
