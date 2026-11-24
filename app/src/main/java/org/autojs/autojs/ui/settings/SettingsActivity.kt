package org.autojs.autojs.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.aiselp.autox.ui.material3.components.BackTopAppBar
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.DialogController
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.aiselp.autox.ui.material3.theme.AppTheme
import com.aiselp.autox.utils.MultiProcessAppConfig
import com.aiselp.autox.utils.multiProcessAppConfig
import com.stardust.autojs.runtime.accessibility.AccessibilityConfig
import com.stardust.pio.PFiles
import de.psdev.licensesdialog.LicensesDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.launch
import org.autojs.autojs.Pref
import org.autojs.autojs.external.open.RunIntentActivity
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.storage.file.FileObservable
import org.autojs.autojs.theme.dialog.ThemeColorMaterialDialogBuilder
import org.autojs.autojs.tool.SimpleObserver
import org.autojs.autojs.ui.settings.LicenseInfo.install
import org.autojs.autojs.ui.widget.CommonMarkdownView
import org.autojs.autoxjs.R
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        init {
            install()
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pref = remember { Pref.def() }
    val scope = rememberCoroutineScope()

    // Keys
    val keyUseVolumeControlRecord = stringResource(R.string.key_use_volume_control_record)
    val keyRecordToast = stringResource(R.string.key_record_toast)
    val keyRootRecordOutFileType = stringResource(R.string.key_root_record_out_file_type)
    val keyIntentRunScript = stringResource(R.string.key_intent_run_script)
    val keyUseVolumeControlRunning = stringResource(R.string.key_use_volume_control_running)
    val keyGuardMode = stringResource(R.string.key_guard_mode)
    val keyMaxLengthCodeCompletion = stringResource(R.string.key_max_length_for_code_completion)
    val keyEnableAccessibilityByRoot = stringResource(R.string.key_enable_accessibility_service_by_root)
    val keyStableMode = stringResource(R.string.key_stable_mode)
    val keyScriptDirPath = stringResource(R.string.key_script_dir_path)
    val defaultScriptDir = stringResource(R.string.default_value_script_dir_path)

    // States
    var useVolumeControlRecord by remember { mutableStateOf(pref.getBoolean(keyUseVolumeControlRecord, false)) }
    var recordToast by remember { mutableStateOf(pref.getBoolean(keyRecordToast, true)) }
    var rootRecordOutFileType by remember { mutableStateOf(pref.getString(keyRootRecordOutFileType, "binary") ?: "binary") }
    var intentRunScript by remember { mutableStateOf(pref.getBoolean(keyIntentRunScript, false)) }
    var useVolumeControlRunning by remember { mutableStateOf(pref.getBoolean(keyUseVolumeControlRunning, false)) }
    var guardMode by remember { mutableStateOf(pref.getBoolean(keyGuardMode, false)) }
    var maxLengthCodeCompletion by remember { mutableStateOf(pref.getString(keyMaxLengthCodeCompletion, "2000") ?: "2000") }
    var enableAccessibilityByRoot by remember { mutableStateOf(pref.getBoolean(keyEnableAccessibilityByRoot, false)) }
    var stableMode by remember { mutableStateOf(pref.getBoolean(keyStableMode, false)) }
    var scriptDirPath by remember { mutableStateOf(pref.getString(keyScriptDirPath, defaultScriptDir) ?: defaultScriptDir) }

    // Dialog Controllers
    val fileTypeDialogController = remember { DialogController() }
    val maxLengthDialogController = remember { DialogController() }
    val scriptDirDialogController = remember { DialogController() }

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = stringResource(R.string.text_setting),
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Category: 脚本记录
            PreferenceCategoryHeader(title = stringResource(R.string.text_script_record))

            SwitchPreferenceItem(
                title = stringResource(R.string.text_use_volume_control_record),
                summary = stringResource(R.string.summary_use_volume_control_record),
                checked = useVolumeControlRecord,
                onCheckedChange = { checked ->
                    useVolumeControlRecord = checked
                    pref.edit { putBoolean(keyUseVolumeControlRecord, checked) }
                }
            )

            SwitchPreferenceItem(
                title = stringResource(R.string.text_record_msg),
                checked = recordToast,
                onCheckedChange = { checked ->
                    recordToast = checked
                    pref.edit { putBoolean(keyRecordToast, checked) }
                }
            )

            val rootRecordKeys = stringArrayResource(R.array.root_record_out_file_type_keys)
            val rootRecordValues = stringArrayResource(R.array.root_record_out_file_type_values)
            val fileTypeSummaryIndex = rootRecordValues.indexOf(rootRecordOutFileType).coerceAtLeast(0)
            val currentFileTypeSummary = if (fileTypeSummaryIndex in rootRecordKeys.indices) rootRecordKeys[fileTypeSummaryIndex] else rootRecordOutFileType

            PreferenceItem(
                title = stringResource(R.string.text_root_record_out_file_type),
                summary = currentFileTypeSummary,
                onClick = { fileTypeDialogController.show() }
            )

            // Category: 脚本运行
            PreferenceCategoryHeader(title = stringResource(R.string.text_script_running))

            SwitchPreferenceItem(
                title = stringResource(R.string.text_intent_run_script),
                checked = intentRunScript,
                onCheckedChange = { checked ->
                    intentRunScript = checked
                    pref.edit { putBoolean(keyIntentRunScript, checked) }
                    val state = if (checked) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    }
                    context.packageManager.setComponentEnabledSetting(
                        ComponentName(context, RunIntentActivity::class.java),
                        state,
                        PackageManager.DONT_KILL_APP
                    )
                }
            )

            SwitchPreferenceItem(
                title = stringResource(R.string.text_use_volume_to_stop_running),
                checked = useVolumeControlRunning,
                onCheckedChange = { checked ->
                    useVolumeControlRunning = checked
                    scope.launch {
                        context.multiProcessAppConfig.updateData {
                            MultiProcessAppConfig(isRunningVolumeControlEnabled = checked)
                        }
                    }
                }
            )

            SwitchPreferenceItem(
                title = stringResource(R.string.text_guard_mode),
                summary = stringResource(R.string.summary_guard_mode),
                checked = guardMode,
                onCheckedChange = { checked ->
                    guardMode = checked
                    pref.edit { putBoolean(keyGuardMode, checked) }
                    AccessibilityConfig.setIsUnintendedGuardEnabled(checked)
                }
            )

            // Category: 编辑
            PreferenceCategoryHeader(title = stringResource(R.string.text_edit))

            PreferenceItem(
                title = stringResource(R.string.text_max_length_for_code_completion),
                summary = maxLengthCodeCompletion,
                onClick = { maxLengthDialogController.show() }
            )

            // Category: 无障碍服务
            PreferenceCategoryHeader(title = stringResource(R.string.text_accessibility_service))

            SwitchPreferenceItem(
                title = stringResource(R.string.text_enable_accessibility_service_by_root),
                summary = stringResource(R.string.summary_enable_accessibility_service_by_root),
                checked = enableAccessibilityByRoot,
                onCheckedChange = { checked ->
                    enableAccessibilityByRoot = checked
                    pref.edit { putBoolean(keyEnableAccessibilityByRoot, checked) }
                }
            )

            SwitchPreferenceItem(
                title = stringResource(R.string.text_stable_mode),
                summary = stringResource(R.string.summary_stable_mode),
                checked = stableMode,
                onCheckedChange = { checked ->
                    stableMode = checked
                    pref.edit { putBoolean(keyStableMode, checked) }
                }
            )

            // Category: 其他
            PreferenceCategoryHeader(title = stringResource(R.string.text_others))

            PreferenceItem(
                title = stringResource(R.string.text_change_script_dir),
                summary = scriptDirPath,
                onClick = { scriptDirDialogController.show() }
            )

            // Category: 关于
            PreferenceCategoryHeader(title = stringResource(R.string.text_about))

            PreferenceItem(
                title = stringResource(R.string.text_about_me_and_repo),
                onClick = {
                    context.startActivity(
                        Intent(context, AboutActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            PreferenceItem(
                title = stringResource(R.string.text_licenses),
                onClick = { showLicenseDialog(context) }
            )

            PreferenceItem(
                title = stringResource(R.string.text_licenses_other),
                onClick = { showLicenseDialog2(context) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Dialogs
    val keys = stringArrayResource(R.array.root_record_out_file_type_keys)
    val values = stringArrayResource(R.array.root_record_out_file_type_values)
    fileTypeDialogController.SingleChoiceDialog(
        title = stringResource(R.string.text_root_record_out_file_type),
        options = keys.toList(),
        selectedOptionIndex = values.indexOf(rootRecordOutFileType).coerceAtLeast(0),
        onOptionSelected = { index ->
            val newValue = values[index]
            rootRecordOutFileType = newValue
            pref.edit { putString(keyRootRecordOutFileType, newValue) }
        }
    )

    maxLengthDialogController.EditTextDialog(
        title = stringResource(R.string.text_max_length_for_code_completion),
        initialValue = maxLengthCodeCompletion,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onConfirm = { newValue ->
            maxLengthCodeCompletion = newValue
            pref.edit { putString(keyMaxLengthCodeCompletion, newValue) }
        }
    )

    scriptDirDialogController.ScriptDirDialog(
        initialPath = scriptDirPath,
        onConfirm = { newPathValue, selectedAction ->
            val oldPath = Pref.getScriptDirPath()
            pref.edit { putString(keyScriptDirPath, newPathValue) }
            scriptDirPath = newPathValue

            val newPath = Pref.getScriptDirPath()
            if (oldPath != newPath) {
                when (selectedAction) {
                    ScriptDirAction.NONE -> Explorers.workspace().refreshAll()
                    ScriptDirAction.COPY -> showFileProgressDialog(context, FileObservable.copy(oldPath, newPath))
                    ScriptDirAction.MOVE -> showFileProgressDialog(context, FileObservable.move(oldPath, newPath))
                }
            }
        }
    )
}

private fun showLicenseDialog(context: Context) {
    LicensesDialog.Builder(context)
        .setNotices(R.raw.licenses)
        .setIncludeOwnLicense(true)
        .build()
        .show()
}

private fun showLicenseDialog2(context: Context) {
    CommonMarkdownView.DialogBuilder(context)
        .padding(36, 0, 36, 0)
        .markdown(PFiles.read(context.resources.openRawResource(R.raw.licenses_other)))
        .title(R.string.text_licenses_other)
        .positiveText(R.string.ok)
        .canceledOnTouchOutside(false)
        .show()
}

private fun showFileProgressDialog(context: Context, observable: Observable<File>) {
    val dialog = ThemeColorMaterialDialogBuilder(context)
        .progress(true, 0)
        .progressIndeterminateStyle(true)
        .title(R.string.text_on_progress)
        .cancelable(false)
        .content("")
        .show()
    observable.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(object : SimpleObserver<File>() {
            override fun onNext(file: File) {
                dialog.setContent(file.path)
            }

            override fun onError(e: Throwable) {
                e.printStackTrace()
                dialog.dismiss()
                Explorers.workspace().refreshAll()
                Toast.makeText(
                    context,
                    context.getString(R.string.text_error_copy_file, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onComplete() {
                dialog.dismiss()
                Explorers.workspace().refreshAll()
            }
        })
}

@Composable
private fun PreferenceCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun PreferenceItem(
    title: String,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!summary.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(4.dp))
            trailingContent()
        }
    }
}

@Composable
private fun SwitchPreferenceItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    PreferenceItem(
        title = title,
        summary = summary,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun DialogController.SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedOptionIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    var selectedIndex by remember(selectedOptionIndex) { mutableIntStateOf(selectedOptionIndex) }

    BaseDialog(
        onDismissRequest = { dismiss() },
        title = { DialogTitle(title = title) },
        positiveText = stringResource(R.string.ok),
        onPositiveClick = {
            onOptionSelected(selectedIndex)
            dismiss()
        },
        negativeText = stringResource(R.string.cancel),
        onNegativeClick = { dismiss() },
        content = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEachIndexed { index, text ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (index == selectedIndex),
                                onClick = { selectedIndex = index },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (index == selectedIndex),
                            onClick = null
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun DialogController.EditTextDialog(
    title: String,
    initialValue: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onConfirm: (String) -> Unit
) {
    var textValue by remember(initialValue) { mutableStateOf(initialValue) }

    BaseDialog(
        onDismissRequest = { dismiss() },
        title = { DialogTitle(title = title) },
        positiveText = stringResource(R.string.ok),
        onPositiveClick = {
            onConfirm(textValue)
            dismiss()
        },
        negativeText = stringResource(R.string.cancel),
        onNegativeClick = { dismiss() },
        content = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                singleLine = true,
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

private enum class ScriptDirAction {
    NONE, COPY, MOVE
}

@Composable
private fun DialogController.ScriptDirDialog(
    initialPath: String,
    onConfirm: (newPath: String, action: ScriptDirAction) -> Unit
) {
    var pathText by remember(initialPath) { mutableStateOf(initialPath) }
    var selectedAction by remember { mutableStateOf(ScriptDirAction.NONE) }

    BaseDialog(
        onDismissRequest = { dismiss() },
        title = { DialogTitle(title = stringResource(R.string.text_change_script_dir)) },
        positiveText = stringResource(R.string.ok),
        onPositiveClick = {
            onConfirm(pathText, selectedAction)
            dismiss()
        },
        negativeText = stringResource(R.string.cancel),
        onNegativeClick = { dismiss() },
        content = {
            Column {
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (selectedAction == ScriptDirAction.NONE),
                                onClick = { selectedAction = ScriptDirAction.NONE },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedAction == ScriptDirAction.NONE),
                            onClick = null
                        )
                        Text(
                            text = stringResource(R.string.text_do_nothing),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (selectedAction == ScriptDirAction.COPY),
                                onClick = { selectedAction = ScriptDirAction.COPY },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedAction == ScriptDirAction.COPY),
                            onClick = null
                        )
                        Text(
                            text = stringResource(R.string.text_copy_to_new_folder),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (selectedAction == ScriptDirAction.MOVE),
                                onClick = { selectedAction = ScriptDirAction.MOVE },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedAction == ScriptDirAction.MOVE),
                            onClick = null
                        )
                        Text(
                            text = stringResource(R.string.text_move_new_folder),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    )
}
