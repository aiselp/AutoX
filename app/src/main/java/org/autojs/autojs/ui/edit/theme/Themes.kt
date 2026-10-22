package org.autojs.autojs.ui.edit.theme

import android.annotation.SuppressLint
import android.content.Context
import com.stardust.pio.UncheckedIOException
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.autojs.autojs.Pref
import org.autojs.autojs.ui.util.isSystemNightMode
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

/**
 * Created by Stardust on 2018/2/22.
 */
object Themes {
    private const val ASSETS_THEMES_PATH = "editor/theme"
    private const val DEFAULT_THEME = "Quiet Light"
    private const val DARK_THEME = "Dark (Visual Studio)"
    private var sThemes: List<Theme>? = null
    private var sDefaultTheme: Theme? = null

    @SuppressLint("CheckResult")
    @JvmStatic
    fun getAllThemes(context: Context): Observable<List<Theme>> {
        sThemes?.let { return Observable.just(it) }
        val subject = PublishSubject.create<List<Theme>>()
        getAllThemesInner(context)
            .subscribeOn(Schedulers.io())
            .subscribe({ themes ->
                setThemes(themes)
                subject.onNext(sThemes!!)
                subject.onComplete()
            }, Throwable::printStackTrace)
        return subject
    }

    fun getDefault(context: Context): Observable<Theme> {
        return sDefaultTheme?.let { Observable.just(it) }
            ?: getAllThemes(context).map { sDefaultTheme!! }
    }

    @Synchronized
    private fun setThemes(themes: List<Theme>) {
        if (sThemes != null) return
        sThemes = Collections.unmodifiableList(themes).also {
            sDefaultTheme = it.firstOrNull { theme -> DEFAULT_THEME == theme.name } ?: it[0]
        }
    }

    private fun getAllThemesInner(context: Context): Observable<List<Theme>> {
        sThemes?.let { return Observable.just(it) }
        return try {
            Observable.fromArray(*context.assets.list(ASSETS_THEMES_PATH))
                .map { file: String ->
                    context.assets.open("$ASSETS_THEMES_PATH/$file")
                        .use { Theme.fromJson(InputStreamReader(it)) }
                }
                .toList()
                .toObservable()
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @JvmStatic
    fun getCurrent(context: Context): Observable<Theme> {
        val currentTheme =
            (if (Pref.isNightModeEnabled() || context.isSystemNightMode()) DARK_THEME else Pref.getCurrentTheme())
                ?: return getDefault(context)
        return getAllThemes(context)
            .map { themes -> themes.firstOrNull { it.name == currentTheme } ?: themes[0] }
    }

    @JvmStatic
    fun setCurrent(name: String?) {
        Pref.setCurrentTheme(name)
    }
}