package com.example.chess.lessons

import com.example.chess.engine.Piece
import com.example.chess.engine.Square
import com.example.chess.lessons.LessonRules.squares

/** Every lesson in the app, grouped into units and listed in the order kids should learn them. */
object LessonCatalog {

    private fun sq(name: String) = Square.parse(name)

    private val rook = Lesson(
        id = "rook",
        title = "The Rook",
        tagline = "Straight lines, like a train on its tracks",
        icon = Piece.ROOK,
        intro = listOf(
            "This is the Rook! It looks like a castle tower.",
            "The Rook moves in straight lines: up, down, left or right. It can go as far as it likes, but it can't jump over other pieces.",
        ),
        challenges = listOf(
            Challenge("8/8/8/8/8/8/8/R7 w - - 0 1", "Tap the Rook, then tap the star to move there!", Goal.CollectStars(squares("a6"))),
            Challenge("8/8/8/8/8/8/8/R7 w - - 0 1", "Collect both stars!", Goal.CollectStars(squares("a7", "f7"))),
            Challenge("8/8/8/8/3R4/8/8/8 w - - 0 1", "Collect all the stars in as few moves as you can.", Goal.CollectStars(squares("d8", "h8", "h1", "b1"))),
            Challenge("8/8/p3p3/8/8/8/4p3/R7 w - - 0 1", "Rooks can capture! Land on a black pawn to take it off the board. Capture them all!", Goal.CaptureAll),
        ),
    )

    private val bishop = Lesson(
        id = "bishop",
        title = "The Bishop",
        tagline = "Slides along the diagonals",
        icon = Piece.BISHOP,
        intro = listOf(
            "Meet the Bishop! It wears a tall pointy hat.",
            "The Bishop moves diagonally, like sliding down a slanted slide. It can go as far as it wants until something is in the way.",
            "A Bishop that starts on a dark square stays on dark squares forever!",
        ),
        challenges = listOf(
            Challenge("8/8/8/8/8/8/8/2B5 w - - 0 1", "Slide the Bishop to the star.", Goal.CollectStars(squares("h6"))),
            Challenge("8/8/8/8/8/8/8/2B5 w - - 0 1", "Collect all the stars!", Goal.CollectStars(squares("a3", "c5", "f8"))),
            Challenge("8/8/8/8/8/8/8/5B2 w - - 0 1", "This Bishop lives on light squares. Grab the stars!", Goal.CollectStars(squares("b5", "e8", "h5"))),
            Challenge("8/8/1p6/6p1/8/4p3/8/2B5 w - - 0 1", "Capture all the pawns with your Bishop!", Goal.CaptureAll),
        ),
    )

    private val queen = Lesson(
        id = "queen",
        title = "The Queen",
        tagline = "The most powerful piece",
        icon = Piece.QUEEN,
        intro = listOf(
            "The Queen is the most powerful piece on the board!",
            "She moves like a Rook AND like a Bishop: straight lines and diagonals, as far as she wants.",
        ),
        challenges = listOf(
            Challenge("8/8/8/8/8/8/8/3Q4 w - - 0 1", "Collect the stars with your Queen.", Goal.CollectStars(squares("d7", "h3"))),
            Challenge("8/8/8/8/8/8/8/3Q4 w - - 0 1", "So many stars! Collect them all.", Goal.CollectStars(squares("a4", "e8", "h5", "h1"))),
            Challenge("8/1p3p2/8/8/2p5/5p2/8/3Q4 w - - 0 1", "Capture every pawn!", Goal.CaptureAll),
        ),
    )

    private val king = Lesson(
        id = "king",
        title = "The King",
        tagline = "The most important piece",
        icon = Piece.KING,
        intro = listOf(
            "This is the King, the most important piece of all!",
            "The King moves just one square at a time, in any direction.",
            "If your King gets trapped, you lose the game. So always keep your King safe!",
        ),
        challenges = listOf(
            Challenge("8/8/8/8/8/8/8/4K3 w - - 0 1", "One step at a time! Collect the stars.", Goal.CollectStars(squares("d2", "e3", "f4"))),
            Challenge("8/8/8/8/4K3/8/8/8 w - - 0 1", "Walk the King to every star.", Goal.CollectStars(squares("c6", "e6", "g6"))),
            Challenge("8/8/8/8/2p2p2/8/8/4K3 w - - 0 1", "The King can capture too! Capture both pawns, but the King can never step onto a square a pawn attacks.", Goal.CaptureAll),
        ),
    )

