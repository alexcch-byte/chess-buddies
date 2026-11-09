# Chess Buddies

A chess and checkers teaching game for kids, for Android tablets and phones. It's written in Kotlin with Jetpack Compose.

**[Download the APK](https://alexcch-byte.github.io/chess-buddies/)** to install it on an Android or Fire tablet without the Play Store.

Sir Hops, a friendly knight, walks children through short interactive lessons and then coaches them through practice matches against five animal opponents. Pick **Chess** or **Checkers** at the top of the home screen. Each game has its own lessons, practice matches and two-player mode.

## Features

- **28 lessons with 96 challenges:** 21 chess lessons and 7 checkers lessons, including chess strategy (see [Lessons](#lessons)).
- **Practice matches** against five computer opponents, from very gentle to quite strong (see [Opponents](#opponents)).
- **Play a Friend:** two players share one screen, with a **Flip board** button.
- **Play Nearby:** two tablets, one game, over home Wi-Fi or Bluetooth, for chess and for checkers (see [Play Nearby](#play-nearby-two-tablets)). The browser version has it too, paired with an invite link (see [in the browser](#play-nearby-in-the-browser)).
- **Coaching from Sir Hops:**
  - Explains in plain words why a move isn't allowed, for example "Knights move in an L shape" or "You have to jump when you can!".
  - Warns right after a risky move and offers to take it back.
  - Points out a checkmate in one move, an unprotected enemy piece, or an available double jump.
  - Warns when the opponent threatens checkmate.
  - Shares short tips during the game.
- **Helpers:**
  - Dots show where a piece can move.
  - Red danger rings mark your pieces that can be captured.
  - A **Hint** button shows a green arrow.
  - **Undo** takes back moves.
- **Stars and trophies:** each challenge earns 1 to 3 stars. Wins are counted per opponent and per game.
- **Full rules for both games** (see [Rules](#rules)).
- **Synthesized sounds:** made in code, so the app ships with no audio files.
- **Spoken lessons:** Sir Hops reads every lesson instruction, hint, mistake and bit of praise out loud, using the device's own text-to-speech voice (no recordings). A 🔊 button next to his speech bubble says the line again. Turn it off in Settings.
- **Original artwork:** the chess pieces and checkers are vector shapes drawn in code.
- **Layout:** works in landscape and portrait.

## How to play

| Action | Chess | Checkers |
|--------|-------|----------|
| Move | Tap a piece, then tap a square. Or drag it. | Tap a checker, then tap a square. Or drag it. |
| See possible moves | Dots on empty squares, rings on captures | Dots on the next landing squares |
| Multi-jump | — | Tap each landing square in turn, or tap the last one straight away |
| Castle | Move the King two squares, or tap the King then the Rook | — |
| Promote / crown | A picker asks for Queen, Rook, Bishop or Knight | Automatic on the far row |

Buttons during a match:

- **Hint:** shows a suggested move as a green arrow.
- **Undo:** takes back your last move, plus the computer's reply.
- **New game:** starts again (after a confirmation).
- **Flip board:** turns the board around. This button appears in two-player mode only.

After a risky move, the coach shows **Take it back** and **Keep my move**.

## Play Nearby (two tablets)

**Play Nearby** on the home screen lets two children play one game on two tablets. It works the way the Gus & Leo detective game pairs its two tablets: no accounts, no servers, nothing leaves the room.

1. Pick **Wi-Fi** (both tablets on the same network) or **Bluetooth** (tablets close together).
2. One child taps **Start a game** and picks White or Black (Red or Black in checkers). The tablet shows a cheerful animal, like the 🦊, and **4 pictures**.
3. The other child taps **Join a game**, taps the matching game (Wi-Fi) or the other tablet's name (Bluetooth), then taps the **same 4 pictures**. That is the whole pairing.
4. The game starts. Each tablet shows its own player at the bottom. Moves appear on the other tablet straight away, and the usual rules, sounds and coach messages apply.
5. **Play again** asks the friend; if they say yes the colours swap. Leaving asks first and tells the friend.

How it works:

- **Wi-Fi:** the host shouts a small UDP broadcast beacon once a second (game, version, port, a random id; nothing about the pictures). The joining tablet lists the games it hears, then connects over TCP with newline-separated text messages. A grown-up can type the host's number (shown small on the host's screen) if a router blocks broadcasts.
- **Bluetooth:** the host listens on an RFCOMM service (secure and insecure at once, because Android's automatic pairing is too quick for a child to accept). The joiner lists paired and nearby tablets. On Android 11 and older, Bluetooth scanning needs the Location permission.
- **Picture code:** the code never goes over the air. The host sends a random number and the guest answers with a hash of that number plus the 4 pictures. Five wrong tries within a minute switch joining off for 30 seconds. The order the pictures are tapped in does not matter.
- **In the game:** each tablet keeps its own copy of the game and the two swap moves, numbered. A heartbeat every second notices a dropped tablet within 5 seconds; the board waits ("Waiting for your friend…") and the guest reconnects on its own. After a reconnect both tablets swap their full move lists and the one that is behind catches up (if the lists disagree, the host's wins). A move that arrives out of order makes the tablet ask for the whole list.
- There is no Undo, Hint or Flip board in a nearby game, and wins are not counted against the computer opponents.
- The browser version has Play Nearby too, but pairs differently (below), so a tablet running the app and a browser can't play each other.

### Play Nearby in the browser

A web page can't scan the Wi-Fi or use Bluetooth, so two browsers pair by swapping two short codes (a grown-up helps), then talk directly using a WebRTC data channel. No game data goes through a server.

1. One player opens **Play Nearby**, picks their colour and taps **Start a game**. The page makes an **invite link**.
2. The friend opens that link on their device (any browser, tablet, phone or computer). It lands straight on joining and shows a **reply code**.
3. The friend sends the reply code back, the host pastes it and taps **Connect**. The game starts.

- By default both devices must be on the same Wi-Fi and nothing is contacted. **My friend is far away** also asks Google's public STUN service for each device's internet address (the moves still go straight between the two devices); it can't get through every home router.
- The codes are the connection descriptions (SDP) deflate-compressed into URL-safe text, about 650 characters (`docs/play/js/nearby/signal.js`: `CB1` + invite/reply + same-Wi-Fi/far-away + squeezed/plain + payload). They can be pasted with other text around them.
- The game rules are the app's `NearbySession` ported to JavaScript (`docs/play/js/nearby/session.js`): same handshake, heartbeat, move numbering, catch-up and play-again swap. Two differences: there is no picture code (the invite is the secret), and a quiet connection just pauses the game and resumes by itself. If the connection closes for good (the friend's tab was closed, say), the game ends and you invite again; there is no automatic reconnect.
- Not checked on real devices yet: the connection has been tried between two tabs of one browser, not across two devices on a real network.

## Lessons

Each lesson starts with a few intro pages from Sir Hops, then a set of challenges.

### Chess (21 lessons, 74 challenges)

| # | Lesson | What kids learn | Challenges |
|---|--------|-----------------|-----------:|
| 1 | The Rook | Straight lines; capturing | 4 |
| 2 | The Bishop | Diagonals; a bishop stays on its color | 4 |
| 3 | The Queen | Rook and bishop moves together | 3 |
| 4 | The King | One step at a time; a king can't step into danger | 3 |
| 5 | The Knight | The L-shaped jump | 4 |
| 6 | The Pawn | Forward moves, the first double step, diagonal captures | 4 |
| 7 | Capturing | Piece values; unprotected pieces | 5 |
| 8 | Pawn Promotion | Reaching the last row | 4 |
| 9 | Check! | Giving check; escaping by moving, blocking or capturing | 6 |
| 10 | Checkmate | Back-rank, queen, two-rook and smothered mates, Scholar's Mate | 6 |
| 11 | Castling | Kingside, queenside, and no castling through check | 3 |
| 12 | En Passant | The special pawn capture | 2 |
| 13 | Stalemate | Finding mate without stalemating | 2 |
| 14 | Forks | Knight, pawn, royal and queen forks | 4 |
| 15 | Control the Center | Fight for d4, e4, d5 and e5; Knights belong near the middle | 4 |
| 16 | Develop Your Pieces | Bring Knights and Bishops out early; Queen and pawns can wait | 3 |
| 17 | Keep Your King Safe | Castle early, on either side | 3 |
| 18 | Look Before You Move | Spot hanging pieces; move, protect or capture the attacker | 3 |
| 19 | Smart Trades | Swap pieces when you're ahead | 2 |
| 20 | Rooks Love Open Files | Open files, the seventh row, waking up a sleepy Rook | 3 |
| 21 | Endgame Plans | Walk the King to the center; push the passed pawn | 2 |

### Checkers (7 lessons, 22 challenges)

| # | Lesson | What kids learn | Challenges |
|---|--------|-----------------|-----------:|
| 1 | How Checkers Move | Diagonal steps forward on dark squares | 3 |
| 2 | Jumping | Capturing by jumping; choosing a safe jump | 3 |
| 3 | Double Jumps | Double and triple jumps; picking the biggest jump | 4 |
| 4 | Crown a King | Getting crowned; kings move and jump backward | 4 |
| 5 | You Must Jump! | The forced-jump rule; two-for-one traps | 3 |
| 6 | Stay Safe | Edges and neighbors protect your checkers | 2 |
| 7 | Winning the Game | Capturing the last checkers, or trapping them | 3 |

### Challenge types

- **Collect the stars.** Move to every star. Only your pieces move.
- **Capture them all.** Some challenges forbid landing where an enemy piece could capture you.
- **One-move puzzles.**
  - Chess: checkmate, give check, escape check, best capture, castle, en passant, fork, and strategy puzzles (**find the best move**, where several sensible moves can be right).
  - Checkers: jump a set number of checkers, find a safe move, win.
- **Set a trap.** This is a two-step checkers puzzle. First you offer a checker, then Black is forced to jump, and then you jump two of Black's checkers.

After a wrong answer, the board shows what goes wrong (for example, the opponent capturing your piece) and then resets so the child can try again.

### Stars

| Challenge | ⭐⭐⭐ | ⭐⭐ | ⭐ |
|-----------|-------|-----|----|
| Multi-move (stars, captures, crowning) | Done in the fewest moves possible | Up to 2 moves more | More than that |
| One-move puzzle | Solved on the first try | Second try | Later tries |

Using a hint caps the result at 2 stars. The best possible move count is worked out automatically for each challenge and shown as **Best**. Stars are saved on the device, and the best result per challenge is kept.

## Opponents

| Opponent | Style | Chess search | Checkers search |
|----------|-------|--------------|-----------------|
| 🐰 Bubbles the Bunny | Hops around and makes lots of mistakes | 70% random moves | 60% random moves |
| 🐱 Kiki the Kitten | Grabs pieces but forgets to look out | 1 move ahead, with mistakes | 2 moves ahead, with mistakes |
| 🦊 Felix the Fox | Spots captures and simple tricks | 2 moves ahead | 4 moves ahead |
| 🦉 Professor Owl | Thinks ahead; a real test | 3 moves ahead | 6 moves ahead |
| 🐉 Drake the Dragon | The forest champion | Up to 6 moves, 2.5 seconds | Up to 14 moves, 2.2 seconds |

"Moves ahead" counts each side's turn separately. Searches also follow every capture to the end, so the bots don't leave pieces hanging just past their horizon. The weaker bots add random noise to their choices so they make kid-sized mistakes. Each bot waits at least about 0.7 seconds before moving, so children can see the reply happen.

## Rules

**Chess** uses the full rules: castling, en passant, promotion, check, checkmate and stalemate. A game is also a draw by insufficient material, the 50-move rule, or threefold repetition.

**Checkers** uses American / English draughts rules on an 8 × 8 board:

- Red moves first.
- Jumps are compulsory, and a multi-jump must be finished.
- A man that reaches the far row is crowned, and that ends its turn.
- Kings move and jump one square in any direction.
- You win when the opponent has no checkers or no legal moves.
- A game is a draw after 40 moves each with no jump and no man moving, or after threefold repetition.

## Settings

| Setting | What it does |
|---------|--------------|
| Sounds | Clicks, dings and short tunes |
| Read lessons aloud | Sir Hops says each lesson instruction out loud (the 🔊 button still works when this is off) |
| Show possible moves | Move dots on the board |
| Coach tips | Risky-move warnings, hints about chances, and tips |
| Danger alerts | Red rings on your pieces that can be captured (matches against the computer) |
| Board letters and numbers | a–h and 1–8 around the board |
| Board colors | Meadow, Ocean, Candy or Wood |
| Reset progress | Clears all stars and wins (asks first) |

## Requirements

- Android 7.0 (API 24) or newer.
- Tested on an Amazon Fire HD 8 (Android 11) in landscape. Portrait layouts are supported too.
- Play Nearby asks for network and Bluetooth permissions (`INTERNET` for local Wi-Fi sockets, Wi-Fi state, multicast, and the Bluetooth permissions; Location on Android 11 and older for Bluetooth scanning). It only talks to the other tablet on the local network or over Bluetooth, and never to an internet server.
- Building needs Android Studio, or JDK 17+ with the Android SDK. The project uses Android Gradle Plugin 9.0.1, Kotlin 2.3.20 and Gradle 9.1.0.

## Building

Open the project in Android Studio, or build from the command line:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Installing on a Fire tablet

1. On the tablet, open **Settings → Device Options → About Fire Tablet** and tap **Serial Number** seven times to turn on Developer Options.
2. In **Settings → Device Options → Developer Options**, turn on **Enable ADB**.
3. Connect the tablet over USB and accept the debugging prompt.
4. Install the APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

The unit tests check:

- **Chess move generator:** compared against standard perft counts for the start position, Kiwipete and three other well-known test positions.
- **Checkers move generator:** compared against the published counts from the opening position, up to depth 6.
- **Lessons:** every challenge in both games starts from a legal position and can be solved. Every puzzle has at least one wrong answer, unless only one move is legal.
- **Chess bots:** every bot plays only legal moves, the stronger bots find mate-in-one and grab a hanging queen, and the search wins a king-and-queen endgame.
- **Checkers bots:** every bot plays only legal moves, the search finds the double jump, and it beats a random player.
- **Coach helpers:** hanging pieces, mate threats, and explanations of illegal moves.
- **Play Nearby:** two sessions talking over real sockets on the test computer: joining, wrong and locked-out picture codes, version mismatch, a full game, dropouts and reconnecting, catching up after a lost move, playing again and leaving. The beacon, the picture-code proof and the catch-up rule have their own tests. Bluetooth and the screens need real tablets and are not covered.

## Adding lessons

- **Chess** challenges live in `lessons/LessonCatalog.kt`. Each challenge is a FEN position, an instruction and a `Goal`. It can also have an opening move for the opponent (`setupMove`) and a custom message for wrong answers. Strategy puzzles use `Goal.PlayMove`, which lists every accepted move in UCI form (for example `moves("e2e4", "d2d4")`).
- **Checkers** challenges live in `checkers/CheckersLessons.kt`. Positions are built with `setup(red = "c3 e3", black = "d4")`, and every square must be a dark square.

Run the tests after adding a challenge. They catch impossible or illegal setups, and the star targets are worked out automatically.

The web version has its own copy of the lessons in `docs/play/js/lessons/`. When you add or change a lesson, change both, so the app and the web page stay the same.

## Web version

Chess Buddies also runs in a browser: <https://alexcch-byte.github.io/chess-buddies/play/>. It is plain HTML, CSS and JavaScript modules in `docs/play/`, with no build step, and it is a faithful port of the app's rules, bots, coach and lessons. Progress is saved in the browser's local storage and nothing is uploaded.

```bash
python -m http.server 8765 --directory docs   # then open http://localhost:8765/play/
node --test web-tests/chess.test.mjs web-tests/checkers.test.mjs web-tests/lessons.test.mjs web-tests/narrator.test.mjs web-tests/nearby.test.mjs
```

The Node tests port the app's tests (move-generation counts, bot behaviour, and that every lesson is solvable), so the two versions are held to the same standard. `nearby.test.mjs` runs the web Play Nearby session over in-memory links (a port of the app's session tests), checks the invite codes, and plays whole chess and checkers games through the real controllers on both sides. The WebRTC connection itself needs a real browser and isn't covered there.

## Project layout

```
app/src/main/java/com/example/chess/
  engine/         Chess rules, notation, evaluation, alpha-beta AI, coach helpers
  checkers/       Checkers rules, AI, coach helpers, checkers lessons
  lessons/        Chess lesson model, goals, solver and catalog
  data/           Saved progress and settings, game selection
  audio/          Synthesized sound effects and the text-to-speech narrator
  net/            Play Nearby: Wi-Fi beacon, Bluetooth, picture code, the two-tablet session
  ui/board/       Chess and checkers boards, shared drawing, piece and checker artwork
  ui/screens/     Home, lesson map, lessons, practice setup, matches, settings
  ui/components/  Buttons, the coach bubble, stars, confetti and dialogs
app/src/test/     Unit tests for both engines, the bots and every lesson
```
