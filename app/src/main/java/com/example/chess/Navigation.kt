package com.example.chess

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.chess.audio.SoundFx
import com.example.chess.checkers.CheckersLessonCatalog
import com.example.chess.data.GameKind
import com.example.chess.data.ProgressStore
import com.example.chess.engine.Bot
import com.example.chess.lessons.LessonCatalog
import com.example.chess.lessons.LessonInfo
import com.example.chess.ui.screens.CheckersLessonScreen
import com.example.chess.ui.screens.CheckersMatchScreen
import com.example.chess.ui.screens.ChessLessonScreen
import com.example.chess.ui.screens.ChessMatchScreen
import com.example.chess.ui.screens.HomeScreen
import com.example.chess.ui.screens.LessonMapScreen
import com.example.chess.ui.screens.NearbyController
import com.example.chess.ui.screens.NearbyScreen
import com.example.chess.ui.screens.PracticeSetupScreen
import com.example.chess.ui.screens.SettingsScreen

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val progress = remember { ProgressStore(context.applicationContext) }
    val sound = remember { SoundFx { progress.soundOn } }
    DisposableEffect(sound) {
        onDispose { sound.release() }
    }
    // Playing a friend on another tablet: lives as long as the app, and hangs up if the app is closed.
    val nearbyScope = rememberCoroutineScope()
    val nearby = remember { NearbyController(context.applicationContext, nearbyScope) }
    DisposableEffect(nearby) {
        onDispose { nearby.leave() }
    }
    val backStack = rememberNavBackStack(HomeKey)
    val navigateBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            context.findActivity()?.finish()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = navigateBack,
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    progress = progress,
                    onLearn = { backStack.add(LessonMapKey(it.id)) },
                    onPractice = { backStack.add(PracticeSetupKey(it.id)) },
                    onFriend = { backStack.add(MatchKey(it.id, botId = null)) },
                    onNearby = { backStack.add(NearbyKey(it.id)) },
                    onSettings = { backStack.add(SettingsKey) },
                )
            }

            entry<LessonMapKey> { key ->
                LessonMapScreen(
                    game = GameKind.byId(key.game),
                    progress = progress,
                    onOpenLesson = { backStack.add(LessonKey(it.id)) },
                    onBack = navigateBack,
                )
            }

            entry<LessonKey> { key ->
                val openNext: (LessonInfo) -> Unit = { next ->
                    backStack.removeLastOrNull()
                    backStack.add(LessonKey(next.id))
                }
                val checkersLesson = CheckersLessonCatalog.byId(key.lessonId)
                if (checkersLesson != null) {
                    CheckersLessonScreen(
                        lesson = checkersLesson,
                        progress = progress,
                        sound = sound,
                        onNextLesson = openNext,
                        onBack = navigateBack,
                    )
                } else {
                    ChessLessonScreen(
                        lesson = LessonCatalog.byId(key.lessonId) ?: LessonCatalog.lessons.first(),
                        progress = progress,
                        sound = sound,
                        onNextLesson = openNext,
                        onBack = navigateBack,
                    )
                }
            }

            entry<PracticeSetupKey> { key ->
                val game = GameKind.byId(key.game)
                PracticeSetupScreen(
                    game = game,
                    progress = progress,
                    onStart = { bot, playerFirst -> backStack.add(MatchKey(game.id, bot.id, playerFirst)) },
                    onBack = navigateBack,
                )
            }

            entry<MatchKey> { key ->
                val bot = key.botId?.let { Bot.byId(it) }
                when (GameKind.byId(key.game)) {
                    GameKind.CHESS -> ChessMatchScreen(bot, key.playerFirst, progress, sound, onBack = navigateBack)
                    GameKind.CHECKERS -> CheckersMatchScreen(bot, key.playerFirst, progress, sound, onBack = navigateBack)
                }
            }

            entry<NearbyKey> { key ->
                NearbyScreen(
                    controller = nearby,
                    game = GameKind.byId(key.game),
                    // The lobby is replaced by the game, so "back" from the game goes to the home screen.
                    onMatchReady = {
                        backStack.removeLastOrNull()
                        backStack.add(NearbyMatchKey)
                    },
                    onBack = navigateBack,
                )
            }

            entry<NearbyMatchKey> {
                val session = nearby.session
                if (session == null) {
                    // Nothing to play (the connection was closed): go back.
                    LaunchedEffect(Unit) { navigateBack() }
                } else {
                    // Whichever way the player leaves, hang up so the friend is told.
                    DisposableEffect(session) {
                        onDispose { nearby.leave() }
                    }
                    // Taken once: the colours swap after a rematch, but the same game screen keeps running.
                    val firstAtStart = remember(session) { session.iAmFirst }
                    when (nearby.game) {
                        GameKind.CHESS -> ChessMatchScreen(null, firstAtStart, progress, sound, onBack = navigateBack, nearby = session)
                        GameKind.CHECKERS -> CheckersMatchScreen(null, firstAtStart, progress, sound, onBack = navigateBack, nearby = session)
                    }
                }
            }

            entry<SettingsKey> {
                SettingsScreen(
                    progress = progress,
                    onBack = navigateBack,
                )
            }
        }
    )
}
