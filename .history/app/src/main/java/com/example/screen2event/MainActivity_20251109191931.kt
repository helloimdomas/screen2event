package com.example.screen2event

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.screen2event.ui.theme.Screen2eventTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleSharedImage(intent)
        setContent {
            Screen2eventTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BakingScreen()
                }
            }
        }
    }

    private fun handleSharedImage(receivedIntent: Intent?) {
        if (receivedIntent?.action != Intent.ACTION_SEND || receivedIntent.type?.startsWith("image/") != true) {
            return
        }

        val imageUri: Uri? = receivedIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (imageUri != null) {
            Log.d("MainActivity", "Shared image URI: $imageUri")
        }
    }
}