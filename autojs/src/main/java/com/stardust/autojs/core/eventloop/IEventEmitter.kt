package com.stardust.autojs.core.eventloop

interface IEventEmitter {
    fun once(eventName: String, listener: Any): IEventEmitter

    fun on(eventName: String, listener: Any): IEventEmitter

    fun addListener(eventName: String, listener: Any): IEventEmitter

    fun emit(eventName: String, vararg args: (Any?)): Boolean

    fun eventNames(): Array<String>

    fun listenerCount(eventName: String): Int

    fun listeners(eventName: String): Array<Any>

    fun prependListener(eventName: String, listener: Any): IEventEmitter

    fun prependOnceListener(eventName: String, listener: Any): IEventEmitter

    fun removeAllListeners(): IEventEmitter

    fun removeAllListeners(eventName: String): IEventEmitter

    fun removeListener(eventName: String, listener: Any): IEventEmitter
    fun setMaxListeners(n: Int): IEventEmitter
}