package com.aiselp.autox.utils

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val Context.multiProcessAppConfig: DataStore<MultiProcessAppConfig>
    get() = createMultiProcessAppConfig(this)

private var multiProcessAppConfig: DataStore<MultiProcessAppConfig>? = null

@Synchronized
fun createMultiProcessAppConfig(context: Context): DataStore<MultiProcessAppConfig> {
    if (multiProcessAppConfig == null) {
        multiProcessAppConfig = MultiProcessDataStoreFactory.create(
            serializer = MultiProcessAppConfigSerializer,
            produceFile = { context.dataStoreFile("multiProcessAppConfig.pb") },
            corruptionHandler = null,
        )
        return multiProcessAppConfig!!
    } else return multiProcessAppConfig!!
}

@Serializable
data class MultiProcessAppConfig(
    val isRunningVolumeControlEnabled: Boolean
)

object MultiProcessAppConfigSerializer : Serializer<MultiProcessAppConfig> {
    override val defaultValue = MultiProcessAppConfig(
        isRunningVolumeControlEnabled = false
    )

    override suspend fun readFrom(input: InputStream): MultiProcessAppConfig {
        return try {
            Json.decodeFromString(
                MultiProcessAppConfig.serializer(),
                input.readBytes().decodeToString()
            )
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read Time", serialization)
        }
    }

    override suspend fun writeTo(
        t: MultiProcessAppConfig,
        output: OutputStream
    ) {
        output.write(Json.encodeToString(MultiProcessAppConfig.serializer(), t).encodeToByteArray())
    }

}