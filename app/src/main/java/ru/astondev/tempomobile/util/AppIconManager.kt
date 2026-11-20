package ru.astondev.tempomobile.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.astondev.tempomobile.R
import ru.astondev.tempomobile.domain.AppIcon
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun apply(icon: AppIcon) {
        val pm = context.packageManager
        AppIcon.entries.forEach { entry ->
            val state = if (entry == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context, entry.aliasClassName()),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    companion object {
        fun AppIcon.aliasClassName(): String = when (this) {
            AppIcon.ForestDark -> "ru.astondev.tempomobile.launcher.IconForestDark"
            AppIcon.ForestLight -> "ru.astondev.tempomobile.launcher.IconForestLight"
            AppIcon.Ocean -> "ru.astondev.tempomobile.launcher.IconOcean"
            AppIcon.Slate -> "ru.astondev.tempomobile.launcher.IconSlate"
            AppIcon.Ember -> "ru.astondev.tempomobile.launcher.IconEmber"
            AppIcon.Bars -> "ru.astondev.tempomobile.launcher.IconBars"
        }

        fun AppIcon.drawableRes(): Int = when (this) {
            AppIcon.ForestDark -> R.drawable.ic_logwise_forest_dark
            AppIcon.ForestLight -> R.drawable.ic_logwise_forest_light
            AppIcon.Ocean -> R.drawable.ic_logwise_ocean
            AppIcon.Slate -> R.drawable.ic_logwise_slate
            AppIcon.Ember -> R.drawable.ic_logwise_ember
            AppIcon.Bars -> R.drawable.ic_logwise_bars
        }

        fun AppIcon.label(): String = when (this) {
            AppIcon.ForestDark -> "Лес · тёмный"
            AppIcon.ForestLight -> "Лес · светлый"
            AppIcon.Ocean -> "Океан"
            AppIcon.Slate -> "Сланец"
            AppIcon.Ember -> "Янтарь"
            AppIcon.Bars -> "Полосы"
        }
    }
}
