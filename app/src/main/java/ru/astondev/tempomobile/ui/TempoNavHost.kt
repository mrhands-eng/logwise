package ru.astondev.tempomobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import ru.astondev.tempomobile.data.repo.AuthRepository
import ru.astondev.tempomobile.ui.login.LoginScreen
import ru.astondev.tempomobile.ui.logtime.LogTimeScreen
import ru.astondev.tempomobile.ui.settings.SettingsScreen
import ru.astondev.tempomobile.ui.timer.TimerDialog
import ru.astondev.tempomobile.ui.week.QuickLogSheet
import ru.astondev.tempomobile.ui.week.WeekScreen
import java.time.LocalDate

object Routes {
    const val LOGIN = "login"
    const val WEEK = "week"
    const val SETTINGS = "settings"
    const val LOG_TIME =
        "log_time?worklogId={worklogId}&date={date}&duplicateFrom={duplicateFrom}&timerPrefill={timerPrefill}"

    fun logTime(
        worklogId: String? = null,
        date: String? = null,
        duplicateFrom: String? = null,
        timerPrefill: Boolean = false,
    ): String {
        val w = worklogId.orEmpty()
        val d = date.orEmpty()
        val dup = duplicateFrom.orEmpty()
        return "log_time?worklogId=$w&date=$d&duplicateFrom=$dup&timerPrefill=$timerPrefill"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthEntryPoint {
    fun authRepository(): AuthRepository
}

@Composable
fun TempoNavHost() {
    val context = LocalContext.current.applicationContext
    val startDestination = remember {
        val auth = EntryPointAccessors.fromApplication(context, AuthEntryPoint::class.java)
            .authRepository()
        if (auth.isLoggedIn()) Routes.WEEK else Routes.LOGIN
    }
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.WEEK) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.WEEK) { entry ->
            val refresh by entry.savedStateHandle
                .getStateFlow("refresh_week", false)
                .collectAsStateWithLifecycle()
            var quickLogDate by remember { mutableStateOf<LocalDate?>(null) }
            var showTimer by remember { mutableStateOf(false) }

            WeekScreen(
                onAdd = { date ->
                    navController.navigate(Routes.logTime(date = date.toString()))
                },
                onEdit = { worklogId ->
                    navController.navigate(Routes.logTime(worklogId = worklogId))
                },
                onDuplicate = { worklogId ->
                    navController.navigate(Routes.logTime(duplicateFrom = worklogId))
                },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onQuickLog = { date -> quickLogDate = date },
                onTimer = { showTimer = true },
                refreshSignal = refresh,
                onRefreshConsumed = {
                    entry.savedStateHandle["refresh_week"] = false
                },
            )

            quickLogDate?.let { date ->
                QuickLogSheet(
                    date = date,
                    onDismiss = { quickLogDate = null },
                    onSaved = {
                        quickLogDate = null
                        entry.savedStateHandle["refresh_week"] = true
                    },
                )
            }

            if (showTimer) {
                TimerDialog(
                    onDismiss = { showTimer = false },
                    onStopAndLog = {
                        showTimer = false
                        navController.navigate(
                            Routes.logTime(
                                date = LocalDate.now().toString(),
                                timerPrefill = true,
                            )
                        )
                    },
                )
            }
        }
        composable(
            route = Routes.LOG_TIME,
            arguments = listOf(
                navArgument("worklogId") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
                navArgument("duplicateFrom") { type = NavType.StringType; defaultValue = "" },
                navArgument("timerPrefill") { type = NavType.StringType; defaultValue = "false" },
            ),
        ) {
            LogTimeScreen(
                onDone = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("refresh_week", true)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
