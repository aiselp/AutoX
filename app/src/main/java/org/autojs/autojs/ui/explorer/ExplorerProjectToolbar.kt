package org.autojs.autojs.ui.explorer

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stardust.autojs.project.ProjectConfig
import com.stardust.autojs.project.ProjectConfig.Companion.fromProject
import com.stardust.autojs.servicecomponents.EngineController
import com.stardust.pio.PFile
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.ui.build.BuildActivity.Companion.start
import org.autojs.autojs.ui.build.ProjectConfigActivity
import org.autojs.autoxjs.R
import java.io.File

class ExplorerProjectToolbar(val context: Context) {
    private var mProjectConfig: ProjectConfig? by mutableStateOf(null)
    private var mDirectory: PFile? = null
    private var projectName by mutableStateOf("")
    var disposable: Disposable? = null
    var visibility by mutableStateOf(true)

    @Composable
    fun Content() {
        if (visibility) return
        ElevatedCard(
            onClick = { edit() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(16.dp))
                if (mProjectConfig?.icon != null) {
                    val file = File(mProjectConfig!!.projectDirectory, mProjectConfig!!.icon)
                    AsyncImage(
                        model = file,
                        modifier = Modifier.size(32.dp),
                        contentDescription = null
                    )
                } else Image(
                    painter = painterResource(R.drawable.ic_project),
                    modifier = Modifier.size(32.dp),
                    contentDescription = null
                )
                Spacer(Modifier.width(16.dp))
                Text(text = projectName, modifier = Modifier.weight(1f), fontSize = 18.sp)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tint = Color(0xFF3FC4C4)
                    val modifier = Modifier
                        .height(40.dp)
                        .width(35.dp)
                        .clip(RoundedCornerShape(32.dp))
                    val iconModifier = Modifier.size(22.dp)
                    Box(
                        modifier.clickable { run() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = iconModifier,
                            painter = painterResource(R.drawable.ic_run_gray),
                            contentDescription = null,
                            tint = tint
                        )
                    }
                    Box(
                        modifier.clickable { build() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = iconModifier,
                            painter = painterResource(R.drawable.ic_android_fill),
                            contentDescription = null,
                            tint = tint
                        )
                    }
                    Box(
                        modifier.clickable { sync() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            modifier = iconModifier,
                            painter = painterResource(R.drawable.ic_sync),
                            contentDescription = null,
                            tint = tint
                        )
                    }
                }
            }
        }
    }

    fun setProject(dir: PFile) {
        CoroutineScope(Dispatchers.Main).launch {
            mProjectConfig = withContext(Dispatchers.IO) {
                fromProject(File(dir.path))
            }

            if (mProjectConfig == null) {
                visibility = true
                return@launch
            }
            mDirectory = dir
            projectName = mProjectConfig!!.name ?: ""
        }
    }

    fun refresh() {
        if (mDirectory != null) {
            setProject(mDirectory!!)
        }
    }

    fun run() {
        EngineController.scope.launch {
            EngineController.launchProject(fromProject(mDirectory!!)!!)
        }
    }

    fun build() {
        start(context, mDirectory!!.path)
    }

    fun sync() {
    }

    fun edit() = ProjectConfigActivity.editProjectConfig(context, mDirectory!!)
}