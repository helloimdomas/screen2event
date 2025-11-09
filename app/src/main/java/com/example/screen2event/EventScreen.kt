package com.example.screen2event

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.asImageBitmap
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun EventScreen(
    EventViewModel: EventViewModel = viewModel(),
    sharedImageUri: Uri? = null
) {
    val selectedImage = remember { mutableIntStateOf(-1) }
    val placeholderResult = stringResource(R.string.results_placeholder)
    var result by rememberSaveable { mutableStateOf(placeholderResult) }
    val uiState by EventViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyyMMdd") }

    val imageItems = remember(sharedImageUri) {
        sharedImageUri?.let { listOf(it) } ?: emptyList()
    }

    LaunchedEffect(imageItems) {
        selectedImage.intValue = if (imageItems.isEmpty()) -1 else 0
    }

    if (sharedImageUri != null) {
        Log.d("EventScreen", "Received shared URI: $sharedImageUri")
    }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.fillMaxHeight(0.1f))
            if (imageItems.isEmpty()) {
                Text(
                text = stringResource(R.string.share_image_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(imageItems) { index, uri ->
                    var imageModifier = Modifier
                        .padding(start = 8.dp, end = 8.dp)
                        .requiredSize(200.dp)
                        .clickable {
                            selectedImage.intValue = index
                        }
                    if (index == selectedImage.intValue) {
                        imageModifier =
                            imageModifier.border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary))
                    }
                    val bitmap = remember(uri) {
                        loadBitmapFromUri(context, uri)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = uri.toString(),
                            modifier = imageModifier
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(all = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val selectedItem = selectedImage.intValue.takeIf { it >= 0 }?.let(imageItems::get)
                    val bitmap = selectedItem?.let { loadBitmapFromUri(context, it) }
                    if (bitmap != null) {
                        val text = ""
                        val currentDateStr = LocalDate.now().format(dateFormatter)
                        val prompt = "Extract event details from this text: \"$text\". Today's date is $currentDateStr. " +
                            "Return JSON with these fields only: {\"name\": \"event name\", \"date\": \"YYYYMMDD\", " +
                            "\"startTime\": \"HHMMSS\", \"endTime\": \"HHMMSS\", \"location\": \"location\", " +
                            "\"url\": \"url\", \"description\": \"description\", \"timezone\": \"IANA format or UTC offset or N/A\"}. " +
                            "If year isn't specified, assume the nearest future date. If endTime isn't specified, " +
                            "estimate based on event type (meetings: 1h, viewings: 30m, concerts: 2-3h). " +
                            "Never return 'N/A' for date, startTime, or endTime fields."
                        EventViewModel.sendPrompt(bitmap, prompt, context)
                    }
                },
                enabled = selectedImage.intValue >= 0,
            ) {
                Text(text = stringResource(R.string.action_go))
            }
        }

        if (uiState is UiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            var textColor = MaterialTheme.colorScheme.onSurface
            if (uiState is UiState.Error) {
                textColor = MaterialTheme.colorScheme.error
                result = (uiState as UiState.Error).errorMessage
            } else if (uiState is UiState.Success) {
                textColor = MaterialTheme.colorScheme.onSurface
                result = (uiState as UiState.Success).outputText
            }
            val scrollState = rememberScrollState()
            Text(
                text = result,
                textAlign = TextAlign.Start,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            )
        }
    }
}

private fun loadBitmapFromUri(context: android.content.Context, uri: Uri): Bitmap? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    }.getOrNull()

@Preview(showSystemUi = true)
@Composable
fun EventScreenPreview() {
    EventScreen()
}