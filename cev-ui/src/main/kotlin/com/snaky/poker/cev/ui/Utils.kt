package com.snaky.poker.cev.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.snaky.poker.cev.ui.theme.DefaultTheme
import java.io.File
import javax.swing.JFileChooser

fun amountAnnotatedString(value: Double): AnnotatedString {
    return "%.2f€".format(value).toAmountAnnotatedString(value)
}

fun String.toAmountAnnotatedString(value: Double): AnnotatedString {
    return buildAnnotatedString {
        val color = when {
            value < 0 -> DefaultTheme.Colors.TextDanger
            value > 0 -> DefaultTheme.Colors.Success
            else -> Color.Unspecified
        }
        withStyle(style = SpanStyle(color = color)) {
            append(this@toAmountAnnotatedString)
        }
    }
}

fun pickDirectory(lastPath: File?, onPathSelected: (String) -> Unit) {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select a Hand History folder"
        currentDirectory = lastPath ?: File(System.getProperty("user.home"))
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        onPathSelected(chooser.selectedFile.absolutePath)
    }
}

fun formatBuyIn(buyInCents: Int): String {
    return if (buyInCents % 100 == 0) "${buyInCents / 100} €" else "%.2f €".format(buyInCents / 100.0)
}

fun formatCents(cents: Int): String = "%.2f €".format(cents / 100.0)

fun formatStartingStack(stack: Int) = when (stack) {
    200 -> "Flash"
    300 -> "Nitro"
    500 -> "Regular"
    else -> "$stack chips"
}