package com.example.screen2event

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.CalendarContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class EventViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-flash-latest",
        apiKey = BuildConfig.apiKey
    )

    private val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("HHmmss")

    fun sendPrompt(
        bitmap: Bitmap,
        prompt: String,
        context: Context
    ) {
        _uiState.value = UiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
                response.text?.let { outputContent ->
                    Log.d("EventViewModel", "Raw AI response: $outputContent")
                    val eventDetails = parseEventDetailsFromJson(outputContent)
                    val eventName = eventDetails?.name
                    Log.d("EventViewModel", "Parsed event name: ${eventName ?: "<missing>"}")
                    if (eventDetails != null) {
                        Log.d(
                            "EventViewModel",
                            "Parsed timestamps -> start: ${eventDetails.startMillis ?: "none"}, end: ${eventDetails.endMillis ?: "none"}, zone: ${eventDetails.zoneId.id}, raw timezone: ${eventDetails.rawTimezone ?: "none"}"
                        )
                        maybeLaunchCalendarIntent(context, eventDetails)
                    } else {
                        Log.d("EventViewModel", "Parsed timestamps -> unavailable")
                    }
                    _uiState.value = UiState.Success(outputContent)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "")
            }
        }
    }

    private suspend fun maybeLaunchCalendarIntent(context: Context, eventDetails: EventDetails) {
        val calendarIntent = createCalendarIntent(eventDetails)
        if (calendarIntent != null) {
            Log.d(
                "EventViewModel",
                "Launching calendar intent with start=${eventDetails.startMillis}, end=${eventDetails.endMillis}, title=${eventDetails.name}"
            )
            withContext(Dispatchers.Main) {
                runCatching { context.startActivity(calendarIntent) }
                    .onFailure { Log.e("EventViewModel", "Failed to start calendar intent", it) }
            }
        } else {
            Log.d(
                "EventViewModel",
                "Calendar intent not launched; missing required data (title/start/end)"
            )
        }
    }

    private fun parseEventDetailsFromJson(responseText: String): EventDetails? {
        val jsonPayload = extractJsonPayload(responseText) ?: return null

        return try {
            val json = JSONObject(jsonPayload)
            val rawTimezone = json.optString("timezone").trim().ifBlank { null }
            val zoneId = parseZoneId(rawTimezone) ?: ZoneId.systemDefault()
            val startMillis = parseDateTimeToEpoch(
                json.optString("date").trim(),
                json.optString("startTime").trim(),
                zoneId
            )
            val endMillis = parseDateTimeToEpoch(
                json.optString("date").trim(),
                json.optString("endTime").trim(),
                zoneId
            )

            val name = json.optString("name").trim().takeIf { it.isNotEmpty() }
            val description = json.optString("description").trim()
                .takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }
            val location = json.optString("location").trim()
                .takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }
            val url = json.optString("url").trim()
                .takeIf { it.isNotEmpty() && !it.equals("N/A", ignoreCase = true) }

            EventDetails(
                name = name,
                description = description,
                location = location,
                url = url,
                startMillis = startMillis,
                endMillis = endMillis,
                zoneId = zoneId,
                rawTimezone = rawTimezone
            )
        } catch (_: JSONException) {
            null
        }
    }

    private fun createCalendarIntent(eventDetails: EventDetails): Intent? {
        val title = eventDetails.name ?: return null
        val start = eventDetails.startMillis ?: return null
        val end = eventDetails.endMillis ?: return null

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, eventDetails.zoneId.id)
            eventDetails.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            eventDetails.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        }
    }

    private fun parseDateTimeToEpoch(
        dateValue: String?,
        timeValue: String?,
        zoneId: ZoneId
    ): Long? {
        if (dateValue.isNullOrBlank() || timeValue.isNullOrBlank()) return null
        val normalizedTime = normalizeTimeString(timeValue) ?: return null

        return runCatching {
            val localDate = LocalDate.parse(dateValue.trim(), dateFormatter)
            val localTime = LocalTime.parse(normalizedTime, timeFormatter)
            ZonedDateTime.of(localDate, localTime, zoneId).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun normalizeTimeString(timeValue: String): String? {
        val digitsOnly = timeValue.filter(Char::isDigit)
        if (digitsOnly.isEmpty()) return null

        return when {
            digitsOnly.length >= 6 -> digitsOnly.substring(0, 6)
            digitsOnly.length == 5 -> digitsOnly + "0"
            digitsOnly.length == 4 -> digitsOnly + "00"
            digitsOnly.length in 2..3 -> digitsOnly.padEnd(6, '0')
            else -> null
        }
    }

    private fun parseZoneId(rawTimezone: String?): ZoneId? {
        if (rawTimezone.isNullOrBlank()) return null
        val trimmed = rawTimezone.trim()
        if (trimmed.equals("n/a", ignoreCase = true)) return null

        runCatching { ZoneId.of(trimmed) }.onSuccess { return it }

        val offsetCandidate = when {
            trimmed.startsWith("UTC", ignoreCase = true) -> trimmed.substring(3).trim()
            trimmed.startsWith("GMT", ignoreCase = true) -> trimmed.substring(3).trim()
            else -> trimmed
        }.takeIf { it.startsWith("+") || it.startsWith("-") }

        if (!offsetCandidate.isNullOrBlank()) {
            runCatching { ZoneOffset.of(offsetCandidate) }.onSuccess { return it }
        }

        return null
    }

    private fun extractJsonPayload(responseText: String): String? {
        val startIndex = responseText.indexOf('{')
        val endIndex = responseText.lastIndexOf('}')
        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) return null
        return responseText.substring(startIndex, endIndex + 1)
    }

    private data class EventDetails(
        val name: String?,
        val description: String?,
        val location: String?,
        val url: String?,
        val startMillis: Long?,
        val endMillis: Long?,
        val zoneId: ZoneId,
        val rawTimezone: String?
    )
}