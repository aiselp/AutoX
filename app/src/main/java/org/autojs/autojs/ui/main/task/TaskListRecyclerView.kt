package org.autojs.autojs.ui.main.task

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bignerdranch.expandablerecyclerview.ChildViewHolder
import com.bignerdranch.expandablerecyclerview.ExpandableRecyclerAdapter
import com.bignerdranch.expandablerecyclerview.ParentViewHolder
import com.stardust.autojs.script.AutoFileSource
import com.stardust.autojs.servicecomponents.BinderScriptListener
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.autojs.servicecomponents.TaskInfo
import com.stardust.autojs.workground.WrapContentLinearLayoutManager
import com.yqritc.recyclerviewflexibledivider.HorizontalDividerItemDecoration
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.storage.database.ModelChange
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTask
import org.autojs.autojs.timing.TimedTaskManager.intentTaskChanges
import org.autojs.autojs.timing.TimedTaskManager.timeTaskChanges
import org.autojs.autojs.ui.explorer.FileIcon
import org.autojs.autojs.ui.explorer.FileInfo
import org.autojs.autojs.ui.main.task.Task.PendingTask
import org.autojs.autojs.ui.main.task.TaskGroup.PendingTaskGroup
import org.autojs.autojs.ui.main.task.TaskGroup.RunningTaskGroup
import org.autojs.autojs.ui.timing.TimedTaskSettingActivity
import org.autojs.autoxjs.R

/**
 * Created by Stardust on 2017/3/24.
 */
class TaskListRecyclerView : RecyclerView {
    private val mRunningTaskGroup: RunningTaskGroup = RunningTaskGroup(context)
    private val mPendingTaskGroup: PendingTaskGroup = PendingTaskGroup(context)
    private val mTaskGroups: MutableList<TaskGroup> =
        mutableListOf(mRunningTaskGroup, mPendingTaskGroup)
    private var mAdapter: Adapter = Adapter(mTaskGroups)
    private var mTimedTaskChangeDisposable: Disposable? = null
    private var mIntentTaskChangeDisposable: Disposable? = null
    private val mScriptExecutionListener: BinderScriptListener =
        object : BinderScriptListener {
            override fun onStart(taskInfo: TaskInfo) {
                post { mAdapter.notifyChildInserted(0, mRunningTaskGroup.addTask(taskInfo)) }
            }

            override fun onSuccess(taskInfo: TaskInfo) = onFinish(taskInfo)
            override fun onException(taskInfo: TaskInfo, e: Throwable) = onFinish(taskInfo)


            private fun onFinish(task: TaskInfo) {
                post {
                    val i = mRunningTaskGroup.removeTask(task)
                    if (i >= 0) {
                        mAdapter.notifyChildRemoved(0, i)
                    } else {
                        refresh()
                    }
                }
            }
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) :
            super(context, attrs, defStyle)

