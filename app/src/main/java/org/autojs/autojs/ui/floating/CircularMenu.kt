package org.autojs.autojs.ui.floating

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.aiselp.autox.ui.material3.components.AlertDialog
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.ComposeDialog
import com.aiselp.autox.ui.material3.components.DialogText
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.makeramen.roundedimageview.RoundedImageView
import com.stardust.app.DialogUtils
import com.stardust.autojs.core.record.Recorder
import com.stardust.enhancedfloaty.FloatyService
import com.stardust.enhancedfloaty.FloatyWindow
import com.stardust.toast
import com.stardust.util.ClipboardUtil
import com.stardust.view.accessibility.AccessibilityService.Companion.instance
import com.stardust.view.accessibility.LayoutInspector.CaptureAvailableListener
import com.stardust.view.accessibility.NodeInfo
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.autojs.autojs.Pref
import org.autojs.autojs.autojs.AutoJs
import org.autojs.autojs.autojs.record.GlobalActionRecorder
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.tool.AccessibilityServiceTool
import org.autojs.autojs.tool.RootTool
import org.autojs.autojs.ui.explorer.ExplorerViewKt
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autoxjs.R
import org.autojs.autoxjs.databinding.CircularActionMenuBinding

/**
 * Created by Stardust on 2017/10/18.
 */
class CircularMenu(context: Context?) : Recorder.OnStateChangedListener, CaptureAvailableListener {
    class StateChangeEvent(val currentState: Int, val previousState: Int)

    private var mWindow: CircularMenuWindow? = null
    private var mState = 0
    private var mActionViewIcon: RoundedImageView? = null
    private val mContext: Context = ContextThemeWrapper(context, R.style.AppTheme)
    private val mRecorder: GlobalActionRecorder
    private var mRunningPackage: String? = null
    private var mRunningActivity: String? = null
    private var captureDeferred: CompletableDeferred<NodeInfo>? = null

    init {
        initFloaty()
        setupListeners()
        mRecorder = GlobalActionRecorder.getSingleton(context)
        mRecorder.addOnStateChangedListener(this)
        AutoJs.getInstance().layoutInspector.addCaptureAvailableListener(this)
    }

    private fun setupListeners() {
        mWindow?.setOnActionViewClickListener {
            if (mState == STATE_RECORDING) {
                stopRecord()
            } else if (mWindow?.isExpanded == true) {
                mWindow?.collapse()
            } else {
                captureDeferred = CompletableDeferred()
                AutoJs.getInstance().layoutInspector.captureCurrentWindow()
                mWindow?.expand()
            }
        }
    }

    private fun initFloaty() {
        mWindow = CircularMenuWindow(mContext, object : CircularMenuFloaty {
            override fun inflateActionView(
                service: FloatyService,
                window: CircularMenuWindow
            ): View {
                val actionView = View.inflate(service, R.layout.circular_action_view, null)
                mActionViewIcon = actionView.findViewById(R.id.icon)
                return actionView
            }

            override fun inflateMenuItems(
                service: FloatyService,
                window: CircularMenuWindow
            ): CircularActionMenu {
                val menu = View.inflate(
                    ContextThemeWrapper(service, R.style.AppTheme),
                    R.layout.circular_action_menu,
                    null
                ) as CircularActionMenu
                val binding = CircularActionMenuBinding.bind(menu)
                setBinding(binding)
                return menu
            }
        })
        mWindow?.setKeepToSideHiddenWidthRadio(0.25f)
        FloatyService.addWindow(mWindow)
    }

    fun setBinding(binding: CircularActionMenuBinding) {
        binding.scriptList.setOnClickListener { showScriptList() }
        binding.record.setOnClickListener { startRecord() }
        binding.layoutInspect.setOnClickListener { inspectLayout() }
        binding.settings.setOnClickListener { settings() }
        binding.stopAllScripts.setOnClickListener { stopAllScripts() }
    }

