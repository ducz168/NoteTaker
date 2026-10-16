package com.example.notetaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notetaker.ui.NoteEditScreen
import com.example.notetaker.ui.NotesListScreen
import com.example.notetaker.ui.SettingsScreen
import com.example.notetaker.ui.theme.NoteTakerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoteTakerTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "list") {
                    composable("list") {
                        NotesListScreen(
                            onOpenNote = { nav.navigate("note/$it") },
                            onNewNote = { templateId -> nav.navigate("note/0?template=$templateId") },
                            onSettings = { nav.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                    composable(
                        "note/{id}?template={template}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("template") { type = NavType.LongType; defaultValue = 0L }
                        )
                    ) { entry ->
                        NoteEditScreen(
                            noteId = entry.arguments?.getLong("id") ?: 0L,
                            templateId = entry.arguments?.getLong("template") ?: 0L,
                            onClose = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
