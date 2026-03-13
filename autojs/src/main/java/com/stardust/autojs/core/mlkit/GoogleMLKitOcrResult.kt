package com.stardust.autojs.core.mlkit

import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.min

data class GoogleMLKitOcrResult(
    val level: Int,
    val confidence: Float = -1f,
    var text: String,
    val language: String? = null,
    val bounds: Rect? = null,
    var children: List<GoogleMLKitOcrResult>? = null
) : Comparable<GoogleMLKitOcrResult> {

    fun find(predicate: (GoogleMLKitOcrResult) -> Boolean): GoogleMLKitOcrResult? {
        return recursiveFind(this, predicate)
    }

    fun find(level: Int, predicate: (GoogleMLKitOcrResult) -> Boolean): GoogleMLKitOcrResult? {
        return find { it.level == level && predicate(it) }
    }

    fun filter(predicate: (GoogleMLKitOcrResult) -> Boolean): List<GoogleMLKitOcrResult> {
        val filterList = mutableListOf<GoogleMLKitOcrResult>()
        recursiveFilter(filterList, this, false, predicate)
        return filterList.toList()
    }

    fun filter(
        level: Int,
        predicate: (GoogleMLKitOcrResult) -> Boolean
    ): List<GoogleMLKitOcrResult> {
        return filter { it.level == level && predicate(it) }
    }

    fun toArray(level: Int): List<GoogleMLKitOcrResult> {
        val filterList = mutableListOf<GoogleMLKitOcrResult>()
        recursiveFilter(filterList, this, true) { it.level == level }
        return filterList.toList()
    }

    fun toArray(): List<GoogleMLKitOcrResult> {
        val filterList = mutableListOf<GoogleMLKitOcrResult>()
        recursiveFilter(filterList, this, true) { true }
        return filterList.toList()
    }

    fun toSortedArray(level: Int): List<GoogleMLKitOcrResult> {
        val filterList = toArray(level)
        return filterList.sorted()
    }

    fun sort() {
        if (!children.isNullOrEmpty()) {
            recursiveSort(this)
        }
    }

    fun sorted(): GoogleMLKitOcrResult {
        val newResult = this.copy()
        if (!children.isNullOrEmpty()) {
            recursiveSort(newResult)
        }
        return newResult
    }

    companion object {
        private fun recursiveFind(
            mlKitOcrResult: GoogleMLKitOcrResult,
            predicate: (GoogleMLKitOcrResult) -> Boolean
        ): GoogleMLKitOcrResult? {
            if (predicate(mlKitOcrResult) && mlKitOcrResult.level > 0) return mlKitOcrResult
            if (!mlKitOcrResult.children.isNullOrEmpty()) {
                for (child in mlKitOcrResult.children!!) {
                    recursiveFind(child, predicate)?.let { return it }
                }
            }
            return null
        }

        private fun recursiveFilter(
            filterList: MutableList<GoogleMLKitOcrResult>,
            mlKitOcrResult: GoogleMLKitOcrResult,
            childToNull: Boolean = false,
            predicate: (GoogleMLKitOcrResult) -> Boolean
        ) {
            if (predicate(mlKitOcrResult) && mlKitOcrResult.level > 0) {
                filterList.add(if (childToNull) mlKitOcrResult.copy(children = null) else mlKitOcrResult)
            }
            if (!mlKitOcrResult.children.isNullOrEmpty()) {
                for (child in mlKitOcrResult.children!!) {
                    recursiveFilter(filterList, child, childToNull, predicate)
                }
            }
            return
        }

        private fun recursiveSort(mlKitOcrResult: GoogleMLKitOcrResult) {
            if (!mlKitOcrResult.children.isNullOrEmpty()) {
                mlKitOcrResult.children = mlKitOcrResult.children!!.sorted()

                val stringBuilder = StringBuilder()
                mlKitOcrResult.children!!.forEachIndexed { index, child ->
                    mlKitOcrResult.children!!.getOrNull(index - 1)?.let { lastElement ->
                        if (child.bounds!!.top >= lastElement.bounds!!.bottom - lastElement.bounds.height() / 4f) {
                            stringBuilder.append("\n")
                        }
                    }
                    stringBuilder.append(child.text)
                }
                mlKitOcrResult.text = stringBuilder.toString()

                for (child in mlKitOcrResult.children!!) {
                    recursiveSort(child)
                }
            }
        }

    }

    override fun compareTo(other: GoogleMLKitOcrResult): Int {
        // 1. 使用固定阈值替代动态偏差（避免破坏传递性）
        val yThreshold = min(bounds!!.height(), other.bounds!!.height()) * 0.5f

        // 2. 优先比较垂直位置（确保传递性）
        val thisCenterY = (bounds.top + bounds.bottom) / 2f
        val otherCenterY = (other.bounds.top + other.bounds.bottom) / 2f

        // 3. 使用稳定的比较逻辑：同水平线：按左边界排序；不同水平线：按垂直中线排序
        return when {
            abs(thisCenterY - otherCenterY) < yThreshold -> bounds.left.compareTo(other.bounds.left)
            else -> thisCenterY.compareTo(otherCenterY)
        }
    }

}