    private val knight = Lesson(
        id = "knight",
        title = "The Knight",
        tagline = "Jumps in an L shape",
        icon = Piece.KNIGHT,
        intro = listOf(
            "The Knight is a horse, and it's the trickiest piece!",
            "It moves in an L shape: two squares in one direction, then one square to the side.",
            "The Knight is the only piece that can jump over other pieces!",
        ),
        challenges = listOf(
            Challenge("8/8/8/8/8/8/8/1N6 w - - 0 1", "Hop the Knight to the star.", Goal.CollectStars(squares("c3"))),
            Challenge("8/8/8/8/8/8/8/6N1 w - - 0 1", "Collect the stars. Remember the L shape!", Goal.CollectStars(squares("f3", "e5", "g6"))),
            Challenge("8/8/8/8/8/8/PPPP4/1N6 w - - 0 1", "Knights can jump over other pieces! Collect the stars.", Goal.CollectStars(squares("a3", "c3"))),
            Challenge("8/4p3/2p3p1/8/3N1p2/8/8/8 w - - 0 1", "Capture all the pawns with your Knight!", Goal.CaptureAll),
        ),
    )

    private val pawn = Lesson(
        id = "pawn",
        title = "The Pawn",
        tagline = "Small but brave",
        icon = Piece.PAWN,
        intro = listOf(
            "Pawns are small, but they're brave!",
            "A pawn moves straight forward, one square at a time. On its very first move, it may jump forward two squares.",
            "Pawns never move backward. And here's the funny part: they capture diagonally, one square forward!",
        ),
        challenges = listOf(
            Challenge("8/8/8/8/8/8/4P3/8 w - - 0 1", "On its first move a pawn can jump two squares. Reach the star in one move!", Goal.CollectStars(squares("e4"))),
            Challenge("8/8/8/8/8/8/4P3/8 w - - 0 1", "March forward to the star.", Goal.CollectStars(squares("e6"))),
            Challenge("8/8/8/8/4p3/3p4/2P5/8 w - - 0 1", "Pawns capture diagonally! Capture both black pawns.", Goal.CaptureAll),
            Challenge("8/8/8/3pp3/4P3/8/8/8 w - - 0 1", "Pawns can't capture straight ahead, so this pawn is stuck! Find another way to the star.", Goal.CollectStars(squares("d7"))),
        ),
    )

    private val capture = Lesson(
        id = "capture",
        title = "Capturing",
        tagline = "Which pieces are worth the most?",
        icon = -Piece.KNIGHT,
        intro = listOf(
            "When a piece lands on an enemy piece, it captures it and takes it off the board!",
            "Pieces are worth different points: a Pawn is worth 1, a Knight or Bishop 3, a Rook 5, and the Queen 9.",
            "Capture pieces that nobody is protecting, and watch out that your pieces don't get captured!",
        ),
        challenges = listOf(
            Challenge(
                "8/4p3/3p4/8/8/8/8/3R4 w - - 0 1",
                "Capture both pawns, but never land where a black pawn could capture your Rook!",
                Goal.CaptureAll,
                safeOnly = true,
            ),
            Challenge(
                "8/7n/2p5/1n6/8/3B4/8/8 w - - 0 1",
                "Your Bishop can capture a Knight. Which Knight is NOT protected?",
                Goal.CaptureOn(sq("h7")),
                wrongText = "Oops! That Knight was protected by a pawn. Find the piece nobody is guarding!",
            ),
            Challenge(
                "8/8/5r2/2b5/4N3/8/3p4/8 w - - 0 1",
                "Your Knight can capture three different pieces. Take the one worth the most points!",
                Goal.CaptureOn(sq("f6")),
                wrongText = "That's a capture, but there's a piece worth more. Rooks are worth 5 points!",
            ),
            Challenge(
                "8/8/8/3q1r2/4P3/8/8/8 w - - 0 1",
                "Your pawn can capture! Which piece should it take?",
                Goal.CaptureOn(sq("d5")),
                wrongText = "A Rook is nice, but the Queen is worth 9 points!",
            ),
            Challenge(
                "8/2p3p1/1p5p/8/8/8/8/Q7 w - - 0 1",
                "Capture all the pawns with your Queen. Some pawns protect each other, so pick your order carefully!",
                Goal.CaptureAll,
                safeOnly = true,
            ),
        ),
    )

