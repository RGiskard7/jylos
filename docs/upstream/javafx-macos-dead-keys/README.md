# JavaFX on macOS: a repeated dead key loses every other keystroke

| | |
|---|---|
| Status | Documented here only; not filed with JavaFX. If it ever is, put its JDK id here and in `docs/WEBVIEW.md`. |
| Affects | Every JavaFX text input on macOS: the editor and the reading view (`WebView`) and every `TextField`/`TextArea`. |
| Compensated in Jylos | Everywhere: `DeadKeyRepeats`, installed by `Main` on every window, those open at start and those opened later (dialogs). It covers web pages and JavaFX `TextField`/`TextArea` alike. |
| Seen with | JavaFX 23.0.2 (the version Jylos uses) and 24.0.2, JDK 22.0.2, macOS 27.0.1, layout Spanish - ISO. |

## Files

- [REPORT.md](REPORT.md): the bug written up as a JavaFX bug report (environment, steps, expected
  and actual results, the recorded events), so that it can be filed as it is if ever needed.
- [DeadKeyRepro.java](DeadKeyRepro.java): the stand-alone test case it refers to (no Jylos code).

## What happens

On macOS a dead key (`` ` `` `´` `^` `¨` `~` on a Spanish keyboard; `'` and `"` on U.S.
International) is typed as an input method composition: the mark stays pending until the next
key. Pressed again, the pending mark should be committed and a new one left pending. JavaFX
does that for the 2nd, 4th… press, but delivers the 3rd, 5th… as a new composition with nothing
committed, so the control replaces the pending mark and that press is lost: three `` ` `` for a
code fence cannot be typed. The event sequence, recorded from a real keyboard, is in
[REPORT.md](REPORT.md).

Other engines do not have the problem: Chromium-based applications (Electron) receive the
commit from macOS directly, and native Cocoa text views type one mark per press.

## The workaround

`jylos/src/main/java/com/example/jylos/ui/DeadKeyRepeats.java`: an event filter on every
window, installed by `Main.start` (`DeadKeyRepeats.installEverywhere()`). When an `INPUT_METHOD_TEXT_CHANGED` event composes the same single spacing accent that
is already pending and commits nothing, it first fires an event committing the pending mark, then
the original composition. "Spacing accent" is a Unicode modifier symbol, a spacing modifier
letter (U+02B0 to U+02FF), `~`, `'` or `"`. Any other composition, such as Japanese or Chinese
input, passes through untouched.

Tests replay the recorded sequences: `DeadKeyRepeatsTest` into a `TextField` and a `TextArea` in a
window opened after the install (as a dialog is), and `MarkdownEditorViewTest` into the real editor:
`aDeadKeyPressedAgainAndAgainTypesOneMarkPerPress`, `theCircumflexDeadKeyRepeatsToo`,
`theApostropheAndQuoteDeadKeysRepeatToo`, and `aCompositionThatChangesIsStillOneComposition` for
an input method that must not be touched.

## When to check it again

On every JavaFX upgrade, run `DeadKeyRepro.java` with that version and press `` ` `` five times
in each control (instructions in the file):

- **Five marks in the `TextField` and in the text area:** JavaFX is fixed. Remove
  `DeadKeyRepeats` (and its install in `Main.start`), keep the tests, which must still
  pass, and update this page and `docs/WEBVIEW.md`.
- **Still three:** keep it.
- **Anything else** (a different event sequence): the recorded sequence in the tests no longer
  matches reality; record the new one from a real keyboard before changing anything.

A simulated keyboard (`java.awt.Robot`, the JavaFX `Robot`) does not compose dead keys reliably
and cannot be used to check this.
