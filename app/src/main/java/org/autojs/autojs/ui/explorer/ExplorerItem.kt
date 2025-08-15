package org.autojs.autojs.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.autojs.autoxjs.R


@Composable

fun ExplorerItem(
    onItemClick: () -> Unit,
    name: String,
    desc: String,
    firstChar: String,
    firstCharBackground: Color,
    runVisibility: Boolean,
    editVisibility: Boolean,
    run: () -> Unit,
    edit: () -> Unit,
    more: () -> Unit,
    optionMenuContent: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .clickable { onItemClick() },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(firstCharBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstChar,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFFFFFFF),
                    fontSize = 24.sp
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    fontSize = 14.sp
                )
                Text(
                    text = desc,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    fontSize = 11.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = Color(0xFFA9AAAB)
                val modifier = Modifier
                    .height(40.dp)
                    .width(35.dp)
                    .clip(RoundedCornerShape(32.dp))
                val iconModifier = Modifier.size(18.dp)
                if (runVisibility) Box(
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
                if (editVisibility) Box(
                    modifier.clickable { edit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = iconModifier,
                        painter = painterResource(R.drawable.ic_mode_edit_black_24dp),
                        contentDescription = null,
                        tint = tint
                    )
                }
                Box(
                    modifier.clickable { more() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        modifier = iconModifier,
                        painter = painterResource(R.drawable.ic_more_vert_black_24dp),
                        contentDescription = null,
                        tint = tint
                    )
                }
                optionMenuContent()
            }
        }
    }

}