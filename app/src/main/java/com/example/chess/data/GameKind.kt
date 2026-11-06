package com.example.chess.data

import com.example.chess.checkers.CheckersLessonCatalog
import com.example.chess.lessons.LessonCatalog
import com.example.chess.lessons.LessonInfo
import com.example.chess.lessons.LessonUnit

/** The games the app teaches. */
enum class GameKind(val id: String, val title: String) {
    CHESS("chess", "Chess"),
    CHECKERS("checkers", "Checkers"),
    ;

    val units: List<LessonUnit<LessonInfo>>
        get() = when (this) {
            CHESS -> LessonCatalog.units
            CHECKERS -> CheckersLessonCatalog.units
        }

    val lessons: List<LessonInfo> get() = units.flatMap { it.lessons }

    fun nextLessonAfter(id: String): LessonInfo? {
        val list = lessons
        val i = list.indexOfFirst { it.id == id }
        return list.getOrNull(i + 1)
    }

    companion object {
        fun byId(id: String?): GameKind = entries.firstOrNull { it.id == id } ?: CHESS

        fun ofLesson(lessonId: String): GameKind =
            if (CheckersLessonCatalog.byId(lessonId) != null) CHECKERS else CHESS
    }
}
