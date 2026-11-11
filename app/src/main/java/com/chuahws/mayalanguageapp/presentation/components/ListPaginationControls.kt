package com.chuahws.mayalanguageapp.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.chuahws.mayalanguageapp.domain.service.ListPaging

/**
 * A shared accessible pager: 10 results per page, previous/next arrows
 * and direct numeric navigation with either the Go key or the Go button.
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
    val pages = ListPaging.pageCount(totalItems)
    var pageNumber by rememberSaveable(page, pages) {
        mutableStateOf(page.toString())
    }
    val focusManager = LocalFocusManager.current

    fun goToEnteredPage() {
        val target = pageNumber.toIntOrNull()
        if (target != null && target in 1..pages) {
            focusManager.clearFocus()
            onPageSelected(target)
        }
    }

    val enteredPage = pageNumber.toIntOrNull()
    val validInput = enteredPage != null && enteredPage in 1..pages

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Showing ${ListPaging.firstVisibleIndex(page, totalItems)}–" +
                    "${ListPaging.lastVisibleIndex(page, totalItems)} of $totalItems",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Keep navigation spacious on smaller phone screens.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
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
                    text = "Page $page of $pages",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                )

                IconButton(
                    onClick = { onPageSelected(page + 1) },
                    enabled = page < pages,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next page",
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = pageNumber,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all(Char::isDigit)) {
                            pageNumber = input
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("Jump to page") },
                    singleLine = true,
                    maxLines = 1,
                    isError = pageNumber.isNotEmpty() && !validInput,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { goToEnteredPage() },
                    ),
                )

                TextButton(
                    onClick = ::goToEnteredPage,
                    enabled = validInput && enteredPage != page,
                ) {
                    Text("Go")
                }
            }
        }
    }
}
