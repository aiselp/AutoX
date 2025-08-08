package com.aiselp.autox.utils

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSupertypeOf

object Members {
    private const val TAG = "Members"

    fun findMethod(obj: KClass<*>, methodName: String, argsType: Array<KClass<*>?>): KCallable<*>? {
        return findMethod(obj, methodName, argsType.toList())
    }

    fun findMethod(obj: KClass<*>, methodName: String, argsType: List<KClass<*>?>): KCallable<*>? {
        val callable = obj.members.find { member ->
            if (member.name != methodName) return@find false
            if (member.parameters.size != argsType.size + 1) return@find false
            member.parameters.forEachIndexed { index, parameter ->
                if (index == 0) {
                    return@forEachIndexed
                } else {
                    // Check subsequent parameters against argsType
                    val argType = argsType.getOrNull(index - 1)
                    if (!checkParameter(parameter, argType)) {
                        return@find false
                    }
                }
            }
            true
        }
        return callable
    }

    fun checkParameter(v: KParameter, n: KClass<*>?): Boolean {
        if (n == null) {
            return v.type.isMarkedNullable
        }
        // Check if the parameter type is a supertype of the given class
        if (n.typeParameters.isNotEmpty()) {
            val arguments = v.type.arguments
            if (arguments.size != n.typeParameters.size) {
                return false
            }
            val nType = createType(n, arguments) ?: return false
            return v.type.isSupertypeOf(nType)
        } else {
            val nType = createType(n) ?: return false
            return v.type.isSupertypeOf(nType)
        }
    }

    fun createType(kClass: KClass<*>, type: List<KTypeProjection> = emptyList()): KType? {
        return try {
            kClass.createType(type)
        } catch (e: Exception) {
//            Log.w(TAG, e.stackTraceToString())
            null
        }
    }

}