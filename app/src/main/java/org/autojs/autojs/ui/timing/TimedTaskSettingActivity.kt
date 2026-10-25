package org.autojs.autojs.ui.timing

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiselp.autox.ui.material3.components.BackTopAppBar
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.DialogController
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.aiselp.autox.ui.material3.theme.AppTheme
import com.stardust.autojs.execution.ExecutionConfig
import com.stardust.toast
import com.stardust.util.MapBuilder
import kotlinx.coroutines.launch
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TaskReceiver
import org.autojs.autojs.timing.TimedTask
import org.autojs.autojs.timing.TimedTask.Companion.getDayOfWeekTimeFlag
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.timing.toLocalDate
import org.autojs.autojs.timing.toLocalDateTime
import org.autojs.autojs.timing.toLocalTimeOfDay
import org.autojs.autojs.ui.main.task.Task
import org.autojs.autoxjs.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Created by Stardust on 2017/11/28.
 */
class TimedTaskSettingActivity : AppCompatActivity() {
    private var mScriptFile: ScriptFile? = null
    private var mTimedTask: TimedTask? = null
    private var mIntentTask: IntentTask? = null

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId != -1L) {
            mTimedTask = TimedTaskManager.getTimedTask(taskId)
            mScriptFile = ScriptFile(mTimedTask!!.scriptPath!!)
        } else {
            val intentTaskId = intent.getLongExtra(EXTRA_INTENT_TASK_ID, -1)
            if (intentTaskId != -1L) {
                mIntentTask = TimedTaskManager.getIntentTask(intentTaskId)
                mScriptFile = ScriptFile(mIntentTask!!.scriptPath!!)
            } else {
                val path = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PATH)
                if (path.isNullOrEmpty()) {
                    return finish()
                }
                mScriptFile = ScriptFile(path)
            }
        }
        setContent {
            AppTheme {
                val viewModel = viewModel(TimedTaskSettingModel::class.java)
                var current: TaskModel by remember {
                    mIntentTask?.let {
                        viewModel.intentTask.initModel(it)
                        return@remember mutableStateOf(viewModel.intentTask)
                    }
                    mutableStateOf(viewModel.initModel(mTimedTask))
                }
                Scaffold(
                    topBar = {
                        BackTopAppBar(
                            title = stringResource(R.string.text_timed_task),
                            actions = {
                                IconButton(onClick = {
                                    if (!checkPowerOptimizations()) return@IconButton
                                    val result = runCatching {
                                        if (current == viewModel.intentTask) {
                                            val intentTask =
                                                viewModel.intentTask.createIntentTask(
                                                    this@TimedTaskSettingActivity,
                                                    mScriptFile!!.path
                                                )
                                            registerIntentTask(intentTask)
                                        }
                                        val task = current.createTimedTask(
                                            this@TimedTaskSettingActivity,
                                            mScriptFile!!.path
                                        )
                                        checkNotNull(task)
                                        registerTask(task)
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Done,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    },
                ) {
                    Column(modifier = Modifier.padding(it)) {
                        mScriptFile?.let {
                            Text(
                                text = it.name,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        FlowRow(modifier = Modifier.fillMaxWidth()) {
                            RadioOption(
                                Modifier,
                                viewModel.disposableTask === current,
                                stringResource(R.string.text_disposable_task)
                            ) { current = viewModel.disposableTask }
                            RadioOption(
                                Modifier,
                                viewModel.dailyTask === current,
                                stringResource(R.string.text_daily_task)
                            ) { current = viewModel.dailyTask }
                            RadioOption(
                                Modifier,
                                viewModel.weeklyTask === current,
                                stringResource(R.string.text_weekly_task)
                            ) { current = viewModel.weeklyTask }
                            RadioOption(
                                Modifier,
                                viewModel.intentTask === current,
                                stringResource(R.string.text_run_on_broadcast)
                            ) { current = viewModel.intentTask }

                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                ToggleAnimation(current === viewModel.disposableTask) {
                                    viewModel.disposableTask.Ui()
                                }
                                ToggleAnimation(current === viewModel.dailyTask) {
                                    viewModel.dailyTask.Ui()
                                }
                                ToggleAnimation(current === viewModel.weeklyTask) {
                                    viewModel.weeklyTask.Ui()
                                }
                                ToggleAnimation(current === viewModel.intentTask) {
                                    viewModel.intentTask.Ui()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ToggleAnimation(visible: Boolean, content: @Composable () -> Unit) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(),
            exit = fadeOut()
        ) {
            content()
        }
    }

    @Composable
    private fun RadioOption(
        modifier: Modifier = Modifier,
        selected: Boolean,
        label: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .then(modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(text = label)
        }
    }

    private fun checkPowerOptimizations(): Boolean {
        val powerManager = (getSystemService(POWER_SERVICE) as PowerManager)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")),
            )
            return false
        } else return true
    }

    private fun registerTask(timedTask: TimedTask) {
        val oldTask = mTimedTask
        if (oldTask == null) {
            TimedTaskManager.addTask(timedTask)
        } else {
            timedTask.id = oldTask.id
            TimedTaskManager.updateTask(timedTask)
        }
        toast(this, R.string.text_already_create)
        if (mIntentTask != null) {
            TimedTaskManager.removeTask(mIntentTask!!)
        }
        finish()
    }


    private fun registerIntentTask(task: IntentTask) {
        if (task.action.isNullOrEmpty()) {
            toast(this, R.string.error_empty_selection)
            return
        }

        if (mIntentTask != null) {
            task.id = mIntentTask!!.id
            TimedTaskManager.updateTask(task)
        } else {
            TimedTaskManager.addTask(task)
        }
        toast(this, R.string.text_already_create)
        if (mTimedTask != null) {
            TimedTaskManager.removeTask(mTimedTask!!)
        }
        finish()
    }

    interface TaskModel {
        fun createTimedTask(context: Context, scriptFile: String): TimedTask?
    }

    @OptIn(ExperimentalMaterial3Api::class)
    class TimedTaskSettingModel : ViewModel() {
        val disposableTask = DisposableTask()
        val dailyTask = DailyTask()
        val weeklyTask = WeeklyTask()
        val intentTask = IntentTask()

        fun initModel(timedTask: TimedTask?): TaskModel {
            if (timedTask == null) return disposableTask
            if (timedTask.timeFlag == TimedTask.FLAG_DISPOSABLE.toLong()) {
                disposableTask.initModel(timedTask)
                return disposableTask
            }
            if (timedTask.timeFlag == TimedTask.FLAG_EVERYDAY.toLong()) {
                dailyTask.initModel(timedTask)
                return dailyTask
            }
            weeklyTask.initModel(timedTask)
            return weeklyTask
        }

        class DisposableTask : TaskModel {
            private val time = LocalTime.now()
            var timePickerState = TimePickerState(time.hour, time.minute, true)
            val datePickerState =
                DatePickerState(Locale.getDefault(), initialSelectedDateMillis = System.currentTimeMillis())
            val timeDialog = DialogController()
            val dateDialog = DialogController()

            override fun createTimedTask(context: Context, scriptFile: String): TimedTask? {
                val date = datePickerState.selectedDateMillis.let {
                    if (it == null) return null
                    it.toLocalDate()
                }
                val dateTime = date.atTime(timePickerState.hour, timePickerState.minute)
                if (dateTime.isBefore(LocalDateTime.now())) {
                    toast(context, R.string.text_disposable_task_time_before_now)
                    return null
                }
                return TimedTask.disposableTask(dateTime, scriptFile, ExecutionConfig.default)
            }

            fun initModel(timedTask: TimedTask) {
                val dateTime = timedTask.millis.toLocalDateTime()
                datePickerState.selectedDateMillis = timedTask.millis
                timePickerState = TimePickerState(dateTime.hour, dateTime.minute, true)
            }
        }

        class DailyTask : TaskModel {
            private val time = LocalTime.now()
            var timePickerState = TimePickerState(time.hour, time.minute, true)
            override fun createTimedTask(context: Context, scriptFile: String): TimedTask? {
                val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                return TimedTask.dailyTask(time, scriptFile, ExecutionConfig.default)
            }

            fun initModel(timedTask: TimedTask) {
                val timeOfDay = timedTask.millis.toLocalTimeOfDay()
                timePickerState = TimePickerState(timeOfDay.hour, timeOfDay.minute, true)
            }
        }

        class WeeklyTask : TaskModel {
            private val time = LocalTime.now()
            var timePickerState = TimePickerState(time.hour, time.minute, true)
            val chooseDays = mutableSetOf<Int>()
            override fun createTimedTask(context: Context, scriptFile: String): TimedTask? {
                var timeFlag: Long = 0
                for (i in chooseDays) {
                    timeFlag = timeFlag or getDayOfWeekTimeFlag(i)
                }
                if (timeFlag == 0L) {
                    toast(context, R.string.text_weekly_task_should_check_day_of_week)
                    return null
                }
                val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                return TimedTask.weeklyTask(time, timeFlag, scriptFile, ExecutionConfig.default)
            }

            fun initModel(timedTask: TimedTask) {
                val timeOfDay = timedTask.millis.toLocalTimeOfDay()
                timePickerState = TimePickerState(timeOfDay.hour, timeOfDay.minute, true)
                for (i in 1..7) {
                    if (timedTask.hasDayOfWeek(i)) {
                        chooseDays.add(i)
                    }
                }
            }
        }

        class IntentTask : TaskModel {
            var action: String? = null
            override fun createTimedTask(context: Context, scriptFile: String): TimedTask? {
                return null
            }

            fun createIntentTask(
                context: Context,
                scriptFile: String
            ): org.autojs.autojs.timing.IntentTask {
                val task = org.autojs.autojs.timing.IntentTask()
                task.action = action
                task.scriptPath = scriptFile
                task.isLocal = action == DynamicBroadcastReceivers.ACTION_STARTUP
                return task
            }

            fun initModel(intentTask: org.autojs.autojs.timing.IntentTask) {
                action = intentTask.action
            }

        }
    }

    @Composable
    private fun TimedTaskSettingModel.IntentTask.Ui() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            val otherAction = "other"
            var u: String? by remember {
                if (action != null) {
                    if (ACTION_DESC_MAP.containsKey(action)) {
                        mutableStateOf(action)
                    } else mutableStateOf(otherAction)
                } else mutableStateOf(null)
            }
            for ((action, id) in ACTION_DESC_MAP) {
                RadioOption(
                    modifier = Modifier.fillMaxWidth(),
                    selected = action == u,
                    label = stringResource(id)
                ) {
                    this@Ui.action = action
                    u = action
                }
            }

            var otherActionValue by remember { mutableStateOf(action ?: "") }
            LaunchedEffect(otherActionValue) {
                if (u == otherAction) {
                    action = otherActionValue
                }
            }
            RadioOption(
                modifier = Modifier.fillMaxWidth(),
                selected = u == otherAction,
                label = stringResource(R.string.text_run_on_other_broadcast)
            ) { u = otherAction;action = otherActionValue }
            Column {
                TextField(value = otherActionValue,
                    label = { Text(text = stringResource(R.string.text_broadcast_action)) },
                    onValueChange = { otherActionValue = it }
                )
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
    @Composable
    private fun TimedTaskSettingModel.WeeklyTask.Ui() {
        val days = listOf(
            stringResource(R.string.text_day1),
            stringResource(R.string.text_day2),
            stringResource(R.string.text_day3),
            stringResource(R.string.text_day4),
            stringResource(R.string.text_day5),
            stringResource(R.string.text_day6),
            stringResource(R.string.text_day7),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TimeInput(state = timePickerState)
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                days.forEachIndexed { index, day ->
                    var selected by remember { mutableStateOf(chooseDays.contains(index + 1)) }
                    fun checked(selected: Boolean) {
                        if (selected) {
                            chooseDays.add(index + 1)
                        } else {
                            chooseDays.remove(index + 1)
                        }
                    }
                    Row(
                        modifier = Modifier.clickable {
                            selected = !selected
                            checked(selected)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = selected,
                            onCheckedChange = {
                                selected = it
                                checked(it)
                            })
                        Text(text = day)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TimedTaskSettingModel.DailyTask.Ui() {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TimeInput(state = timePickerState)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TimedTaskSettingModel.DisposableTask.Ui() {
        val scope = rememberCoroutineScope()
        timeDialog.BaseDialog(
            onDismissRequest = { scope.launch { timeDialog.dismiss() } },
            title = { DialogTitle("设置时间") },
            onPositiveClick = { scope.launch { timeDialog.dismiss() } },
            positiveText = stringResource(R.string.ok)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimeInput(state = timePickerState)
            }
        }
        dateDialog.BaseDialog(
            onDismissRequest = { scope.launch { dateDialog.dismiss() } },
            title = { DialogTitle("设置日期") },
            onPositiveClick = { scope.launch { dateDialog.dismiss() } },
            positiveText = stringResource(R.string.ok)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DatePicker(state = datePickerState)
            }
        }
        Column {
            val iconModifier = Modifier.size(24.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { timeDialog.show() } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = iconModifier,
                    painter = painterResource(R.drawable.ic_access_time_black_48dp),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${timePickerState.hour}:${timePickerState.minute}")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { dateDialog.show() } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = iconModifier,
                    painter = painterResource(R.drawable.ic_date_range_black_48dp),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                val date = datePickerState.selectedDateMillis.let {
                    if (it == null) LocalDate.now()
                    else it.toLocalDate()
                }
                Text(text = "${date.year}-${date.monthValue}-${date.dayOfMonth}")
            }
        }
    }


    companion object {
        const val EXTRA_INTENT_TASK_ID: String = "intent_task_id"
        const val EXTRA_TASK_ID: String = TaskReceiver.EXTRA_TASK_ID

        private const val LOG_TAG = "TimedTaskSettings"

        fun reviseTimeTask(context: Context, task: Task.PendingTask) {
            val intent = Intent(context, TimedTaskSettingActivity::class.java)
            task.timedTask?.let {
                intent.putExtra(EXTRA_TASK_ID, it.id)
                context.startActivity(intent)
                return
            }
            task.mIntentTask?.let {
                intent.putExtra(EXTRA_INTENT_TASK_ID, it.id)
                context.startActivity(intent)
                return
            }
        }

        val ACTION_DESC_MAP: Map<String, Int> = MapBuilder<String, Int>()
            .put(DynamicBroadcastReceivers.ACTION_STARTUP, R.string.text_run_on_startup)
            .put(Intent.ACTION_BOOT_COMPLETED, R.string.text_run_on_boot)
            .put(Intent.ACTION_SCREEN_OFF, R.string.text_run_on_screen_off)
            .put(Intent.ACTION_SCREEN_ON, R.string.text_run_on_screen_on)
            .put(Intent.ACTION_USER_PRESENT, R.string.text_run_on_screen_unlock)
            .put(Intent.ACTION_BATTERY_CHANGED, R.string.text_run_on_battery_change)
            .put(Intent.ACTION_POWER_CONNECTED, R.string.text_run_on_power_connect)
            .put(Intent.ACTION_POWER_DISCONNECTED, R.string.text_run_on_power_disconnect)
            .put(ConnectivityManager.CONNECTIVITY_ACTION, R.string.text_run_on_conn_change)
            .put(Intent.ACTION_PACKAGE_ADDED, R.string.text_run_on_package_install)
            .put(Intent.ACTION_PACKAGE_REMOVED, R.string.text_run_on_package_uninstall)
            .put(Intent.ACTION_PACKAGE_REPLACED, R.string.text_run_on_package_update)
            .put(Intent.ACTION_HEADSET_PLUG, R.string.text_run_on_headset_plug)
            .put(Intent.ACTION_CONFIGURATION_CHANGED, R.string.text_run_on_config_change)
            .put(Intent.ACTION_TIME_TICK, R.string.text_run_on_time_tick)
            .build()
    }
}
