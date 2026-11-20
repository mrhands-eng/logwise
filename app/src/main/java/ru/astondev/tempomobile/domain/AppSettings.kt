package ru.astondev.tempomobile.domain

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class AppPalette {
    Forest,
    Ocean,
    Slate,
    Ember,
}

enum class AppIcon {
    ForestDark,
    ForestLight,
    Ocean,
    Slate,
    Ember,
    Bars,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val palette: AppPalette = AppPalette.Forest,
    val appIcon: AppIcon = AppIcon.ForestDark,
    val hideWeekends: Boolean = true,
    val dailyTargetHours: Int = 8,
    val defaultDayStartHour: Int = 9,
    val defaultDayStartMinute: Int = 0,
)