    fun showScriptList() {
        mWindow?.collapse()
        val explorerView = ExplorerViewKt(mContext)
        explorerView.setExplorer(
            Explorers.workspace(),
            ExplorerDirPage.createRoot(Pref.getScriptDirPath())
        )
        explorerView.setDirectorySpanSize(2)
        val dialog = ComposeDialog(mContext).apply {
            setContent {
                BackHandler {
                    if (explorerView.canGoBack())
                        explorerView.goBack()
                    else dismiss()
                }
                BaseDialog(
                    title = { DialogTitle(stringResource(R.string.text_run_script)) },
                    negativeText = stringResource(R.string.cancel),
                    onNegativeClick = { dismiss() }
                ) {
                    AndroidView(factory = { explorerView })
                }
            }
            setCancelable(false)
        }
        explorerView.setOnItemOperatedListener {
            dialog.dismiss()
        }
        explorerView.setOnItemClickListener { _, item ->
            Log.d(TAG, "onItemClick: ${item?.path}")
            item?.let { Scripts.run(item.toScriptFile()) }
        }
        DialogUtils.showDialog(dialog)
    }

    fun startRecord() {
        mWindow?.collapse()
        if (!RootTool.isRootAvailable()) {
            DialogUtils.showDialog(
                ComposeDialog(mContext).apply {
                    setContent {
                        AlertDialog(
                            title = stringResource(R.string.text_device_not_rooted),
                            content = stringResource(R.string.prompt_device_not_rooted),
                            negativeText = stringResource(R.string.cancel),
                            onNegativeClick = { dismiss() },
                            neutralText = stringResource(R.string.text_device_rooted),
                            onNeutralClick = { dismiss(); mRecorder.start() }
                        )
                    }
                })
        } else {
            mRecorder.start()
        }
    }

    private fun setState(state: Int) {
        val previousState = mState
        mState = state
        mActionViewIcon?.setImageResource(if (mState == STATE_RECORDING) R.drawable.ic_ali_record else IC_ACTION_VIEW)
        //  mActionViewIcon.setBackgroundColor(mState == STATE_RECORDING ? mContext.getResources().getColor(R.color.color_red) :
        //        Color.WHITE);
        mActionViewIcon?.setBackgroundResource(if (mState == STATE_RECORDING) R.drawable.circle_red else R.drawable.circle_white)
        val padding =
            mContext.resources.getDimension(if (mState == STATE_RECORDING) R.dimen.padding_circular_menu_recording else R.dimen.padding_circular_menu_normal)
                .toInt()
        mActionViewIcon?.setPadding(padding, padding, padding, padding)
        STATE_CHANGE_PUBLISHER.onNext(StateChangeEvent(mState, previousState))
    }

    private fun stopRecord() {
        mRecorder.stop()
    }

    fun inspectLayout() {
        mWindow?.collapse()
        val dialog = ComposeDialog(mContext).apply {
            setContent {
                BaseDialog(title = { DialogTitle(stringResource(R.string.text_inspect_layout)) }) {
                    Column {
                        SettingsItem(
                            title = stringResource(R.string.text_inspect_layout_bounds),
                            icon = R.drawable.ic_circular_menu_bounds
                        ) { dismiss(); showLayoutBounds() }
                        SettingsItem(
                            title = stringResource(R.string.text_inspect_layout_hierarchy),
                            icon = R.drawable.ic_circular_menu_hierarchy
                        ) { dismiss(); showLayoutHierarchy() }
                    }
                }
            }
        }
        DialogUtils.showDialog(dialog)
    }

    fun showLayoutBounds() {
        inspectLayout { rootNode -> rootNode?.let { LayoutBoundsFloatyWindow(it) } }
    }

    fun showLayoutHierarchy() {
        inspectLayout { mRootNode -> mRootNode?.let { LayoutHierarchyFloatyWindow(it) } }
    }

