package org.autojs.autojs.ui.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.aiselp.autox.ui.material3.components.BackTopAppBar
import com.aiselp.autox.ui.material3.theme.AppTheme
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.tool.BitmapTool
import org.autojs.autojs.tool.writeTo
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autoxjs.R
import java.io.File


/**
 * Created by Stardust on 2017/10/25.
 * Modified by wilinz on 2022/5/23
 */

open class ShortcutIconSelectActivity : BaseActivity() {
    private val mAppList: MutableList<AppItem> = ArrayList()
    private var size by mutableIntStateOf(mAppList.size)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
                if (it == null) return@rememberLauncherForActivityResult
                setResult(RESULT_OK, Intent().setData(it))
                finish()
            }
            AppTheme {
                Scaffold(
                    topBar = {
                        BackTopAppBar(
                            title = stringResource(R.string.text_select_icon),
                            onBack = { finish() },
                            actions = {
                                IconButton(onClick = { launcher.launch("image/*") }) {
                                    Icon(
                                        modifier = Modifier.size(36.dp),
                                        painter = painterResource(R.drawable.ic_insert_photo_white_48dp),
                                        contentDescription = stringResource(R.string.text_select_image)
                                    )
                                }
                            })
                    }) { it ->
                    AppList(Modifier.padding(it))
                }
            }
        }
        loadApps()
    }

    @Composable
    private fun AppList(modifier: Modifier = Modifier) {
        LazyVerticalGrid(
            columns = GridCells.FixedSize(65.dp),
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            items(size) { index ->
                val modifier = Modifier
                    .size(65.dp)
                    .padding(horizontal = 2.dp)
                val imageBitmap = mAppList[index].image
                Box(modifier = Modifier.clickable {
                    val appItem = mAppList[index]
                    selectApp(appItem)
                }) {
                    if (imageBitmap != null) {
                        Image(
                            modifier = modifier, bitmap = imageBitmap, contentDescription = null
                        )
                    } else {
                        Image(
                            modifier = modifier,
                            painter = painterResource(R.drawable.ic_ali_android),
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }

    private fun loadApps() {
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        lifecycleScope.launch {
            packages.forEach {
                if (it.icon == 0) return@forEach
                launch(Dispatchers.Default) {
                    val item = AppItem(it)
                    withContext(Dispatchers.Main) {
                        mAppList.add(item)
                        size++
                    }
                }
            }
        }
    }

    private fun selectApp(appItem: AppItem) {
        val file = File(this.cacheDir, "icon/${appItem.info.packageName}.png")
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        appItem.icon.toBitmapOrNull()?.writeTo(file)
        setResult(
            RESULT_OK, Intent().setData(file.toUri())
        )
        finish()
    }

    private inner class AppItem(var info: ApplicationInfo) {
        val icon: Drawable
            get() = info.loadIcon(packageManager)
        val image = icon.toBitmapOrNull()?.asImageBitmap()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        @JvmStatic
        @Deprecated("Use org/autojs/autojs/ui/shortcut/ShortcutIconSelectResult")
        fun getBitmapFromIntent(context: Context, data: Intent): Observable<Bitmap> {
            val packageName = data.getStringExtra(EXTRA_PACKAGE_NAME)
            if (packageName != null) {
                return Observable.fromCallable {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    BitmapTool.drawableToBitmap(drawable)
                }
            }
            val uri =
                data.data ?: return Observable.error(IllegalArgumentException("invalid intent"))
            return Observable.fromCallable {
                BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(
                        uri
                    )
                )
            }
        }

    }
}