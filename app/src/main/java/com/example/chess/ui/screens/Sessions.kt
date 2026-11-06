package com.example.chess.ui.screens

import com.example.chess.engine.Bot
import com.example.chess.lessons.LessonInfo
import com.example.chess.ui.components.Mood

/** What the shared lesson screen needs from a running lesson, whichever game it teaches. */
enum class LessonPhase { INTRO, PLAYING, BUSY, SOLVED, FINISHED }

interface LessonSession {
    val lesson: LessonInfo
    val phase: LessonPhase
    val introPage: Int
    val isLastIntroPage: Boolean
    val index: Int
    val challengeCount: Int
    val coachText: String
    val mood: Mood
    val moves: Int
    /** Fewest moves that solve a multi-move challenge, once known. */
    val par: Int?
    val earned: Int
    val isMultiMove: Boolean
    /** Short description of the current challenge's goal. */
    val goalLabel: String

    fun nextIntroPage()
    fun skipIntro()
    fun restartChallenge()
    fun nextChallenge()
    fun showHint()
}

data class MatchResult(val headline: String, val explanation: String, val emoji: String, val celebrate: Boolean)

/** What the shared match screen needs from a running game. */
interface MatchSession {
    /** Null when two people share the device. */
    val bot: Bot?
    /** True when the player has the side that moves first (White in chess, Red in checkers). */
    val playerFirst: Boolean
    val firstSideName: String
    val secondSideName: String
    var flipped: Boolean
    val coachText: String
    val mood: Mood
    val thinking: Boolean
    val isHumanTurn: Boolean
    val canUndo: Boolean
    /** Set when the coach suggests taking back a risky move. */
    val takeback: String?
    val firstSideToMove: Boolean
    val result: MatchResult?
    var showResult: Boolean
    /** Changes for every finished game, to restart the celebration. */
    val gameNumber: Int

    fun requestHint()
    fun undo()
    fun newGame()
    fun acceptTakeback()
    fun declineTakeback()
}
