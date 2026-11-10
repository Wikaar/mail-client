package com.mailtabs.app.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Строки вне UI (уведомления, ошибки, тексты из ViewModel) на выбранном в приложении языке.
 * На Android 13+ контекст приложения уже следует per-app locale; на более старых версиях
 * AppCompat меняет язык только у активити, поэтому подменяем конфигурацию сами.
 */
@Singleton
class Strings @Inject constructor(
    @ApplicationContext private val app: Context,
    private val settings: AppSettings,
) {
    @Volatile
    private var cache: Pair<String, Context>? = null

    fun context(): Context {
        if (Build.VERSION.SDK_INT >= 33) return app
        val tag = settings.cached.language
        cache?.let { (t, c) -> if (t == tag) return c }
        val c = if (tag.isEmpty()) app else {
            val cfg = Configuration(app.resources.configuration)
            cfg.setLocales(LocaleList.forLanguageTags(tag))
            app.createConfigurationContext(cfg)
        }
        cache = tag to c
        return c
    }

    fun get(@StringRes id: Int, vararg args: Any): String = context().getString(id, *args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        context().resources.getQuantityString(id, count, *args)

    fun locale(): Locale = context().resources.configuration.locales[0] ?: Locale.getDefault()
}
