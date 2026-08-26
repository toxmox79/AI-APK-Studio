package de.aiapk.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.aiapk.studio.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            AIAPKTheme(settings.darkMode) {
                Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") { HomeScreen(vm, { nav.navigate("new") }, { nav.navigate("project/$it") }, { nav.navigate("settings") }) }
                        composable("new") { NewProjectScreen(vm, { nav.popBackStack() }) { id -> nav.navigate("project/$id") { popUpTo("new") { inclusive = true } } } }
                        composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
                        composable("project/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                            ProjectScreen(vm, back.arguments?.getLong("id") ?: 0L) { nav.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}
