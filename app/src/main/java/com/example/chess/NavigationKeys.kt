package com.example.chess

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

/** [game] is a GameKind id. */
@Serializable
data class LessonMapKey(val game: String) : NavKey

@Serializable
data class LessonKey(val lessonId: String) : NavKey

@Serializable
data class PracticeSetupKey(val game: String) : NavKey

/** [botId] null means two players sharing the device. [playerFirst] is White in chess, Red in checkers. */
@Serializable
data class MatchKey(val game: String, val botId: String?, val playerFirst: Boolean = true) : NavKey

@Serializable
data object SettingsKey : NavKey
