package com.stardust.autojs.core.console

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.stardust.autojs.R
import com.stardust.autojs.core.console.ConsoleImpl.LogListener
import com.stardust.enhancedfloaty.ResizableExpandableFloatyWindow
import com.stardust.util.MapBuilder
import com.stardust.util.ViewUtils

/**
 * Created by Stardust on 2017/5/2.
 *
 *
 * TODO: 优化为无锁形式
 */
class ConsoleView : FrameLayout, LogListener {
    /**
     * 多选状态变化的监听。
     */
    fun interface SelectionListener {
        fun onSelectionChanged(selectionMode: Boolean, selectedCount: Int)
    }

    val colors = SparseIntArray().apply {
        put(Log.VERBOSE, context.getColor(R.color.console_verbose))
        put(Log.DEBUG, context.getColor(R.color.console_debug))
        put(Log.INFO, -0x9b22e9)
        put(Log.WARN, -0xd69d01)
        put(Log.ERROR, -0x2b0000)
        put(Log.ASSERT, -0xacb2)
    }
    private var mConsole: ConsoleImpl? = null
    private lateinit var mLogListRecyclerView: RecyclerView
    private lateinit var mEditText: EditText
    private lateinit var submitButton: Button
    private var mWindow: ResizableExpandableFloatyWindow? = null
    private var mShouldStopRefresh = false
    private val mLogEntries = ArrayList<LogEntry>()
    private var mLogSize = -1f

    /** 已经与 [mLogEntries] 对齐的 [ConsoleImpl.trimmedCount]。 */
    private var mSyncedTrimmedCount = 0L

    private val mSelectedIds = HashSet<Int>()
    private var mSelectionAnchorId: Int? = null
    private var mSelectionListener: SelectionListener? = null

    /**
     * 是否处于多选状态。多选状态下点击某一行会选中/取消选中该行，而不是选中行内文字。
     */
    var isSelectionMode = false
        private set

