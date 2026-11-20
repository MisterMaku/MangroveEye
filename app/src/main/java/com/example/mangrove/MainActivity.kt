package com.example.mangrove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mangrove.theme.Mg
import com.example.mangrove.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MangroveTheme { App() } }
    }
}

@Composable
fun App() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(bottomBar = {
        if (route in listOf("home", "map")) CompactNavBar(
            route,
            onHome = { nav.navigate("home") { popUpTo("home"); launchSingleTop = true } },
            onMap = { nav.navigate("map") { popUpTo("home"); launchSingleTop = true } }
        )
    }) { pad ->
        NavHost(nav, "splash", Modifier.padding(pad)) {
            composable("splash") {
                SplashScreen { nav.navigate("home") { popUpTo("splash") { inclusive = true } } }
            }
            composable("home") { HomeScreen(vm, nav) }
            composable("upload") { UploadScreen(vm, nav) }
            composable("analyzing") { AnalyzingScreen(vm) }
            composable("map") { MapScreen(vm, nav) }
        }
    }
}

/** Slim bottom bar */
@Composable
private fun CompactNavBar(route: String?, onHome: () -> Unit, onMap: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 6.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            NavItem(route == "home", Icons.Outlined.Home, "Home", onHome, Modifier.weight(1f))
            NavItem(route == "map", Icons.Outlined.Map, "Map", onMap, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    val tint = if (selected) Mg.Forest2 else Mg.Muted
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (selected) Mg.GreenSoft else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 2.dp)
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Text(label, fontSize = 11.sp, color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}