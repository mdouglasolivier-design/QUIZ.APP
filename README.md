# ☕ Java Quiz App

A quiz game in pure Java with **two ways to play**: a graphical app with clickable
answers and a live timer bar (no input/timer collisions, ever), and a classic console
mode for the terminal. No external libraries — just the JDK.

- **Graphical (default):** click 1–4 buttons, watch a color-changing timer bar turn
  orange then red as time runs low. When the buzzer hits, answers lock automatically.
- **Certificates you can see right away:** when the quiz ends, your personalized
  certificate is generated on the spot — click **View / Download my Certificate** on
  the results screen to open it, and find it saved in the `certificates/` folder.
  Emailing it is **optional** (the email address is optional too).
- **Certificates by email:** enter your email and (if the instructor configured the
  mail server) the certificate is also sent to the student's inbox.
- **Instructor panel (password: `teach123`):** change the timer, edit exam questions,
  view every student's marks in a table (or open them in Excel), and configure
  **email delivery** of certificates.
- **Console:** type 1–4, `q` to quit. The time limit is shown with the prompt (nothing
  types over your input), and your answer time is shown after each question.
- Questions load from `questions.csv`, shuffled every run. Edit the CSV to make it yours.

---

## ✅ Requirements (one-time)

A **JDK** (Java Development Kit), version 8+. Check with `javac -version`.

- **Windows:** install from <https://adoptium.net> (Temurin JDK), then reopen terminals.
- **macOS:** `brew install --cask temurin`
- **Linux:** `sudo apt install default-jdk`

No JDK yet? The launchers will tell you and point you to the download page.

---

## ▶ Run it

### Windows

| Want | Do this |
|---|---|
| Graphical quiz | **Double-click `run.bat`** |
| Console quiz | Double-click `run-console.bat` |
| Build shareable jar | Double-click `build-jar.bat` → then `java -jar QuizApp.jar` |

### macOS / Linux

```bash
./run.sh            # graphical quiz
./run-console.sh    # console quiz
./build-jar.sh      # build QuizApp.jar
```
(First time only: `chmod +x *.sh` to allow running the scripts.)

### Manual (any OS)

```bash
javac QuizApp.java QuizGui.java
java QuizGui questions.csv          # graphical
java QuizApp questions.csv          # console
java QuizApp questions.csv 10       # console, 10 seconds per question
```

### Custom time limit

```bash
java QuizGui questions.csv 10       # graphical, 10 seconds per question
java QuizApp questions.csv 30       # console, 30 seconds per question
```

To change the default permanently, edit `secondsPerQuestion` (GUI) or
`SECONDS_PER_QUESTION` (console) at the top of the file and recompile.

---

## 🎓 Students: name, quiz, certificate by email

1. **Start screen:** type your **name**. The email address is **optional** — only
   needed if you want the certificate emailed to you.
2. Questions appear one by one in random order. Click an answer (or the timer decides).
3. The **timer bar** drains from blue → orange (half time) → red (last 5 seconds, the
   "Xs" counter turns red too).
4. After you click (or time out), the correct answer flashes **green** (your wrong pick
   flashes **red**) for a moment, then the next question appears.
5. The **results screen** shows your score, timeouts, total time, and feedback.
   Click **View / Download my Certificate** to see your certificate immediately —
   it is saved in the `certificates/` folder. Optionally click **Email my
   Certificate** to also send it to the address you typed. **Play Again** starts a
   fresh shuffled round.

   > Emailing needs a one-time mail-server setup by the instructor; without it, the
   > View / Download button still works fine.

   > First time only, the mail server must be configured. The app warns about it on
   > startup and walks the instructor through setup; the Email Log tab tracks every
   > delivery, and failed ones can be resent from the Marks or Email Log tab.

## 👩‍🏫 Instructor: timer, exam, marks

Click **Instructor** on the start screen and enter the password **`teach123`**
(to change it, edit `INSTRUCTOR_PASSWORD` in `QuizStore.java`).

| Tab | What you can do |
|---|---|
| **Questions** | Edit the whole exam — add, change or delete questions — then Save. New questions appear immediately for the next student. |
| **Timer** | Set seconds per question (5–600) and save. Applies to both GUI and console. |
| **Marks** | Every attempt is listed: student, **email**, score, %, grade, time taken, date. Select a student and click **Resend Certificate** to re-email theirs. **Open in Excel** opens `results.csv`; **Clear All Marks** wipes it. |
| **Email** | Set the mail server used to deliver certificates: SMTP host, port, SSL on/off, username, password, from address. **Send Test Email** verifies it works. Example for Gmail: `smtp.gmail.com`, port `465`, SSL on, your Gmail address as username, an **App Password** as password. |
| **Email Log** | Every delivery attempt, newest first: status (**SENT** in green / **FAILED** in red), recipient, student, score, date/time, and the failure reason if any. **Resend to Selected** re-sends a failed one; **Open Log in Excel** opens `email_log.csv`. |

