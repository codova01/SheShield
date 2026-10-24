package com.example.sheshield

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val VAULT = "vault"
    const val HELPLINES = "helplines"
    const val ROUTES = "routes"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.ONBOARDING) { Placeholder("Onboarding") }
        composable(Routes.CONTACTS) { Placeholder("Trusted contacts") }
        composable(Routes.VAULT) { Placeholder("Evidence vault") }
        composable(Routes.HELPLINES) { Placeholder("Helplines") }
        composable(Routes.ROUTES) { Placeholder("Safe route planner") }
    }
}

@Composable
fun HomeScreen(nav: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Text("SheShield", style = MaterialTheme.typography.headlineLarge)
        Button(onClick = { /* SOS comes later */ }, modifier = Modifier.fillMaxWidth().height(80.dp)) {
            Text("SOS")
        }
        OutlinedButton(onClick = { nav.navigate(Routes.ROUTES) }, modifier = Modifier.fillMaxWidth()) { Text("Plan a safe route") }
        OutlinedButton(onClick = { nav.navigate(Routes.CONTACTS) }, modifier = Modifier.fillMaxWidth()) { Text("Trusted contacts") }
        OutlinedButton(onClick = { nav.navigate(Routes.VAULT) }, modifier = Modifier.fillMaxWidth()) { Text("Evidence vault") }
        OutlinedButton(onClick = { nav.navigate(Routes.HELPLINES) }, modifier = Modifier.fillMaxWidth()) { Text("Helplines") }
    }
}

@Composable
fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}