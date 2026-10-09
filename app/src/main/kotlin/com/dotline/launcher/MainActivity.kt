package com.dotline.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dotline.launcher.ui.components.DotText
import com.dotline.launcher.ui.theme.DotlineTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DotlineTheme(darkTheme = true) {
                Foundation()
            }
        }
    }
}

@Composable
private fun Foundation() {
    Box(
        Modifier.fillMaxSize().background(DotlineTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            DotText("12:34", dot = 7.dp, gap = 2.5.dp)
            Spacer(Modifier.height(24.dp))
            DotText("FRIDAY 9 OCT", dot = 3.dp, gap = 1.5.dp, color = DotlineTheme.colors.secondary)
            Spacer(Modifier.height(24.dp))
            BasicText("Dotline", style = DotlineTheme.type.title.copy(color = DotlineTheme.colors.primary))
        }
    }
}
