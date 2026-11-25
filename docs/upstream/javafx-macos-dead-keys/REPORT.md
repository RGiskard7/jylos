# [macOS] Repeated dead key loses every other keystroke: a new composition replaces the pending one without committing it

A record of the bug, written as a JavaFX bug report: it has not been filed. Should it ever need
to be, it can go as it is to <https://bugreport.java.com> (component: JavaFX, sub-component:
window-toolkit) or to the openjfx-dev list. Everything below was observed on the machine
described; nothing is inferred from source code.

## Environment

| | |
|---|---|
| OS | macOS 27.0.1 (build 26A434), Apple Silicon (arm64) |
| Keyboard layout | Spanish - ISO (`` ` `` is a dead key: the key right of P) |
| JDK | OpenJDK 22.0.2+9-70 |
| JavaFX | 24.0.2 (reproduced with the attached test case); 23.0.2 (same event sequence, seen in an application). 25 not tested: no JDK 23+ at hand. |

## Summary

On macOS, dead keys reach JavaFX as input method compositions. When the same dead key is
pressed several times in a row, every other press after the second arrives as a new
composition with nothing committed, so the pending character is replaced instead of being
committed. Five presses of `` ` `` type three characters in a `TextField` and in a `WebView`
`<textarea>`. Native Cocoa text views (TextEdit, Notes) type one character per press.

This makes Markdown code fences (```` ``` ````) impossible to type on Spanish, French, German,
Portuguese and other layouts where `` ` ``, `´`, `^`, `¨` or `~` are dead keys, in any JavaFX
text input, and in editors hosted in `WebView`.

## Steps to reproduce

1. Select the "Spanish - ISO" keyboard layout in macOS.
2. Run the attached `DeadKeyRepro.java` (a `WebView` with a `<textarea>`, and a `TextField`;
   every `InputMethodEvent` and both values are printed).
3. Click in the text area and press the key right of P five times, then Return.
4. Click in the text field and press the same key five times.

## Expected

Five `` ` `` in each control, as in native macOS applications.

## Actual

`WebView` `<textarea>`: three `` ` `` (then the line break). `TextField`: three `` ` ``, the last
still pending; when focus left the field the pending composition was cancelled and two remained.

The `InputMethodEvent`s are the same for both controls (output of the test case, JavaFX 24.0.2):

```
press 1   composed="`"  committed=""
press 2   composed=""   committed="`"
          composed="`"  committed=""
press 3   composed="`"  committed=""        <- new pending mark, the previous one never committed
press 4   composed=""   committed="`"
          composed="`"  committed=""
press 5   composed="`"  committed=""        <- same again
Return    composed=""   committed="`"
          composed=""   committed="\r"
```

Presses 2 and 4 commit the pending mark and start a new composition; presses 3 and 5 only
start a new composition, so the mark pending from presses 2 and 4 is replaced and lost. A
control that handles `INPUT_METHOD_TEXT_CHANGED` as specified (the composed text replaces the
previous composed text) cannot tell that a character was meant to be typed.

The `WebView` page receives `compositionupdate` for presses 3 and 5 with no
`compositionend`/`insertFromComposition` for the previous mark, consistent with the events above.
No `KEY_PRESSED` is delivered for the dead key itself, only `KEY_RELEASED`.

## Workaround in use

Applications can compensate in an event filter: when an `INPUT_METHOD_TEXT_CHANGED` event
composes the same single spacing accent that is already pending (Unicode modifier symbol, or
spacing modifier letter, `~`, `'` or `"`) and commits nothing, fire an event that commits the
pending mark first, then the original composition. Compositions of other text (CJK input
methods) are left untouched.

## Related

- JDK-8340982: dead key followed by Space produces two characters (Windows), from the
  openjfx-dev thread "Dead keys followed by space" (September 2024), where it is noted that on
  macOS and Linux dead keys go through the input method path.
- JDK-8367045: "[Linux] Dead keys not working" (2025), which delivers Linux text input as
  committed text in `InputMethodEvent`s.
