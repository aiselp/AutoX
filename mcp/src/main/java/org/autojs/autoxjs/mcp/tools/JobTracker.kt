package org.autojs.autoxjs.mcp.tools

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class JobStatus(
    val jobId: Int,
    val name: String,
    val status: Status,
    val message: String? = null
) {
    enum class Status {
        SUBMITTED, RUNNING, SUCCESS, FAILED, CANCELED
    }
}

class JobTracker {
    private val idGen = AtomicInteger(1)
    private val jobs = ConcurrentHashMap<Int, JobStatus>()

    fun newJob(name: String): JobStatus {
        val id = idGen.getAndIncrement()
        val status = JobStatus(id, name, JobStatus.Status.SUBMITTED)
        jobs[id] = status
        return status
    }

    fun update(id: Int, status: JobStatus.Status, message: String? = null) {
        val existing = jobs[id]
        if (existing != null) {
            jobs[id] = existing.copy(status = status, message = message)
        }
    }

    fun get(id: Int): JobStatus? = jobs[id]

    fun recent(limit: Int = 20): List<JobStatus> = jobs.values.sortedBy { it.jobId }.takeLast(limit)
}
