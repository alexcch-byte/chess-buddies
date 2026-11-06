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

    // Strategy lessons are "find the best move" puzzles. The accepted moves were worked out with the engine,
    // and LessonCatalogTest checks that every puzzle is solvable and that not every move solves it.
    private fun moves(vararg uci: String) = Goal.PlayMove(uci.toSet())

    private val center = Lesson(
        id = "center",
        title = "Control the Center",
        tagline = "The middle of the board is the best place",
        icon = Piece.PAWN,
        intro = listOf(
            "The four squares in the middle of the board (d4, e4, d5 and e5) are the most important squares!",
            "A piece in the center can reach almost anywhere. A piece in the corner can hardly do anything.",
            "So at the start of a game, fight for the center!",
        ),
        challenges = listOf(
            Challenge(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "Take the center! Push your e-pawn or your d-pawn two squares.",
                moves("e2e4", "d2d4"),
                wrongText = "That's fine, but the middle squares are the best. Push your e-pawn or d-pawn two squares!",
            ),
            Challenge(
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
                "Both sides have a pawn in the center. Bring a Knight out to help fight for it!",
                moves("g1f3", "b1c3"),
                wrongText = "Knights love the center! Try moving a Knight to f3 or c3.",
            ),
            Challenge(
                "4k3/8/8/8/8/8/8/1N2K3 w - - 0 1",
                "A Knight on the edge can't do much. Which move puts it where it looks at the center?",
                moves("b1c3"),
                wrongText = "On the edge, a Knight sees only a few squares. Move it to c3, where it eyes the center!",
            ),
            Challenge(
                "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
                "Black's pawn on e5 holds the center. Challenge it with your d-pawn!",
                moves("d2d4"),
                wrongText = "Good thinking, but this time fight for the center with a pawn: push your d-pawn to d4!",
            ),
        ),
    )

    private val develop = Lesson(
        id = "develop",
        title = "Develop Your Pieces",
        tagline = "Get everyone into the game",
        icon = Piece.BISHOP,
        intro = listOf(
            "Developing means bringing your Knights and Bishops out from their starting squares so they can join the game.",
            "Pieces stuck at home can't help you! Try to get every Knight and Bishop out early.",
            "Don't bring your Queen out too soon, because she can get chased around. And try not to move the same piece twice.",
        ),
        challenges = listOf(
            Challenge(
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
                "It's the opening! Bring out a Knight or a Bishop.",
                moves("b1c3", "f1e2", "f1d3", "f1c4", "f1b5", "g1e2", "g1f3"),
                wrongText = "Not quite! In the opening, bring a Knight or Bishop out toward the center. Pawns and the Queen can wait.",
            ),
            Challenge(
                "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
                "Your Knight is out. Now develop your Bishop!",
                moves("f1e2", "f1d3", "f1c4", "f1b5"),
                wrongText = "Your Bishop is still asleep at home! Wake it up with a move like Bc4.",
            ),
            Challenge(
                "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
                "Your Bishop and Knight are out, but one piece is still asleep. Develop your other Knight!",
                moves("b1c3"),
                wrongText = "Your Knight on b1 is still at home. Bring it out to c3!",
            ),
        ),
    )

    private val kingSafety = Lesson(
        id = "king_safety",
        title = "Keep Your King Safe",
        tagline = "Castle early and tuck him away",
        icon = Piece.KING,
        intro = listOf(
            "Your King is the most important piece, so keep him safe! In the middle of the board he can be attacked from everywhere.",
            "The best way to protect him is to castle early. He hides behind his pawns and the Rook comes into the game.",
            "Try to castle in the first ten moves, once your Knight and Bishop are out.",
        ),
        challenges = listOf(
            Challenge(
                "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
                "Your Knight and Bishop are out, so it's time to castle on the King's side!",
                Goal.Castle(kingside = true),
            ),
            Challenge(
                "rnbqk2r/pppp1ppp/5n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
                "Black is getting ready to attack. Tuck your King away with a castle!",
                Goal.Castle(kingside = null),
            ),
            Challenge(
                "r3kbnr/pppqpppp/2n5/3p1b2/3P1B2/2N5/PPPQPPPP/R3KBNR w KQkq - 6 5",
                "The pieces on the Queen's side have moved out of the way. Castle that way!",
                Goal.Castle(kingside = false),
            ),
        ),
    )

    private val lookFirst = Lesson(
        id = "look_first",
        title = "Look Before You Move",
        tagline = "Don't leave your pieces hanging",
        icon = -Piece.BISHOP,
        intro = listOf(
            "Before every move, ask yourself: is anything of mine hanging? A piece is hanging when the enemy can capture it for free.",
            "You can save a piece by moving it away, by protecting it, or by capturing the piece that attacks it.",
            "Always look at the whole board before you move!",
        ),
        challenges = listOf(
            Challenge(
                "rnbqkbnr/1ppp1ppp/p7/1B2p3/4P3/8/PPPP1PPP/RNBQK1NR w KQkq - 0 3",
                "Black's pawn is about to capture your Bishop! Move it to a square where nothing can capture it.",
                moves("b5a4", "b5c4", "b5d3", "b5e2", "b5f1"),
                wrongText = "Oh no, something of yours can still be captured! Move the Bishop where no black piece can reach it.",
            ),
            Challenge(
                "rnbqkb1r/pppp1ppp/5n2/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR w KQkq - 2 3",
                "Black's Knight attacks your Queen! Move her to a safe square.",
                moves("h5g5", "h5f5", "h5e5", "h5h4", "h5h3", "h5f3", "h5e2", "h5d1"),
                wrongText = "Your Queen can still be captured! Find a square that no black piece attacks.",
            ),
            Challenge(
                "4k3/8/8/2b5/3N4/8/2P1P3/4K3 w - - 0 1",
                "Black's Bishop attacks your Knight, and nothing protects it. Move it away, or protect it with a pawn!",
                moves("c2c3", "e2e3", "d4e6", "d4f5", "d4f3", "d4b3", "d4b5", "d4c6"),
                wrongText = "Your Knight can still be captured for free! Protect it with a pawn, or move it to safety.",
            ),
        ),
    )

    private val trades = Lesson(
        id = "trades",
        title = "Smart Trades",
        tagline = "Trade pieces when you're ahead",
        icon = -Piece.ROOK,
        intro = listOf(
            "When you have more than your opponent, swapping pieces one for one makes your lead even bigger.",
            "With fewer pieces left, there are fewer tricks to watch out for, and it gets easier to win!",
            "But if you're behind, don't trade. Keep as many pieces as you can.",
        ),
        challenges = listOf(
            Challenge(
                "4k3/4p3/3q4/8/3Q4/8/R7/4K3 w - - 0 1",
                "You have an extra Rook! Trade Queens to make winning easier.",
                moves("d4d6"),
                wrongText = "When you're ahead, trade! Capture the Queen on d6. Black will take back, but you keep your extra Rook.",
            ),
            Challenge(
                "3rk3/pp3ppp/8/8/8/2N5/PP3PPP/3RK3 w - - 0 1",
                "You're a Knight ahead. Trade Rooks on the open file!",
                moves("d1d8"),
                wrongText = "Take the trade! Your Rook can capture Black's Rook on d8. Black takes back, and you're still ahead.",
            ),
        ),
    )

    private val rookFiles = Lesson(
        id = "rook_files",
        title = "Rooks Love Open Files",
        tagline = "Give your Rooks room to run",
        icon = Piece.ROOK,
        intro = listOf(
            "An open file is a column of squares with no pawns on it. Rooks are strongest there, because nothing blocks their way!",
            "Another great spot is the seventh row, where a Rook can attack lots of pawns.",
            "Wake up your Rooks! A sleepy Rook stuck behind its own pawn does nothing.",
        ),
        challenges = listOf(
            Challenge(
                "4k3/pp3ppp/8/8/8/8/PP3PPP/R3K3 w - - 0 1",
                "Put your Rook on an open file: a column with no pawns!",
                moves("a1c1", "a1d1"),
                wrongText = "A Rook is strongest on a column with no pawns in the way. Look for an open file!",
            ),
            Challenge(
                "6k1/pp3ppp/8/8/8/8/PP3PPP/3RK3 w - - 0 1",
                "Your Rook is on an open file. Now march it to the seventh row, where it attacks the pawns!",
                moves("d1d7"),
                wrongText = "Rooks love the seventh row, where they attack the enemy pawns. March your Rook up to d7!",
            ),
            Challenge(
                "4k3/pp3ppp/8/8/8/8/PP3PPP/R2R2K1 w - - 0 1",
                "One of your Rooks is asleep behind a pawn. Wake it up by moving it to an open file!",
                moves("a1c1"),
                wrongText = "The Rook on a1 is stuck behind its own pawn. Move it to the open c-file!",
            ),
        ),
    )

    private val endgamePlans = Lesson(
        id = "endgame_plans",
        title = "Endgame Plans",
        tagline = "Wake up your King and push your pawns",
        icon = -Piece.PAWN,
        intro = listOf(
            "In the endgame, only a few pieces are left, so there's less danger. Your King can come out and fight!",
            "Walk your King toward the center and toward the action. A King hiding in the corner doesn't help.",
            "A passed pawn has no enemy pawns in front of it. Push it to make a new Queen!",
        ),
        challenges = listOf(
            Challenge(
                "4k3/8/8/8/8/8/6P1/6K1 w - - 0 1",
                "Time to wake up your King! Walk him toward the center and collect the stars.",
                Goal.CollectStars(squares("f2", "e3", "d4")),
            ),
            Challenge(
                "6k1/6pp/8/8/8/8/P5P1/4K3 w - - 0 1",
                "You have two pawns, but only one is a passed pawn that nothing can stop. Push it!",
                moves("a2a3", "a2a4"),
                wrongText = "That pawn has enemy pawns in its way. Find the passed pawn, where no black pawn can stop it!",
            ),
        ),
    )

    val units: List<LessonUnit<Lesson>> = listOf(
        LessonUnit("Meet the Pieces", "♞", listOf(rook, bishop, queen, king, knight, pawn)),
        LessonUnit("Capture!", "🎯", listOf(capture, promotion)),
        LessonUnit("Attack the King", "⚔️", listOf(check, checkmate)),
        LessonUnit("Special Rules", "✨", listOf(castling, enPassant, stalemate)),
        LessonUnit("Clever Tricks", "🧠", listOf(forks)),
        LessonUnit("Opening Strategy", "🚀", listOf(center, develop, kingSafety)),
        LessonUnit("Winning Plans", "🏆", listOf(lookFirst, trades, rookFiles, endgamePlans)),
    )

    val lessons: List<Lesson> = units.flatMap { it.lessons }

    fun byId(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    fun next(id: String): Lesson? {
        val i = lessons.indexOfFirst { it.id == id }
        return lessons.getOrNull(i + 1)
    }
}