    val selectedCount: Int
        get() = mSelectedIds.size

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(
        context, attrs
    ) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    ) {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        inflate(context, R.layout.console_view, this)
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ConsoleView)
            for ((styleable, logLevel) in ATTRS) {
                colors.put(logLevel, typedArray.getColor(styleable, colors[logLevel]))
            }
            typedArray.recycle()
        }
        mLogListRecyclerView = findViewById(R.id.log_list)
        val manager = LinearLayoutManager(context)
        mLogListRecyclerView.layoutManager = manager
        mLogListRecyclerView.adapter = Adapter()
        // 点选日志行时不需要渐变动画，否则每次选中都会闪一下
        (mLogListRecyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        initEditText()
        initSubmitButton()
    }

    private fun initSubmitButton() {
        submitButton = findViewById(R.id.submit)
        submitButton.setOnClickListener {
            val input: CharSequence = mEditText.text
            submitInput(input)
        }
    }

    private fun submitInput(input: CharSequence) {
        if (TextUtils.isEmpty(input)) {
            return
        }
        if (mConsole!!.submitInput(input)) {
            mEditText.setText("")
        }
    }

    private fun initEditText() {
        mEditText = findViewById(R.id.input)
        mEditText.isFocusableInTouchMode = true
        val mInputContainer = findViewById<LinearLayout>(R.id.input_container)
        val listener = OnClickListener { _: View? ->
            if (mWindow != null) {
                mWindow!!.requestWindowFocus()
                mEditText.requestFocus()
            }
        }
        mEditText.setOnClickListener(listener)
        mInputContainer.setOnClickListener(listener)
    }

    fun setConsole(console: ConsoleImpl) {
        mConsole = console
        console.setConsoleView(this)
        resetLogs()
    }

    override fun onNewLog(logEntry: LogEntry) {}
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mShouldStopRefresh = false
        postDelayed(object : Runnable {
            override fun run() {
                refreshLog()
                if (!mShouldStopRefresh) {
                    postDelayed(this, REFRESH_INTERVAL)
                }
            }
        }, REFRESH_INTERVAL)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mShouldStopRefresh = true
    }

    override fun onLogClear() {
        post { resetLogs() }
    }

    /**
     * 丢弃当前显示的内容和选中状态，下一次 [refreshLog] 会从 [ConsoleImpl.allLogs] 重新同步。
     */
    private fun resetLogs() {
        val console = mConsole
        mLogEntries.clear()
        mSyncedTrimmedCount = if (console != null) {
            synchronized(console.allLogs) { console.trimmedCount }
        } else {
            0L
        }
        mSelectedIds.clear()
        mSelectionAnchorId = null
        val wasSelecting = isSelectionMode
        isSelectionMode = false
        mLogListRecyclerView.adapter!!.notifyDataSetChanged()
        if (wasSelecting) {
            notifySelectionChanged()
        }
    }

    /**
     * 把 [ConsoleImpl.allLogs] 的变化同步到界面：
     * 先移除被 [ConsoleImpl] 从头部裁剪掉的日志，再追加新日志。
     */
    private fun refreshLog() {
        val console = mConsole ?: return
        val removeCount: Int
        var added: List<LogEntry> = emptyList()
        synchronized(console.allLogs) {
            val trimmed = console.trimmedCount
            val dropped = (trimmed - mSyncedTrimmedCount).coerceAtLeast(0L)
            removeCount = minOf(dropped, mLogEntries.size.toLong()).toInt()
            mSyncedTrimmedCount = trimmed
            // 移除头部之后，界面上剩下的日志对应 allLogs 的前 keep 条
            val keep = mLogEntries.size - removeCount
            if (console.allLogs.size > keep) {
                added = ArrayList(console.allLogs.subList(keep, console.allLogs.size))
            }
        }
        val adapter = mLogListRecyclerView.adapter!!
        if (removeCount > 0) {
            val removed = mLogEntries.subList(0, removeCount)
            for (entry in removed) {
                mSelectedIds.remove(entry.id)
            }
            if (mSelectedIds.isEmpty() || mSelectionAnchorId?.let { it !in mSelectedIds } == true) {
                mSelectionAnchorId = null
            }
            removed.clear()
            adapter.notifyItemRangeRemoved(0, removeCount)
            if (isSelectionMode) {
                notifySelectionChanged()
            }
        }
        if (added.isNotEmpty()) {
            val oldSize = mLogEntries.size
            // 用户停在底部时才跟随新日志滚动，往上翻看时不要把用户拉回底部
            val stickToBottom = oldSize == 0 || !mLogListRecyclerView.canScrollVertically(1)
            mLogEntries.addAll(added)
            adapter.notifyItemRangeInserted(oldSize, added.size)
            if (stickToBottom && !isSelectionMode) {
                mLogListRecyclerView.scrollToPosition(mLogEntries.size - 1)
            }
        }
    }

    fun setWindow(window: ResizableExpandableFloatyWindow?) {
        mWindow = window
    }

    fun showEditText() {
        post {
            mEditText.visibility = VISIBLE
            submitButton.visibility = VISIBLE
            mWindow!!.requestWindowFocus()
            //mInputContainer.setVisibility(VISIBLE);
            mEditText.requestFocus()
        }
    }

    fun hideEditText() {
        post {
            mEditText.visibility = GONE
            submitButton.visibility = GONE
        }
    }

    fun setLogSize(size: Int) {
        mLogSize = ViewUtils.spToPx(context, size.toFloat()).toInt().toFloat()
    }

    fun setSelectionListener(listener: SelectionListener?) {
        mSelectionListener = listener
        listener?.onSelectionChanged(isSelectionMode, mSelectedIds.size)
    }

    /**
     * 进入多选状态。
     */
    fun enterSelectionMode() {
        if (isSelectionMode) return
        isSelectionMode = true
        mLogListRecyclerView.adapter!!.notifyDataSetChanged()
        notifySelectionChanged()
    }

    /**
     * 退出多选状态并取消所有选中。
     */
    fun exitSelectionMode() {
        if (!isSelectionMode) return
        isSelectionMode = false
        mSelectedIds.clear()
        mSelectionAnchorId = null
        mLogListRecyclerView.adapter!!.notifyDataSetChanged()
        notifySelectionChanged()
    }

    /**
     * 选中当前显示的所有日志（必要时先进入多选状态）。
     */
    fun selectAll() {
        isSelectionMode = true
        mSelectedIds.clear()
        for (entry in mLogEntries) {
            mSelectedIds.add(entry.id)
        }
        mSelectionAnchorId = mLogEntries.lastOrNull()?.id
        mLogListRecyclerView.adapter!!.notifyDataSetChanged()
        notifySelectionChanged()
    }

    /**
     * 返回选中的日志，按显示顺序排列。
     */
    fun getSelectedEntries(): List<LogEntry> {
        if (mSelectedIds.isEmpty()) return emptyList()
        return mLogEntries.filter { it.id in mSelectedIds }
    }

    private fun toggleSelection(position: Int) {
        val entry = mLogEntries.getOrNull(position) ?: return
        if (mSelectedIds.remove(entry.id)) {
            if (mSelectionAnchorId == entry.id) mSelectionAnchorId = null
        } else {
            mSelectedIds.add(entry.id)
            mSelectionAnchorId = entry.id
        }
        mLogListRecyclerView.adapter!!.notifyItemChanged(position)
        notifySelectionChanged()
    }

    /**
     * 选中上一次点选的那一行到 [position] 之间的所有行；没有上一次点选的行时只选中 [position] 这一行。
     */
    private fun selectRangeTo(position: Int) {
        val entry = mLogEntries.getOrNull(position) ?: return
        val anchorPosition = mSelectionAnchorId?.let { id -> mLogEntries.indexOfFirst { it.id == id } } ?: -1
        val from = if (anchorPosition >= 0) minOf(anchorPosition, position) else position
        val to = if (anchorPosition >= 0) maxOf(anchorPosition, position) else position
        for (i in from..to) {
            mSelectedIds.add(mLogEntries[i].id)
        }
        mSelectionAnchorId = entry.id
        mLogListRecyclerView.adapter!!.notifyItemRangeChanged(from, to - from + 1)
        notifySelectionChanged()
    }

    private fun notifySelectionChanged() {
        mSelectionListener?.onSelectionChanged(isSelectionMode, mSelectedIds.size)
    }

    private class ViewHolder(itemView: View, val logSize: Float) :
        RecyclerView.ViewHolder(itemView) {
        var textView: TextView = itemView as TextView

        fun bindData(logEntry: LogEntry) {
            val text = if (logEntry.content.length > 5000) {
                logEntry.content.substring(0, 2000) + " ......<${logEntry.content.length - 5000}>"
            } else logEntry.content
            textView.text = text
            if (logSize != -1f) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, logSize)
            }
        }
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder?>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.console_view_item, parent, false),
                mLogSize
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val logEntry = mLogEntries[position]
            holder.bindData(logEntry)
            holder.textView.setTextColor(colors[logEntry.level])
            bindSelection(holder, logEntry)
        }

        override fun getItemCount(): Int {
            return mLogEntries.size
        }

        /**
         * 普通状态下行内文字可以长按选中；多选状态下改为整行点选，并高亮选中的行。
         */
        private fun bindSelection(holder: ViewHolder, logEntry: LogEntry) {
            val textView = holder.textView
            if (isSelectionMode) {
                if (textView.isTextSelectable) {
                    textView.setTextIsSelectable(false)
                }
                val selected = logEntry.id in mSelectedIds
                textView.setBackgroundColor(if (selected) SELECTED_BACKGROUND else Color.TRANSPARENT)
                textView.setOnClickListener { toggleSelection(holder.adapterPosition) }
                textView.setOnLongClickListener {
                    selectRangeTo(holder.adapterPosition)
                    true
                }
            } else {
                textView.setOnClickListener(null)
                textView.setOnLongClickListener(null)
                textView.background = null
                if (!textView.isTextSelectable) {
                    textView.setTextIsSelectable(true)
                }
            }
        }
    }

    companion object {
        private val ATTRS = MapBuilder<Int, Int>()
            .put(R.styleable.ConsoleView_color_verbose, Log.VERBOSE)
            .put(R.styleable.ConsoleView_color_debug, Log.DEBUG)
            .put(R.styleable.ConsoleView_color_info, Log.INFO)
            .put(R.styleable.ConsoleView_color_warn, Log.WARN)
            .put(R.styleable.ConsoleView_color_error, Log.ERROR)
            .put(R.styleable.ConsoleView_color_assert, Log.ASSERT)
            .build()
        private const val REFRESH_INTERVAL = 100L

        /** 多选状态下被选中行的背景色（半透明蓝色，在深色和浅色背景上都看得清文字）。 */
        private const val SELECTED_BACKGROUND = 0x4D2196F3
    }
}
