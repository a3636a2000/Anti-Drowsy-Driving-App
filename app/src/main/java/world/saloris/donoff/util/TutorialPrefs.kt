package world.saloris.donoff.util

import android.content.Context

object TutorialPrefs {
    private const val PREFS_NAME = "isFirst"

    fun isFirstVisit(context: Context, screenKey: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(screenKey, true)
    }

    fun markVisited(context: Context, screenKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(screenKey, false)
            .apply()
    }

    fun runTutorialOnceIfNeeded(context: Context, screenKey: String) {
        if (!isFirstVisit(context, screenKey)) return
        // TODO: 튜토리얼 화면 구현
        markVisited(context, screenKey)
    }
}