    private val promotion = Lesson(
        id = "promotion",
        title = "Pawn Promotion",
        tagline = "A pawn's secret power",
        icon = Piece.QUEEN,
        intro = listOf(
            "Here's a pawn's secret power!",
            "When a pawn reaches the very last row, it gets promoted. It can turn into a Queen, Rook, Bishop or Knight!",
            "Most of the time you should pick a Queen, because she's the strongest.",
        ),
        challenges = listOf(
            Challenge("8/4P3/8/8/8/8/8/8 w - - 0 1", "Move your pawn to the last row and choose a new piece!", Goal.Promote),
            Challenge("8/8/8/1P6/8/8/8/8 w - - 0 1", "March all the way to the end and promote!", Goal.Promote),
            Challenge("2rnr3/3P4/8/8/8/8/8/8 w - - 0 1", "Your pawn is blocked! Can you capture your way to the last row?", Goal.Promote),
            Challenge("8/6P1/8/8/8/8/8/8 w - - 0 1", "Promote your pawn, then use your new piece to grab the star!", Goal.CollectStars(squares("a1"))),
        ),
    )

    private val check = Lesson(
        id = "check",
        title = "Check!",
        tagline = "Attack the King",
        icon = -Piece.KING,
        intro = listOf(
            "When a piece attacks the King, we say CHECK!",
            "A King in check must escape right away. There are three ways: move the King, block the attack, or capture the attacker.",
        ),
        challenges = listOf(
            Challenge("4k3/8/8/8/8/8/8/R3K3 w - - 0 1", "Put the black King in check with your Rook!", Goal.GiveCheck),
            Challenge("4k3/8/8/8/4N3/8/8/4K3 w - - 0 1", "Give check with your Knight!", Goal.GiveCheck),
            Challenge("4k3/8/8/8/8/8/8/4KB2 w - - 0 1", "Give check with your Bishop!", Goal.GiveCheck),
            Challenge(
                "4k3/r7/8/8/8/8/8/4K3 b - - 0 1",
                "The Rook says CHECK! Move your King to a safe square.",
                Goal.EscapeCheck,
                setupMove = "a7a1",
            ),
            Challenge(
                "7k/r7/8/8/8/3B4/5PPP/6K1 b - - 0 1",
                "Check again! This time your King can't move. Block the attack with your Bishop!",
                Goal.EscapeCheck,
                setupMove = "a7a1",
            ),
            Challenge(
                "4k3/8/8/6n1/8/8/6P1/4K3 b - - 0 1",
                "The Knight gives check! Capture the piece that is attacking your King.",
                Goal.CaptureOn(sq("f3")),
                setupMove = "g5f3",
                wrongText = "That's safe, but you can do better: capture the Knight!",
            ),
        ),
    )

    private val checkmate = Lesson(
        id = "checkmate",
        title = "Checkmate",
        tagline = "How you win the game",
        icon = Piece.KING,
        intro = listOf(
            "Checkmate is how you win the game!",
            "It's checkmate when the King is in check and there's NO way out: it can't move, nothing can block, and nobody can capture the attacker.",
        ),
        challenges = listOf(
            Challenge("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1", "The black King is trapped behind its own pawns. Checkmate it with your Rook!", Goal.Checkmate),
            Challenge("k7/8/1K6/8/8/8/7Q/8 w - - 0 1", "Your King and Queen work together. Find checkmate!", Goal.Checkmate),
            Challenge("4k3/R7/8/8/8/8/8/1R2K3 w - - 0 1", "Two Rooks make a ladder. Checkmate in one move!", Goal.Checkmate),
            Challenge("4k3/8/4K3/8/7Q/8/8/8 w - - 0 1", "Find the checkmate with your Queen!", Goal.Checkmate),
            Challenge("6rk/6pp/8/6N1/8/8/8/7K w - - 0 1", "The black King has no room at all. Can your Knight checkmate it?", Goal.Checkmate),
            Challenge(
                "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
                "This famous trap is called Scholar's Mate. Find the checkmate, and watch out for it in your own games!",
                Goal.Checkmate,
            ),
        ),
    )

