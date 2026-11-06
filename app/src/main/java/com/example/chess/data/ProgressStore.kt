package com.example.chess.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.example.chess.engine.Bot
import com.example.chess.lessons.LessonInfo
import com.example.chess.ui.board.BoardTheme

/** Settings plus lesson stars and match wins, saved on the device. Reads are Compose state. */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("chess_buddies", Context.MODE_PRIVATE)

    private val stars = mutableStateMapOf<String, Int>()
    private val wins = mutableStateMapOf<String, Int>()

    init {
        for ((key, value) in prefs.all) {
            if (value !is Int) continue
            when {
                key.startsWith(STARS) -> stars[key] = value
                key.startsWith(WINS) -> wins[key] = value
            }
        }
    }

    private val soundState = mutableStateOf(prefs.getBoolean(KEY_SOUND, true))
    var soundOn: Boolean
        get() = soundState.value
        set(value) {
            soundState.value = value
            prefs.edit { putBoolean(KEY_SOUND, value) }
        }

    private val readAloudState = mutableStateOf(prefs.getBoolean(KEY_READ_ALOUD, true))
    /** Sir Hops reads lesson instructions out loud. */
    var readAloud: Boolean
        get() = readAloudState.value
        set(value) {
            readAloudState.value = value
            prefs.edit { putBoolean(KEY_READ_ALOUD, value) }
        }

    private val legalMovesState = mutableStateOf(prefs.getBoolean(KEY_LEGAL_MOVES, true))
    var showLegalMoves: Boolean
        get() = legalMovesState.value
        set(value) {
            legalMovesState.value = value
            prefs.edit { putBoolean(KEY_LEGAL_MOVES, value) }
        }

    private val coachTipsState = mutableStateOf(prefs.getBoolean(KEY_COACH_TIPS, true))
    var coachTips: Boolean
        get() = coachTipsState.value
        set(value) {
            coachTipsState.value = value
            prefs.edit { putBoolean(KEY_COACH_TIPS, value) }
        }

    private val dangerState = mutableStateOf(prefs.getBoolean(KEY_DANGER, true))
    var dangerAlerts: Boolean
        get() = dangerState.value
        set(value) {
            dangerState.value = value
            prefs.edit { putBoolean(KEY_DANGER, value) }
        }

    private val coordinatesState = mutableStateOf(prefs.getBoolean(KEY_COORDINATES, true))
    var showCoordinates: Boolean
        get() = coordinatesState.value
        set(value) {
            coordinatesState.value = value
            prefs.edit { putBoolean(KEY_COORDINATES, value) }
        }

    private val themeState = mutableStateOf(BoardTheme.byId(prefs.getString(KEY_THEME, null)))
    var boardTheme: BoardTheme
        get() = themeState.value
        set(value) {
            themeState.value = value
            prefs.edit { putString(KEY_THEME, value.id) }
        }

    private val gameState = mutableStateOf(GameKind.byId(prefs.getString(KEY_GAME, null)))
    /** The game picked on the home screen. */
    var selectedGame: GameKind
        get() = gameState.value
        set(value) {
            gameState.value = value
            prefs.edit { putString(KEY_GAME, value.id) }
        }

    fun starsFor(lessonId: String, challenge: Int): Int = stars["$STARS$lessonId:$challenge"] ?: 0

    /** Keeps the best result for each challenge. */
    fun recordStars(lessonId: String, challenge: Int, count: Int) {
        val key = "$STARS$lessonId:$challenge"
        if (count <= (stars[key] ?: 0)) return
        stars[key] = count
        prefs.edit { putInt(key, count) }
    }

    fun lessonStars(lesson: LessonInfo): Int = (0 until lesson.challengeCount).sumOf { starsFor(lesson.id, it) }
    fun lessonMaxStars(lesson: LessonInfo): Int = lesson.challengeCount * 3
    fun isLessonFinished(lesson: LessonInfo): Boolean = (0 until lesson.challengeCount).all { starsFor(lesson.id, it) > 0 }

    fun totalStars(game: GameKind): Int = game.lessons.sumOf { lessonStars(it) }
    fun maxStars(game: GameKind): Int = game.lessons.sumOf { lessonMaxStars(it) }
    fun allStars(): Int = GameKind.entries.sumOf { totalStars(it) }

    /** The first lesson of [game] the child hasn't finished yet. */
    fun nextLesson(game: GameKind): LessonInfo? = game.lessons.firstOrNull { !isLessonFinished(it) }

    // Chess wins keep their original key so existing progress carries over.
    private fun winsKey(game: GameKind, bot: Bot) = if (game == GameKind.CHESS) "$WINS${bot.id}" else "$WINS${game.id}:${bot.id}"

    fun winsAgainst(game: GameKind, bot: Bot): Int = wins[winsKey(game, bot)] ?: 0
    fun totalWins(game: GameKind): Int = Bot.entries.sumOf { winsAgainst(game, it) }
    fun allWins(): Int = GameKind.entries.sumOf { totalWins(it) }

    fun recordWin(game: GameKind, bot: Bot) {
        val key = winsKey(game, bot)
        val count = (wins[key] ?: 0) + 1
        wins[key] = count
        prefs.edit { putInt(key, count) }
    }

    fun resetProgress() {
        prefs.edit {
            (stars.keys + wins.keys).forEach { remove(it) }
        }
        stars.clear()
        wins.clear()
    }

    private companion object {
        const val STARS = "stars:"
        const val WINS = "wins:"
        const val KEY_SOUND = "sound"
        const val KEY_READ_ALOUD = "read_aloud"
        const val KEY_LEGAL_MOVES = "legal_moves"
        const val KEY_COACH_TIPS = "coach_tips"
        const val KEY_DANGER = "danger_alerts"
        const val KEY_COORDINATES = "coordinates"
        const val KEY_THEME = "board_theme"
        const val KEY_GAME = "selected_game"
    }
}
