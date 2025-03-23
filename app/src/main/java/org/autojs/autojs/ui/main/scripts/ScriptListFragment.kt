package org.autojs.autojs.ui.main.scripts

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.aiselp.autox.ui.material3.components.BaseDialog
import com.aiselp.autox.ui.material3.components.CheckboxOption
import com.aiselp.autox.ui.material3.components.DialogController
import com.aiselp.autox.ui.material3.components.DialogTitle
import com.google.android.material.snackbar.Snackbar
import com.stardust.app.GlobalAppContext.get
import com.stardust.pio.PFiles
import com.stardust.util.IntentUtil
import org.autojs.autojs.Pref
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.Scripts.edit
import org.autojs.autojs.ui.build.ProjectConfigActivity
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.explorer.ExplorerViewKt
import org.autojs.autojs.ui.viewmodel.ExplorerItemList.SortConfig
import org.autojs.autojs.ui.widget.fillMaxSize
import org.autojs.autoxjs.R
import java.io.File

/**
 * Created by wilinz on 2022/7/15.
 */
class ScriptListFragment : Fragment() {

    val explorerView by lazy { ExplorerViewKt(this.requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        explorerView.setUpViews()
        return ComposeView(requireContext()).apply {
            setContent {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        FloatingButton()
                    },
                ) {
                    AndroidView(
                        modifier = Modifier.padding(it),
                        factory = { explorerView }
                    )
                }
            }
        }
    }

    @Composable
    private fun FloatingButton() {
        Column(
            modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End
        ) {
            var expand by remember { mutableStateOf(false) }
            val rotate by animateFloatAsState(
                if (!expand) 0f else 360f,
                label = "FloatingActionButton"
            )
            AnimatedVisibility(expand) {
                Actions { expand = false }
                Spacer(modifier = Modifier.height(16.dp))
            }

            FloatingActionButton(
                onClick = { expand = !expand },
                modifier = Modifier.rotate(rotate)
            ) {
                if (expand) {
                    Icon(Icons.Default.Close, null)
                } else Icon(
                    Icons.Default.Add,
                    null,
                )
            }
        }
    }

    @Composable
    fun Actions(closeRequest: () -> Unit) {
        val context = LocalContext.current
        val spacerModifier = Modifier.height(12.dp)
        Column(horizontalAlignment = Alignment.End) {
            NewDirectory(closeRequest)
            Spacer(modifier = spacerModifier)
            NewFile(closeRequest)
            Spacer(modifier = spacerModifier)
            ImportFile(context, closeRequest)
            Spacer(modifier = spacerModifier)
            NewProject(context, closeRequest)
            Spacer(modifier = spacerModifier)
        }
    }

    @Composable
    private fun NewProject(context: Context, closeRequest: () -> Unit) {
        ExtendedFloatingActionButton(text = { Text(text = stringResource(id = R.string.text_project)) },
            icon = {
                Icon(
                    painterResource(id = R.drawable.ic_project2),
                    contentDescription = null,
                    tint = Color(0xFF09ECBF)
                )
            },
            onClick = {
                closeRequest()
                val explorerView = this@ScriptListFragment.explorerView
                ProjectConfigActivity.newProject(context, explorerView.currentPage!!.toScriptFile())
            })
    }


    @Composable
    private fun ImportFile(context: Context, closeRequest: () -> Unit) {
        ExtendedFloatingActionButton(text = { Text(text = stringResource(id = R.string.text_import)) },
            icon = {
                Icon(
                    painterResource(id = R.drawable.ic_floating_action_menu_open),
                    contentDescription = null,
                    tint = Color(0xFF831DDD)
                )
            },
            onClick = {
                closeRequest()
                getScriptOperations(
                    context, this@ScriptListFragment
                ).importFile()
            })
    }

    @Composable
    private fun NewFile(closeRequest: () -> Unit) {
        val dialog = remember { DialogController() }
        var name by remember { mutableStateOf("") }
        var jsFile by remember { mutableStateOf(false) }
        var mjsFile by remember { mutableStateOf(false) }

        dialog.BaseDialog(onDismissRequest = { dialog.dismiss() }, title = {
            DialogTitle(title = stringResource(R.string.text_name))
        }, positiveText = stringResource(R.string.ok), onPositiveClick = {
            closeRequest();dialog.dismiss()
            val dir = explorerView.currentPage?.toScriptFile()
            if (name.isEmpty()) {
                showSnackbar(explorerView, R.string.text_file_name_cannot_be_empty)
                return@BaseDialog
            }
            if (dir != null) {
                var fileName = name
                if (jsFile) fileName += ".js"
                if (mjsFile) fileName += ".mjs"
                val file = File(dir, fileName)
                PFiles.createIfNotExists(file.path)
                Explorers.workspace()
                    .notifyItemCreated(ExplorerFileItem(file, explorerView.currentPage))
                showSnackbar(explorerView, R.string.text_already_create)
            } else {
                showSnackbar(explorerView, R.string.text_create_fail)
            }
        }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.text_please_input_name)) },
                    suffix = {
                        if (jsFile || mjsFile) {
                            Text(text = ".${if (jsFile) "js" else "mjs"}")
                        }
                    }
                )
                Row {
                    CheckboxOption(
                        modifier = Modifier,
                        checked = jsFile,
                        onCheckedChange = {
                            jsFile = it
                            if (it && mjsFile) mjsFile = false
                        },
                        name = stringResource(R.string.text_js_file)
                    )
                    CheckboxOption(
                        modifier = Modifier,
                        checked = mjsFile,
                        onCheckedChange = {
                            mjsFile = it
                            if (it && jsFile) jsFile = false
                        },
                        name = stringResource(R.string.text_mjs_file)
                    )
                }
            }
        }
        ExtendedFloatingActionButton(text = { Text(text = stringResource(id = R.string.text_file)) },
            icon = {
                Icon(
                    painterResource(id = R.drawable.ic_floating_action_menu_file),
                    contentDescription = null,
                    tint = Color(0xFF2196F3)
                )
            },
            onClick = { dialog.show() })
    }

    @Composable
    private fun NewDirectory(closeRequest: () -> Unit) {
        val dialog = remember { DialogController() }
        var name by remember { mutableStateOf("") }

        dialog.BaseDialog(onDismissRequest = { dialog.dismiss() }, title = {
            DialogTitle(title = stringResource(R.string.text_name))
        }, positiveText = stringResource(R.string.ok), onPositiveClick = {
            closeRequest();dialog.dismiss()
            val dir = explorerView.currentPage?.toScriptFile()
            if (name.isEmpty()) {
                showSnackbar(explorerView, R.string.text_file_name_cannot_be_empty)
                return@BaseDialog
            }
            if (dir != null && name.isNotEmpty()) {
                val newDir = File(dir, name)
                if (newDir.exists()){
                    showSnackbar(explorerView, R.string.text_file_exists)
                    return@BaseDialog
                }
                newDir.mkdirs()
                Explorers.workspace()
                    .notifyItemCreated(ExplorerDirPage(newDir, explorerView.currentPage))
                showSnackbar(explorerView, R.string.text_already_create)
            } else {
                showSnackbar(explorerView, R.string.text_create_fail)
            }
        }) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.text_please_input_name)) }
            )
        }
        ExtendedFloatingActionButton(text = { Text(text = stringResource(id = R.string.text_directory)) },
            icon = {
                Icon(
                    painterResource(id = R.drawable.ic_floating_action_menu_dir),
                    tint = Color(0xFFFFC107),
                    contentDescription = null
                )
            },
            onClick = { dialog.show() })
    }

    fun ExplorerViewKt.setUpViews() {
        fillMaxSize()
        sortConfig = SortConfig.from(
            PreferenceManager.getDefaultSharedPreferences(
                requireContext()
            )
        )
        setExplorer(
            Explorers.workspace(),
            ExplorerDirPage.createRoot(Pref.getScriptDirPath())
        )
        setOnItemClickListener { _, item ->
            item?.let {
                if (item.isEditable) {
                    edit(requireContext(), item.toScriptFile());
                } else {
                    IntentUtil.viewFile(get(), item.path, AppFileProvider.AUTHORITY)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        explorerView.sortConfig?.saveInto(
            PreferenceManager.getDefaultSharedPreferences(
                requireContext()
            )
        )
    }

    private fun getScriptOperations(
        context: Context,
        scriptListFragment: ScriptListFragment
    ): ScriptOperations {
        val explorerView = scriptListFragment.explorerView
        return ScriptOperations(
            context,
            explorerView,
            explorerView.currentPage
        )
    }

    fun onBackPressed(): Boolean {
        if (explorerView.canGoBack()) {
            explorerView.goBack()
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "MyScriptListFragment"
        private fun showSnackbar(view: View, res: Int) {
            Snackbar.make(
                view, res, Snackbar.LENGTH_SHORT
            ).show()
        }
    }


}