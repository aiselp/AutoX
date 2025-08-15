package org.autojs.autojs.ui.explorer

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.pio.PFiles
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerChangeEvent
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.ExplorerProjectPage
import org.autojs.autojs.model.explorer.ExplorerSamplePage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts.edit
import org.autojs.autojs.model.script.Scripts.openByOtherApps
import org.autojs.autojs.model.script.Scripts.send
import org.autojs.autojs.tool.Observers
import org.autojs.autojs.ui.build.BuildActivity
import org.autojs.autojs.ui.common.ScriptLoopDialog
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.viewmodel.ExplorerItemList
import org.autojs.autojs.ui.viewmodel.ExplorerItemList.SortConfig
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.ui.widget.fillMaxSize
import org.autojs.autojs.workground.WrapContentGridLayoutManger
import org.autojs.autoxjs.R
import java.io.File
import java.util.Stack

@OptIn(ExperimentalMaterial3Api::class)
open class ExplorerViewKt : FrameLayout,
    PopupMenu.OnMenuItemClickListener, ViewTreeObserver.OnGlobalFocusChangeListener {

    private var onItemClickListener: ((view: View, item: ExplorerItem?) -> Unit)? = null
    private var onItemOperatedListener: ((item: ExplorerItem?) -> Unit)? = null
    private var explorerItemList = ExplorerItemList()
    protected val explorerItemListView: RecyclerView = RecyclerView(context)
    private val projectToolbar: ExplorerProjectToolbar = ExplorerProjectToolbar(context)

    private val explorerAdapter: ExplorerAdapter = ExplorerAdapter()
    private var filter: ((ExplorerItem) -> Boolean)? = null
    private var explorer: Explorer? = null
    private val pageStateHistory = Stack<ExplorerPageState>()
    private var currentPageState = ExplorerPageState()
    private var dirSortMenuShowing = false
    private var directorySpanSize1 = 2
    val currentPage get() = currentPageState.currentPage
    private var disposable: Disposable? = null
    private var isRefreshing by mutableStateOf(false)
    private var scope: CoroutineScope? = null

    init {
        val composeView = ComposeView(context)
        composeView.setContent {
            scope = rememberCoroutineScope()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { onRefresh() }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    projectToolbar.Content()
                    Box(Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))) {
                        AndroidView(factory = { explorerItemListView.fillMaxSize() })
                    }
                }
            }
        }
        addView(composeView)
        Log.d(
            LOG_TAG, "item bg = " + Integer.toHexString(
                ContextCompat.getColor(context, R.color.item_background)
            )
        )
        initExplorerItemListView()
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    fun setRootPage(page: ExplorerPage?) {
        pageStateHistory.clear()
        setCurrentPageState(ExplorerPageState(page))
        loadItemList()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && canGoBack()) {
            goBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setCurrentPageState(currentPageState: ExplorerPageState) {
        this.currentPageState = currentPageState
        if (this.currentPageState.currentPage is ExplorerProjectPage) {
            projectToolbar.visibility = false
            projectToolbar.setProject(currentPageState.currentPage!!.toScriptFile())
        } else {
            projectToolbar.visibility = true
        }
    }

    protected fun enterDirectChildPage(childItemGroup: ExplorerPage?) {
        currentPageState.scrollY =
            (explorerItemListView.layoutManager as LinearLayoutManager?)!!.findLastCompletelyVisibleItemPosition()
        pageStateHistory.push(currentPageState)
        setCurrentPageState(ExplorerPageState(childItemGroup))
        loadItemList()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    fun setOnItemClickListener(listener: (view: View, item: ExplorerItem?) -> Unit) {
        this.onItemClickListener = listener
    }

    var sortConfig: SortConfig?
        get() = explorerItemList.sortConfig
        set(sortConfig) {
            explorerItemList.sortConfig = sortConfig
        }

    fun setExplorer(explorer: Explorer, rootPage: ExplorerPage?) {
        disposable?.dispose()
        this.explorer = explorer
        setRootPage(rootPage)
        disposable = explorer.registerChangeListener { event -> onExplorerChange(event) }
    }

    fun setExplorer(explorer: Explorer?, rootPage: ExplorerPage?, currentPage: ExplorerPage) {
        disposable?.dispose()
        this.explorer = explorer
        pageStateHistory.clear()
        setCurrentPageState(ExplorerPageState(rootPage))
        disposable = this.explorer!!.registerChangeListener { event -> onExplorerChange(event) }
        enterChildPage(currentPage)
    }

    fun enterChildPage(childPage: ExplorerPage) {
        val root = currentPageState.currentPage!!.toScriptFile()
        var dir = childPage.toScriptFile()
        val dirs = Stack<ScriptFile>()
        while (dir != root) {
            dir = dir!!.parentFile
            if (dir == null) {
                break
            }
            dirs.push(dir)
        }
        var parent: ExplorerDirPage? = null
        while (!dirs.empty()) {
            dir = dirs.pop()
            val dirPage = ExplorerDirPage(dir, parent)
            pageStateHistory.push(ExplorerPageState(dirPage))
            parent = dirPage
        }
        setCurrentPageState(ExplorerPageState(childPage))
        loadItemList()
    }

    fun setOnItemOperatedListener(listener: (item: ExplorerItem?) -> Unit) {
        this.onItemOperatedListener = listener
    }

    fun canGoBack(): Boolean {
        return !pageStateHistory.empty()
    }

    fun goBack() {
        setCurrentPageState(pageStateHistory.pop())
        loadItemList()
    }

    fun setDirectorySpanSize(directorySpanSize: Int) {
        directorySpanSize1 = directorySpanSize
    }

    fun setFilter(filter: (ExplorerItem) -> Boolean) {
        this.filter = filter
        reload()
    }

    fun reload() {
        loadItemList()
    }

    override fun isFocused(): Boolean {
        return true
    }

    private fun initExplorerItemListView() {
        explorerItemListView.adapter = explorerAdapter
        val manager = WrapContentGridLayoutManger(context, 2)
        manager.setDebugInfo("ExplorerView")
        manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                //For directories
                return if (position > positionOfCategoryDir && position < positionOfCategoryFile()) {
                    directorySpanSize1
                } else 2
                //For files and category
            }
        }
        explorerItemListView.layoutManager = manager
    }

    private fun positionOfCategoryFile(): Int {
        return if (currentPageState.dirsCollapsed) 1 else explorerItemList.groupCount() + 1
    }

    @SuppressLint("CheckResult", "NotifyDataSetChanged")
    private fun loadItemList() {
        isRefreshing = true
        explorer!!.fetchChildren(currentPageState.currentPage)
            .subscribeOn(Schedulers.io())
            .flatMapObservable { page: ExplorerPage? ->
                currentPageState.currentPage = page
                Observable.fromIterable(page)
            }
            .filter { f: ExplorerItem -> if (filter == null) true else filter!!.invoke(f) }
            .collectInto(explorerItemList.cloneConfig()) { obj: ExplorerItemList, item: ExplorerItem? ->
                obj.add(
                    item
                )
            }
            .observeOn(Schedulers.computation())
            .doOnSuccess { obj: ExplorerItemList -> obj.sort() }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { list: ExplorerItemList ->
                explorerItemList = list
                explorerAdapter.notifyDataSetChanged()
                isRefreshing = false
                post { explorerItemListView.scrollToPosition(currentPageState.scrollY) }
            }
    }

    fun onExplorerChange(event: ExplorerChangeEvent) {
        Log.d(LOG_TAG, "on explorer change: $event")
        if (event.action == ExplorerChangeEvent.ALL) {
            loadItemList()
            return
        }
        val currentDirPath = currentPageState.currentPage!!.path
        val changedDirPath = event.page.path
        val item = event.item
        val changedItemPath = item?.path
        if (currentDirPath == changedItemPath || currentDirPath == changedDirPath &&
            event.action == ExplorerChangeEvent.CHILDREN_CHANGE
        ) {
            loadItemList()
            return
        }
        if (currentDirPath == changedDirPath) {
            val i: Int
            when (event.action) {
                ExplorerChangeEvent.CHANGE -> {
                    i = explorerItemList.update(item, event.newItem)
                    if (i >= 0) {
                        explorerAdapter.notifyItemChanged(item, i)
                    }
                }

                ExplorerChangeEvent.CREATE -> {
                    explorerItemList.insertAtFront(event.newItem)
                    explorerAdapter.notifyItemInserted(event.newItem, 0)
                }

                ExplorerChangeEvent.REMOVE -> {
                    i = explorerItemList.remove(item)
                    if (i >= 0) {
                        explorerAdapter.notifyItemRemoved(item, i)
                    }
                }
            }
        }
    }

    fun onRefresh() {
        explorer!!.notifyChildrenChanged(currentPageState.currentPage)
        projectToolbar.refresh()
    }

    val currentDirectory: ScriptFile?
        get() = currentPage?.toScriptFile()


    fun onMenuSelect(optionMenu: OptionMenu, item: ExplorerItem) {
        val operations = ScriptOperations(context, this, currentPage)
        when (optionMenu) {
            OptionMenu.RUN_REPEATEDLY -> {
                ScriptLoopDialog(context, item.toScriptFile()).show()
            }

            OptionMenu.RENAME -> operations.rename(item as ExplorerFileItem)

            OptionMenu.DELETE -> operations.delete(item.toScriptFile()) {
                loadItemList()
            }

            OptionMenu.SEND -> send(item.toScriptFile())

            OptionMenu.RESET_TO_INITIAL_CONTENT -> {
                val e = Explorers.Providers.workspace().resetSample(
                    item.toScriptFile()
                ).observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        Snackbar.make(
                            this,
                            R.string.text_reset_succeed,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }, Observers.toastMessage())
            }

            OptionMenu.TIMED_TASK -> operations.timedTask(item.toScriptFile())
            OptionMenu.BUILD_APK -> BuildActivity.start(context, item.path)
            OptionMenu.OPEN_BY_OTHER_APPS -> openByOtherApps(item.toScriptFile())
            OptionMenu.CREATE_SHORTCUT -> operations.createShortcut(item.toScriptFile())
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_sort_by_date -> sort(ExplorerItemList.SORT_TYPE_DATE, dirSortMenuShowing)
            R.id.action_sort_by_type -> sort(ExplorerItemList.SORT_TYPE_TYPE, dirSortMenuShowing)
            R.id.action_sort_by_name -> sort(ExplorerItemList.SORT_TYPE_NAME, dirSortMenuShowing)
            R.id.action_sort_by_size -> sort(ExplorerItemList.SORT_TYPE_SIZE, dirSortMenuShowing)

            else -> return false
        }
        return true
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun sort(sortType: Int, isDir: Boolean) {
        isRefreshing = true
        scope?.launch(Dispatchers.Default) {
            if (isDir) {
                explorerItemList.sortItemGroup(sortType)
            } else {
                explorerItemList.sortFile(sortType)
            }
            withContext(Dispatchers.Main) {
                explorerAdapter.notifyDataSetChanged()
                isRefreshing = false
            }
        }
    }


    protected open fun onCreateViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        viewType: Int
    ): BindableViewHolder<Any> {
        return when (viewType) {
            VIEW_TYPE_ITEM -> ExplorerItemViewHolder(ComposeView(context))
            VIEW_TYPE_PAGE -> ExplorerPageViewHolder(ComposeView(context))

            else -> {
                CategoryViewHolder(
                    inflater.inflate(
                        R.layout.script_file_list_category,
                        parent,
                        false
                    )
                )
            }
        }
    }

    @Composable
    fun ExplorerList() {
        LazyColumn {
            items(explorerItemList.groupCount()) {

            }
        }
    }

    private inner class ExplorerAdapter : RecyclerView.Adapter<BindableViewHolder<Any>>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindableViewHolder<Any> {
            val inflater = LayoutInflater.from(context)
            return this@ExplorerViewKt.onCreateViewHolder(inflater, parent, viewType)
        }

        override fun onBindViewHolder(holder: BindableViewHolder<Any>, position: Int) {
            val positionOfCategoryFile = positionOfCategoryFile()
            if (position == positionOfCategoryDir || position == positionOfCategoryFile) {
                holder.bind(position == positionOfCategoryDir, position)
                return
            }
            if (position < positionOfCategoryFile) {
                holder.bind(explorerItemList.getItemGroup(position - 1), position)
                return
            }
            holder.bind(
                explorerItemList.getItem(position - positionOfCategoryFile - 1),
                position
            )
        }

        override fun getItemViewType(position: Int): Int {
            val positionOfCategoryFile = positionOfCategoryFile()
            return if (position == positionOfCategoryDir || position == positionOfCategoryFile) {
                VIEW_TYPE_CATEGORY
            } else if (position < positionOfCategoryFile) {
                VIEW_TYPE_PAGE
            } else {
                VIEW_TYPE_ITEM
            }
        }

        fun getItemPosition(item: ExplorerItem?, i: Int): Int {
            return if (item is ExplorerPage) {
                i + positionOfCategoryDir + 1
            } else i + positionOfCategoryFile() + 1
        }

        fun notifyItemChanged(item: ExplorerItem?, i: Int) {
            notifyItemChanged(getItemPosition(item, i))
        }

        fun notifyItemRemoved(item: ExplorerItem?, i: Int) {
            notifyItemRemoved(getItemPosition(item, i))
        }

        fun notifyItemInserted(item: ExplorerItem?, i: Int) {
            notifyItemInserted(getItemPosition(item, i))
        }

        override fun getItemCount(): Int {
            var count = 0
            if (!currentPageState.dirsCollapsed) {
                count += explorerItemList.groupCount()
            }
            if (!currentPageState.filesCollapsed) {
                count += explorerItemList.itemCount()
            }
            return count + 2
        }
    }

    inner class ExplorerItemViewHolder internal constructor(view: ComposeView) :
        BindableViewHolder<Any>(view) {
        var name by mutableStateOf("")
        var firstChar by mutableStateOf("J")
        var desc by mutableStateOf("")
        var firstCharBackground by mutableStateOf(Color(0xFF5cab7d))

        var editVisibility by mutableStateOf(true)
        var runVisibility by mutableStateOf(true)
        var showMenu by mutableStateOf(false)

        //        var options = itemBinding.more
        private var explorerItem: ExplorerItem? by mutableStateOf(null)

        init {
            view.setContent {
                ExplorerItem(
                    name = name,
                    desc = desc,
                    firstChar = firstChar,
                    firstCharBackground = firstCharBackground,
                    editVisibility = editVisibility,
                    runVisibility = runVisibility,
                    onItemClick = { onItemClick() },
                    run = { run() },
                    edit = ::edit,
                    more = { showMenu = true },
                ) {
                    explorerItem?.let {
                        OptionMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            it
                        ) { showMenu = false; onMenuSelect(it, explorerItem!!) }
                    }
                }
            }
        }

        override fun bind(item: Any, position: Int) {
            if (item !is ExplorerItem) return
            explorerItem = item
            name = ExplorerViewHelper.getDisplayName(item)
            desc = PFiles.getHumanReadableSize(item.size)
            firstChar = ExplorerViewHelper.getIconText(item)
            firstCharBackground = Color(ExplorerViewHelper.getIconColor(item))
            editVisibility = item.isEditable
            runVisibility = item.isExecutable
        }

        private fun onItemClick() {
            onItemClickListener?.invoke(itemView, explorerItem)
            onItemOperatedListener?.invoke(explorerItem)
        }

        fun run() {
            EngineController.runScript(File(explorerItem!!.path))
        }

        fun edit() {
            edit(context, ScriptFile(explorerItem!!.path))
        }
    }


    inner class ExplorerPageViewHolder internal constructor(
        view: ComposeView,
    ) : BindableViewHolder<Any>(view) {

        var name by mutableStateOf("")
        var optionsVisibility by mutableStateOf(false)
        var iconRes by mutableIntStateOf(R.drawable.circle_blue)
        private var explorerPage: ExplorerPage? = null

        init {
            view.setContent {
                ElevatedCard(
                    onClick = { onItemClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(16.dp))
                        Image(
                            painter = painterResource(iconRes),
                            modifier = Modifier.size(32.dp),
                            contentDescription = null
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(text = name, modifier = Modifier.weight(1f))
                        var show by remember { mutableStateOf(false) }
                        if (!optionsVisibility) IconButton(onClick = { show = true }) {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                painter = painterResource(R.drawable.ic_more_vert_black_24dp),
                                contentDescription = null
                            )
                            OptionMenu2(
                                expanded = show,
                                onDismissRequest = { show = false },
                                onMenuSelect = { show = false; onMenuSelect(it, explorerPage!!) }
                            )
                        }
                    }
                }
            }
        }

        override fun bind(data: Any, position: Int) {
            if (data !is ExplorerPage) return
            name = ExplorerViewHelper.getDisplayName(data)
            iconRes = ExplorerViewHelper.getIcon(data)
            optionsVisibility = data is ExplorerSamplePage
            explorerPage = data
        }

        private fun onItemClick() {
            enterDirectChildPage(explorerPage)
        }
    }

    internal inner class CategoryViewHolder(itemView: View) :
        BindableViewHolder<Any>(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val sort: ImageView = itemView.findViewById(R.id.sort)
        private val sortOrder: ImageView = itemView.findViewById(R.id.order)
        private val goBack: ImageView = itemView.findViewById(R.id.back)
        private val arrow: ImageView = itemView.findViewById(R.id.collapse)
        private var isDir = false

        init {
            sortOrder.setOnClickListener { changeSortOrder() }
            sort.setOnClickListener { showSortOptions() }
            goBack.setOnClickListener {
                if (canGoBack()) {
                    goBack()
                }
            }
            itemView.findViewById<View>(R.id.title_container).setOnClickListener {
                collapseOrExpand()
            }
        }

        override fun bind(isDirCategory: Any, position: Int) {
            if (isDirCategory !is Boolean) return
            title.setText(if (isDirCategory) R.string.text_directory else R.string.text_file)
            isDir = isDirCategory
            if (isDirCategory && canGoBack()) {
                goBack.visibility = VISIBLE
            } else {
                goBack.visibility = GONE
            }
            if (isDirCategory) {
                arrow.rotation = if (currentPageState.dirsCollapsed) -90f else 0.toFloat()
                sortOrder.setImageResource(if (explorerItemList.isDirSortedAscending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
            } else {
                arrow.rotation = if (currentPageState.filesCollapsed) -90f else 0.toFloat()
                sortOrder.setImageResource(if (explorerItemList.isFileSortedAscending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
            }
        }


        private fun changeSortOrder() {
            if (isDir) {
                sortOrder.setImageResource(if (explorerItemList.isDirSortedAscending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
                explorerItemList.isDirSortedAscending = !explorerItemList.isDirSortedAscending
                sort(explorerItemList.dirSortType, isDir)
            } else {
                sortOrder.setImageResource(if (explorerItemList.isFileSortedAscending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
                explorerItemList.isFileSortedAscending = !explorerItemList.isFileSortedAscending
                sort(explorerItemList.fileSortType, isDir)
            }
        }

        private fun showSortOptions() {
            val popupMenu = PopupMenu(context, sort)
            popupMenu.inflate(R.menu.menu_sort_options)
            popupMenu.setOnMenuItemClickListener(this@ExplorerViewKt)
            dirSortMenuShowing = isDir
            popupMenu.show()
        }


        private fun collapseOrExpand() {
            if (isDir) {
                currentPageState.dirsCollapsed = !currentPageState.dirsCollapsed
            } else {
                currentPageState.filesCollapsed = !currentPageState.filesCollapsed
            }
            explorerAdapter.notifyDataSetChanged()
        }
    }

    private class ExplorerPageState {
        var currentPage: ExplorerPage? = null
        var dirsCollapsed = false
        var filesCollapsed = false
        var scrollY = 0

        constructor()
        constructor(page: ExplorerPage?) {
            currentPage = page
        }
    }

    companion object {
        private const val LOG_TAG = "ExplorerView"
        const val VIEW_TYPE_ITEM = 0
        const val VIEW_TYPE_PAGE = 1

        //category是类别，也即"文件", "文件夹"那两个
        protected const val VIEW_TYPE_CATEGORY = 2
        private const val positionOfCategoryDir = 0
    }

    override fun onGlobalFocusChanged(oldView: View, newView: View) {
        newView.setOnKeyListener { _, _, event ->
            Log.d("TAG", "dispatchKeyEvent: ")
            if (event.keyCode == KeyEvent.KEYCODE_BACK && canGoBack()) {
                goBack()
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }
    }
}