    init {
        setLayoutManager(WrapContentLinearLayoutManager(context))
        addItemDecoration(
            HorizontalDividerItemDecoration.Builder(context)
                .color(ContextCompat.getColor(context, R.color.divider))
                .size(2)
                .marginResId(
                    R.dimen.script_and_folder_list_divider_left_margin,
                    R.dimen.script_and_folder_list_divider_right_margin
                )
                .showLastDivider()
                .build()
        )
        setAdapter(mAdapter)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun refresh() {
        GlobalScope.launch {
            for (group in mTaskGroups) {
                group.refresh()
            }
            mAdapter = Adapter(mTaskGroups)
            withContext(Dispatchers.Main) {
                setAdapter(mAdapter)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EngineController.registerGlobalScriptExecutionListener(mScriptExecutionListener)
        mTimedTaskChangeDisposable = timeTaskChanges
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { taskChange: ModelChange<TimedTask> -> onTaskChange(taskChange) }
        mIntentTaskChangeDisposable = intentTaskChanges
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { taskChange: ModelChange<IntentTask> -> onTaskChange(taskChange) }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            refresh()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EngineController.unregisterGlobalScriptExecutionListener(mScriptExecutionListener)
        mTimedTaskChangeDisposable!!.dispose()
        mIntentTaskChangeDisposable!!.dispose()
    }

    fun onTaskChange(taskChange: ModelChange<*>) {
        if (taskChange.action == ModelChange.INSERT) {
            mAdapter.notifyChildInserted(1, mPendingTaskGroup.addTask(taskChange.data))
        } else if (taskChange.action == ModelChange.DELETE) {
            val i = mPendingTaskGroup.removeTask(taskChange.data)
            if (i >= 0) {
                mAdapter.notifyChildRemoved(1, i)
            } else {
                Log.w(LOG_TAG, "data inconsistent on change: $taskChange")
                refresh()
            }
        } else if (taskChange.action == ModelChange.UPDATE) {
            val i = mPendingTaskGroup.updateTask(taskChange.data)
            if (i >= 0) {
                mAdapter.notifyChildChanged(1, i)
            } else {
                refresh()
            }
        }
    }

    private inner class Adapter(parentList: List<TaskGroup?>) :
        ExpandableRecyclerAdapter<TaskGroup, TaskInfo, TaskGroupViewHolder, TaskViewHolder>(
            parentList
        ) {
        override fun onCreateParentViewHolder(
            parentViewGroup: ViewGroup,
            viewType: Int
        ): TaskGroupViewHolder {
            return TaskGroupViewHolder(ComposeView(context))
        }

        override fun onCreateChildViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
            return TaskViewHolder(ComposeView(context))
        }

        override fun onBindParentViewHolder(
            viewHolder: TaskGroupViewHolder,
            parentPosition: Int,
            taskGroup: TaskGroup
        ) {
            viewHolder.title = taskGroup.title
        }

        override fun onBindChildViewHolder(
            viewHolder: TaskViewHolder,
            parentPosition: Int,
            childPosition: Int,
            task: TaskInfo
        ) {
            viewHolder.bind(task)
        }
    }

    internal inner class TaskViewHolder(itemView: ComposeView) : ChildViewHolder<Task?>(itemView) {
        var firstChar by mutableStateOf("J")
        var name by mutableStateOf("J")
        var desc by mutableStateOf("J")
        private var mTask: TaskInfo? = null
        var firstCharBackground by mutableStateOf(Color(0xFF2196F3))

        init {
            itemView.setContent {
                TaskItem(this)
            }
        }

        fun bind(task: TaskInfo) {
            mTask = task
            name = task.name
            desc = task.desc
            if (AutoFileSource.ENGINE == mTask!!.engineName) {
                firstChar = "R"
                firstCharBackground = Color(0xFFFD999A)
            } else {
                firstChar = "J"
                firstCharBackground = Color(0xFF99CC99)
            }
        }

        fun stop() {
            val task = mTask ?: return
            if (task is PendingTask) {
                task.cancel()
            } else
                EngineController.stopScript(task.id)
        }

        fun onItemClick() {
            if (mTask is PendingTask) {
                TimedTaskSettingActivity.reviseTimeTask(context, mTask as PendingTask)
            }
        }
    }

    private inner class TaskGroupViewHolder(itemView: ComposeView) :
        ParentViewHolder<TaskGroup?, TaskInfo>(itemView) {
        var title by mutableStateOf("")
        private var exp by mutableStateOf(isExpanded)

        init {
            itemView.setContent {
                TaskGroup(title, exp) {
                    if (isExpanded) {
                        collapseView()
                    } else {
                        expandView()
                    }
                }
            }
        }

        override fun onExpansionToggled(expanded: Boolean) {
            exp = expanded
        }
    }

    companion object {
        private const val LOG_TAG = "TaskListRecyclerView"
    }
}

@Composable
fun TaskGroup(title: String, expanded: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rotation by animateFloatAsState(if (expanded) -90f else 0f)
            Icon(
                modifier = Modifier.rotate(rotation),
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun TaskItem(
    item: TaskListRecyclerView.TaskViewHolder,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp)
            .clickable { item.onItemClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(item.firstChar, item.firstCharBackground)
        Spacer(Modifier.width(16.dp))
        FileInfo(modifier = Modifier.weight(1f), name = item.name, desc = item.desc)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tint = Color(0xFFA9AAAB)
            val iconModifier = Modifier.size(24.dp)

            IconButton(
                onClick = { item.stop() }
            ) {
                Icon(
                    modifier = iconModifier,
                    painter = painterResource(R.drawable.ic_close_gray600_48dp),
                    contentDescription = null,
                    tint = tint
                )
            }
        }
    }
}