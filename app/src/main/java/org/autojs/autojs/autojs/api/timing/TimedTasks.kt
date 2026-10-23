package org.autojs.autojs.autojs.api.timing

import com.stardust.autojs.execution.ExecutionConfig
import org.autojs.autojs.timing.TimedTask
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.timing.toLocalDateTime
import java.time.LocalTime

object TimedTasks {

    fun daily(path: String, hour: Int, minute: Int) {
        TimedTaskManager.addTask(
            TimedTask.dailyTask(
                LocalTime.of(hour, minute),
                path,
                ExecutionConfig()
            )
        )
    }

    fun disposable(path: String, millis: Long) {
        TimedTaskManager.addTask(
            TimedTask.disposableTask(
                millis.toLocalDateTime(),
                path,
                ExecutionConfig()
            )
        )
    }

    fun weekly(path: String, millis: Long, timeFlag: Long) {
        TimedTaskManager.addTask(
            TimedTask.weeklyTask(
                millis.toLocalDateTime().toLocalTime(),
                timeFlag,
                path,
                ExecutionConfig()
            )
        )
    }

}