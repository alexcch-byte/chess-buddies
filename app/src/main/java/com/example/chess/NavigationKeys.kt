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

/** Finding a friend's tablet. [game] is a GameKind id: the game the host will play. */
@Serializable
data class NearbyKey(val game: String) : NavKey

/** A game against a friend on another tablet. The live connection itself is held by NearbyController. */
@Serializable
data object NearbyMatchKey : NavKey
