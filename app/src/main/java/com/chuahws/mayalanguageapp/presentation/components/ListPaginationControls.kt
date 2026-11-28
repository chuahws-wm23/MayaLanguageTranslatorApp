package com.chuahws.mayalanguageapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.service.ListPaging

/**
 * Single-row pagination above each list. The current number in
 * "Page [2] of 4" is directly editable; no extra Go bar is needed.
 * Press keyboard Done or tap outside to apply the entered page.
 */
@Composable
fun ListPaginationControls(
    totalItems: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (totalItems <= ListPaging.PAGE_SIZE) return

    val page = ListPaging.safePage(currentPage, totalItems)
    val pageCount = ListPaging.pageCount(totalItems)
    var pageInput by rememberSaveable(page, pageCount) {
        mutableStateOf(page.toString())
    }
    var wasFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun submitPage() {
        val target = pageInput.toIntOrNull()
        if (target != null && target in 1..pageCount) {
            onPageSelected(target)
        } else {
            // Reject invalid numbers and restore the actual selected page.
            pageInput = page.toString()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = { onPageSelected(page - 1) },
            enabled = page > 1,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "Previous page",
            )
        }

        Text(
            text = "Page",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.width(8.dp))

        BasicTextField(
            value = pageInput,
            onValueChange = { typed ->
                if (typed.length <= 5 && typed.all(Char::isDigit)) {
                    pageInput = typed
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    submitPage()
                    focusManager.clearFocus()
                }
            ),
            modifier = Modifier
                .width(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .onFocusChanged { focus ->
                    if (wasFocused && !focus.isFocused) {
                        submitPage()
                    }
                    wasFocused = focus.isFocused
                }
                .semantics {
                    contentDescription = "Page number, from 1 to $pageCount"
                }
                .padding(vertical = 8.dp, horizontal = 5.dp),
        )

        Spacer(Modifier.width(8.dp))
        Text(
            text = "of $pageCount",
            style = MaterialTheme.typography.labelLarge,
        )

        IconButton(
            onClick = { onPageSelected(page + 1) },
            enabled = page < pageCount,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Next page",
            )
        }
    }
}
