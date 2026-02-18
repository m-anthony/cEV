package com.snaky.poker.cev.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainView() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Interface chargée avec succès !", style = MaterialTheme.typography.h4)
                Text("Version détectée : ${AppConfig.version}")
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { println("Click !") }) {
                    Text("Tester un bouton")
                }
            }
        }
    }
}