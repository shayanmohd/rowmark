package com.mohdshayan.rowmark.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mohdshayan.rowmark.billing.DebugUnlock
import com.mohdshayan.rowmark.di.ServiceLocator
import com.mohdshayan.rowmark.ui.components.LocalUnlock
import com.mohdshayan.rowmark.ui.components.UnlockAccess
import com.mohdshayan.rowmark.ui.counter.CounterScreen
import com.mohdshayan.rowmark.ui.pattern.PatternScreen
import com.mohdshayan.rowmark.ui.projectedit.ProjectEditScreen
import com.mohdshayan.rowmark.ui.projects.ProjectsScreen
import com.mohdshayan.rowmark.ui.settings.SettingsScreen
import com.mohdshayan.rowmark.ui.stash.StashScreen
import com.mohdshayan.rowmark.ui.stats.StatsScreen
import com.mohdshayan.rowmark.ui.unlock.UnlockScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable object Projects
@Serializable data class CounterRoute(val id: Long)
@Serializable data class ProjectEditRoute(val id: Long = 0L)
@Serializable data class PatternRoute(val projectId: Long)
@Serializable data class StatsRoute(val projectId: Long)
@Serializable object StashRoute
@Serializable object SettingsRoute

/** The one unlock screen. [reason] names the locked feature that sent the user here. */
@Serializable data class UnlockRoute(val reason: String = "")

/** A destination asked for from outside the composition: the widget's tap or the locked tile. */
sealed class PendingNav {
    data class Counter(val projectId: Long) : PendingNav()
    data class Unlock(val reason: String) : PendingNav()
}

@Composable
fun AppNav(pendingNav: MutableStateFlow<PendingNav?>) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val unlock by ServiceLocator.unlock.state.collectAsStateWithLifecycle()
    // Debug builds may force a tier for screenshots; the release source set always answers null.
    val forced = remember { DebugUnlock.forced(context) }
    val unlocked = forced ?: unlock.unlocked
    val access = remember(unlocked, navController) {
        UnlockAccess(unlocked) { reason ->
            navController.navigate(UnlockRoute(reason)) { launchSingleTop = true }
        }
    }
    val pending by pendingNav.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        when (val p = pending) {
            is PendingNav.Counter -> navController.navigate(CounterRoute(p.projectId)) { launchSingleTop = true }
            is PendingNav.Unlock -> navController.navigate(UnlockRoute(p.reason)) { launchSingleTop = true }
            null -> Unit
        }
        if (pending != null) pendingNav.value = null
    }
    // Every locked feature reads LocalUnlock: unlocked to run, open(reason) to explain and sell.
    CompositionLocalProvider(LocalUnlock provides access) {
        NavHost(navController = navController, startDestination = Projects) {
            composable<Projects> {
                ProjectsScreen(
                    onOpenProject = { navController.navigate(CounterRoute(it)) },
                    onNewProject = { navController.navigate(ProjectEditRoute()) },
                    onOpenStash = { navController.navigate(StashRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<CounterRoute> { entry ->
                val id = entry.toRoute<CounterRoute>().id
                CounterScreen(
                    projectId = id,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(ProjectEditRoute(id)) },
                    onOpenPattern = { navController.navigate(PatternRoute(id)) },
                    onOpenStats = { navController.navigate(StatsRoute(id)) },
                )
            }
            composable<ProjectEditRoute> { entry ->
                val id = entry.toRoute<ProjectEditRoute>().id
                ProjectEditScreen(
                    projectId = id,
                    onBack = { navController.popBackStack() },
                    onCreated = { newId ->
                        navController.navigate(CounterRoute(newId)) { popUpTo<Projects>() }
                    },
                )
            }
            composable<PatternRoute> { entry ->
                PatternScreen(projectId = entry.toRoute<PatternRoute>().projectId, onBack = { navController.popBackStack() })
            }
            composable<StatsRoute> { entry ->
                StatsScreen(projectId = entry.toRoute<StatsRoute>().projectId, onBack = { navController.popBackStack() })
            }
            composable<StashRoute> { StashScreen(onBack = { navController.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
            composable<UnlockRoute> { entry ->
                UnlockScreen(
                    reason = entry.toRoute<UnlockRoute>().reason,
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}
