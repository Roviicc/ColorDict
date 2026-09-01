package io.github.roviicc.colordict.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import io.github.roviicc.colordict.R
import io.github.roviicc.colordict.design.DictionaryAmber
import io.github.roviicc.colordict.design.DictionaryCoral
import io.github.roviicc.colordict.design.DictionaryTeal
import io.github.roviicc.colordict.design.PopupAzure500
import io.github.roviicc.colordict.design.PopupBrandTitle
import io.github.roviicc.colordict.design.PopupDivider
import io.github.roviicc.colordict.design.PopupNavy950
import io.github.roviicc.colordict.design.PopupPaper
import io.github.roviicc.colordict.design.PopupSectionLabel
import io.github.roviicc.colordict.design.PopupSlate500
import io.github.roviicc.colordict.design.PopupSpacing

@Composable
fun PopupMainChrome(
    query: String,
    showRecentLabel: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PopupPaper)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PopupNavy950)
                .statusBarsPadding()
                .padding(
                    start = PopupSpacing.lg,
                    top = PopupSpacing.lg,
                    end = PopupSpacing.lg,
                    bottom = PopupSpacing.md,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_book),
                    contentDescription = null,
                    tint = PopupAzure500,
                    modifier = Modifier.size(42.dp),
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = PopupPaper)) { append("Pop Up ") }
                        withStyle(SpanStyle(color = PopupAzure500)) { append("Dictionary") }
                    },
                    style = PopupBrandTitle,
                    maxLines = 1,
                    modifier = Modifier.padding(start = PopupSpacing.sm).weight(1f),
                )
                PopupIconButton(
                    contentDescription = "Menu",
                    onClick = onMenuClick,
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
            }

            Spacer(Modifier.height(PopupSpacing.lg))

            PopupSearchField(
                value = query,
                onValueChange = onQueryChange,
                onSubmit = onSubmit,
                focusRequester = focusRequester,
            )

            DictionaryColorLegend(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = PopupSpacing.md),
            )
        }

        if (showRecentLabel) {
            PopupSectionHeader("Recent searches")
        }
    }
}

@Composable
fun PopupSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(32.dp)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.take(128)) },
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(PopupPaper)
            .border(1.dp, PopupAzure500, shape)
            .focusRequester(focusRequester),
        singleLine = true,
        textStyle = io.github.roviicc.colordict.design.PopupBody.copy(color = PopupNavy950),
        cursorBrush = SolidColor(PopupAzure500),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = {
            onSubmit()
            keyboard?.hide()
        }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PopupSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = PopupAzure500,
                    modifier = Modifier.size(26.dp),
                )
                Box(modifier = Modifier.padding(horizontal = PopupSpacing.md).weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "Search all dictionaries",
                            style = io.github.roviicc.colordict.design.PopupBody,
                            color = PopupSlate500,
                        )
                    }
                    innerTextField()
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = {
                            onSubmit()
                            keyboard?.hide()
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Search",
                        tint = PopupAzure500,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
    )
}

@Composable
fun PopupIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, PopupAzure500, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides PopupAzure500,
            content = content,
        )
    }
}

@Composable
fun DictionaryColorLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PopupSpacing.sm)) {
        listOf(DictionaryCoral, DictionaryTeal, DictionaryAmber).forEach { color ->
            Box(
                Modifier
                    .size(width = 24.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun PopupSectionHeader(label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(PopupPaper)) {
        Text(
            text = label.uppercase(),
            style = PopupSectionLabel,
            color = PopupSlate500,
            modifier = Modifier.padding(
                start = PopupSpacing.lg,
                top = PopupSpacing.lg,
                end = PopupSpacing.lg,
                bottom = PopupSpacing.sm,
            ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PopupSpacing.lg)
                .height(1.dp)
                .background(PopupDivider),
        )
    }
}