    private val castling = Lesson(
        id = "castling",
        title = "Castling",
        tagline = "Tuck your King in safely",
        icon = Piece.ROOK,
        intro = listOf(
            "Castling is a special move that tucks your King away safely and brings a Rook into the game.",
            "To castle, move your King two squares toward a Rook. The Rook then hops over to the King's other side.",
            "You can only castle if the King and that Rook haven't moved yet, nothing is in between, and the King isn't in check or crossing an attacked square.",
        ),
        challenges = listOf(
            Challenge("4k3/8/8/8/8/8/PPPPPPPP/R3K2R w KQ - 0 1", "Castle on the King's side: move your King two squares to the right!", Goal.Castle(kingside = true)),
            Challenge("4k3/8/8/8/8/8/PPPPPPPP/R3K2R w KQ - 0 1", "Now castle on the Queen's side: two squares to the left!", Goal.Castle(kingside = false)),
            Challenge("4k3/8/b7/8/8/8/8/R3K2R w KQ - 0 1", "The black Bishop is watching f1, so your King can't pass through it. Find the castling move that IS allowed!", Goal.Castle(kingside = null)),
        ),
    )

    private val enPassant = Lesson(
        id = "en_passant",
        title = "En Passant",
        tagline = "A sneaky pawn capture",
        icon = Piece.PAWN,
        intro = listOf(
            "Here's a sneaky pawn trick called en passant. Say it like \"on pah-SAHN\". It's French for \"in passing\"!",
            "If an enemy pawn jumps two squares and lands right beside your pawn, your pawn can capture it as if it had moved just one square.",
            "You have to do it right away, on the very next move, or the chance is gone!",
        ),
        challenges = listOf(
            Challenge("8/3p4/8/4P3/8/8/8/8 b - - 0 1", "The black pawn just zoomed past your pawn! Capture it en passant.", Goal.EnPassant, setupMove = "d7d5"),
            Challenge("8/5p2/8/4P3/8/8/8/8 b - - 0 1", "It happened again! Capture en passant.", Goal.EnPassant, setupMove = "f7f5"),
        ),
    )

    private val stalemate = Lesson(
        id = "stalemate",
        title = "Stalemate",
        tagline = "When nobody wins",
        icon = -Piece.QUEEN,
        intro = listOf(
            "Not every game has a winner. Sometimes it's a tie, called a draw.",
            "Stalemate is a draw that happens when a player has NO legal moves, but their King is NOT in check.",
            "When you're winning, be careful! Leave the enemy King a square to move to until you can checkmate.",
        ),
        challenges = listOf(
            Challenge("7k/8/5K2/8/8/8/8/6Q1 w - - 0 1", "One move here is checkmate, and one is stalemate. Find the checkmate!", Goal.Checkmate),
            Challenge("7k/4Q3/6K1/8/8/8/8/8 w - - 0 1", "Checkmate in one move, and don't trap the King in a stalemate!", Goal.Checkmate),
        ),
    )

    private val forks = Lesson(
        id = "forks",
        title = "Forks",
        tagline = "Attack two pieces at once",
        icon = Piece.KNIGHT,
        intro = listOf(
            "A fork is when one piece attacks two enemy pieces at the same time.",
            "Your opponent can only save one of them, so you get to capture the other!",
            "Knights are great at forks because of their funny L-shaped jump.",
        ),
        challenges = listOf(
            Challenge("r3k3/8/8/1N6/8/8/8/4K3 w - - 0 1", "Find a Knight move that attacks the King AND the Rook!", Goal.Fork),
            Challenge("4k3/8/2r1r3/8/3P4/8/8/6K1 w - - 0 1", "Even a little pawn can fork! Attack both Rooks.", Goal.Fork),
            Challenge("4k3/3q4/8/8/4N3/8/8/4K3 w - - 0 1", "Fork the King and the Queen with your Knight. That's called a Royal Fork!", Goal.Fork),
            Challenge("4k3/8/8/7r/8/8/1Q6/4K3 w - - 0 1", "Use your Queen to attack the King and the Rook, without letting the Rook capture your Queen.", Goal.Fork),
        ),
    )

    val units: List<LessonUnit<Lesson>> = listOf(
        LessonUnit("Meet the Pieces", "♞", listOf(rook, bishop, queen, king, knight, pawn)),
        LessonUnit("Capture!", "🎯", listOf(capture, promotion)),
        LessonUnit("Attack the King", "⚔️", listOf(check, checkmate)),
        LessonUnit("Special Rules", "✨", listOf(castling, enPassant, stalemate)),
        LessonUnit("Clever Tricks", "🧠", listOf(forks)),
    )

    val lessons: List<Lesson> = units.flatMap { it.lessons }

    fun byId(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    fun next(id: String): Lesson? {
        val i = lessons.indexOfFirst { it.id == id }
        return lessons.getOrNull(i + 1)
    }
}