    private fun inspectLayout(windowCreator: (NodeInfo?) -> FloatyWindow?) {
        if (instance == null) {
            toast(mContext, R.string.text_no_accessibility_permission_to_capture)
            AccessibilityServiceTool.goToAccessibilitySetting()
            return
        }
        val progress = ComposeDialog(mContext).apply {
            setContent {
                BaseDialog(title = {}) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(8.dp))
                        DialogText(text = stringResource(R.string.text_layout_inspector_is_dumping))
                    }
                }
            }
            setCanceledOnTouchOutside(false)
        }
        DialogUtils.showDialog(progress)
        progress.lifecycleScope.launch {
            try {
                val capture = captureDeferred?.await()
                if (progress.isShowing) {
                    progress.dismiss()
                    windowCreator.invoke(capture)?.let { FloatyService.addWindow(it) }
                }
            } catch (_: Exception) {
                progress.dismiss()
            }
        }
    }

    fun stopAllScripts() {
        mWindow?.collapse()
        AutoJs.getInstance().scriptEngineService.stopAllAndToast()
    }

    override fun onCaptureAvailable(capture: NodeInfo?) {
        if (capture != null) {
            captureDeferred?.complete(capture)
        }
    }

    @Composable
    fun SettingsItem(title: String, @DrawableRes icon: Int, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .height(45.dp)
                .fillMaxWidth()
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(icon), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
        }
    }

    fun settings() {
        mWindow?.collapse()
        mRunningPackage = AutoJs.getInstance().infoProvider.getLatestPackageByUsageStatsIfGranted()
        mRunningActivity = AutoJs.getInstance().infoProvider.latestActivity
        val dialog = ComposeDialog(mContext)
        dialog.setContent {
            BaseDialog(
                title = { DialogTitle(stringResource(R.string.text_more)) }
            ) {
                Column {
                    SettingsItem(
                        title = stringResource(R.string.text_accessibility_settings),
                        icon = R.drawable.ic_settings
                    ) { dialog.dismiss(); enableAccessibilityService() }
                    SettingsItem(
                        title = stringResource(R.string.text_current_package) + (
                                if (mRunningPackage?.isEmpty() == false) mRunningPackage
                                else "unknown"),
                        icon = R.drawable.ic_android_fill
                    ) { dialog.dismiss(); copyPackageName() }
                    SettingsItem(
                        title = stringResource(R.string.text_current_activity) +
                                (if (mRunningActivity?.isEmpty() == false) mRunningActivity else "unknown"),
                        icon = R.drawable.ic_window
                    ) { dialog.dismiss(); copyActivityName() }
                    SettingsItem(
                        title = stringResource(R.string.text_open_main_activity),
                        icon = R.drawable.ic_home_light
                    ) { dialog.dismiss(); openLauncher() }
                    SettingsItem(
                        title = stringResource(R.string.text_pointer_location),
                        icon = R.drawable.ic_coordinate
                    ) { dialog.dismiss(); togglePointerLocation() }
                    SettingsItem(
                        title = stringResource(R.string.text_exit_floating_window),
                        icon = R.drawable.ic_close
                    ) { dialog.dismiss(); close() }
                }
            }
        }
        DialogUtils.showDialog(dialog)
    }

    fun enableAccessibilityService() {
        AccessibilityServiceTool.enableAccessibilityService()
    }

    fun copyPackageName() {
        if (TextUtils.isEmpty(mRunningPackage)) return
        ClipboardUtil.setClip(mContext, mRunningPackage)
        Toast.makeText(mContext, R.string.text_already_copy_to_clip, Toast.LENGTH_SHORT).show()
    }

    fun copyActivityName() {
        if (TextUtils.isEmpty(mRunningActivity)) return
        ClipboardUtil.setClip(mContext, mRunningActivity)
        Toast.makeText(mContext, R.string.text_already_copy_to_clip, Toast.LENGTH_SHORT).show()
    }

    fun openLauncher() {
        val intent = Intent(mContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        mContext.startActivity(intent)
    }

    fun togglePointerLocation() {
        RootTool.togglePointerLocation()
    }

    fun close() {
        try {
            mWindow?.close()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } finally {
            STATE_CHANGE_PUBLISHER.onNext(StateChangeEvent(STATE_CLOSED, mState))
            mState = STATE_CLOSED
        }
        mRecorder.removeOnStateChangedListener(this)
        AutoJs.getInstance().layoutInspector.removeCaptureAvailableListener(this)
    }

    override fun onStart() {
        setState(STATE_RECORDING)
    }

    override fun onStop() {
        setState(STATE_NORMAL)
    }

    override fun onPause() {}
    override fun onResume() {}

    companion object {
        private const val TAG = "CircularMenu"
        val STATE_CHANGE_PUBLISHER = PublishSubject.create<StateChangeEvent>()
        const val STATE_CLOSED = -1
        const val STATE_NORMAL = 0
        const val STATE_RECORDING = 1
        private val IC_ACTION_VIEW = R.drawable.ic_android_eat_js
    }
}