## ⌨️ How to play (console)

1. Enter your **name**, then optionally your **email address** (only if you want the
   certificate emailed — press Enter to skip it).
2. Type `1`–`4` and press Enter. The prompt shows the limit, e.g. `Your answer (1-4) [20s]:`
3. Nothing prints while you type — no ticking text will ever garble your input.
4. If time runs out: `TIME'S UP!` and the correct answer is revealed.
5. Type `q` to quit early; you'll still get your partial score and your certificate.
6. At the end, the **certificate file path** is printed — open it with any image
   viewer. It is also emailed automatically if you gave an email address and the
   mail server is configured.

---

## ✏️ Add your own questions

Open `questions.csv` in any text editor. Each line:

```
question,option1,option2,option3,option4,correctOptionNumber
```

- `correctOptionNumber` is **1–4**.
- Wrap any field containing a comma in double quotes:
  `"Which class is the root of everything, really?",Object,String,Class,System,1`
- Blank lines are skipped; bad lines are reported but never crash the game.
- Save as **UTF-8**.

---

## 🗂 Files the app creates while you use it

| File | What it is |
|---|---|
| `settings.txt` | The instructor's seconds-per-question setting |
| `results.csv` | Every attempt: name, email, score, %, grade, time taken, date, mode |
| `email_log.csv` | Every certificate delivery attempt: status, recipient, student, score, date, time, detail |
| `certificates/` | Downloaded certificates as PNG images |
| `email.properties` | Instructor's mail server settings (created when email is configured) |

All three are safe to delete — the app recreates them as needed.

---

## 📦 Put it on your PC — step by step

This project is already a folder on your computer. To install it properly:

1. **Get Java:** install the JDK from <https://adoptium.net> (Next → Next → Finish;
   default settings are fine).
2. **Copy the project:** copy this whole folder (all files below) somewhere friendly,
   e.g. `C:\Users\you\JavaQuiz` (Windows) or `~/JavaQuiz` (Mac/Linux):

   ```
   JavaQuiz/
   ├── QuizApp.java         console game
   ├── QuizGui.java         graphical game
   ├── QuizStore.java       settings + marks storage
   ├── CertificateMaker.java certificate generator
   ├── EmailSender.java     built-in email (SMTP) sender
   ├── InstructorPanel.java instructor management window
   ├── questions.csv        your questions (edit me!)
   ├── run.bat              ← double-click (Windows, graphical)
   ├── run-console.bat      console mode (Windows)
   ├── run.sh               graphical (Mac/Linux)
   ├── run-console.sh       console mode (Mac/Linux)
   ├── build-jar.bat        build QuizApp.jar (Windows)
   ├── build-jar.sh         build QuizApp.jar (Mac/Linux)
   └── README.md            this file
   ```
3. **Play:** double-click `run.bat` (Windows) or run `./run.sh` (Mac/Linux).
4. **Optional — make it a single file:** run `build-jar.bat` / `build-jar.sh` once.
   You get `QuizApp.jar` — a single file you can copy anywhere and start with
   `java -jar QuizApp.jar` (or often just by double-clicking it).
5. **Optional — add a desktop shortcut:** right-click `run.bat` → *Show more options* →
   *Send to* → *Desktop (create shortcut)*. On Mac/Linux, make an alias to `run.sh`.
6. **Share with friends:** send them the folder (or just `QuizApp.jar`). They need Java
   too — the same adoptium.net link works.

> Tip: you don't need the `.idea/` or `out/` folders to run the game — they're IDE
> files and can be skipped when copying.

---

## 🧠 Design notes (for the curious)

- The GUI uses Swing, laid out on the Event Dispatch Thread; the timer is a
  `javax.swing.Timer` that ticks 10×/second, drives the progress bar, and locks the
  buttons exactly at the deadline — clicks are guarded, so a click can never race the
  buzzer.
- The console version reads input on a background thread and stamps each line with the
  time Enter was pressed. Answers pressed before the buzzer always count; anything
  pressed after one is discarded so it can't leak into the next question. It prints
  nothing while you type — the collision you saw is designed out.
