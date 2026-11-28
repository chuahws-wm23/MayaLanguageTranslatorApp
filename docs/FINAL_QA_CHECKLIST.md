# Maya Language FYP — Final bug-fixing and acceptance checklist

**Purpose:** Check the actual Android app on a connected phone before the FYP
demo. Green Gradle unit tests establish build correctness, **not** correct
Firebase behavior, camera behavior, model accuracy, or user experience.

**Build under test:** record commit SHA shown by `git rev-parse --short HEAD`.
**Android device and version:** __________________
**Test date:** __________________
**Firebase enabled (real backend vs development mode):** __________________

Priority rules: **P0** app cannot start, loses data, crashes, or wrong-account
data exposure; **P1** feature cannot be completed; **P2** unclear text or visual
polish. Reproduce and fix P0/P1 before the demo. Avoid UI redesigns or new
model training until these pass.

| Case | What to do | Expected result | Result |
|---|---|---|---|
| App start | Clean launch, return from background | No crash or frozen controls | [ ] |
| Login/register | Log in, log out, register a test account | Correct profile; authentication works | [ ] |
| Firebase | Update profile, exit, relaunch in Firebase mode | Data remains and stays within the correct account | [ ] |
| Sidebar | Navigate Home, Photo, Text, Voice, Dictionary, History, Saved, Profile | Correct destination and Back controls | [ ] |
| Photo Gallery | Select PNG/JPG with no EXIF, then valid rotated JPEG | Picture displays upright without metadata errors | [ ] |
| Camera | Allow permission, capture one glyph and return | Camera opens; captured preview and crop work | [ ] |
| Permission denied | Deny camera; select from Gallery | Clear explanation; gallery still works | [ ] |
| Cropping | Drag crop around one complete glyph | Rectangle matches the actual image, not margins | [ ] |
| Rotation | Rotate a picture after selecting a crop | New orientation is correct; crop resets appropriately | [ ] |
| Model v1 | Try Deer/Star training reference | Offline result or honest review result; no crash | [ ] |
| Research v2 | Try a v2 supported reference | Suggested TWKM sign only; marked unverified | [ ] |
| Research v3 | Try an unrelated sign | May say 'No reliable supported match'; records aren't marked verified | [ ] |
| Research v4 | Test the held-out 10-sign picture pack | Model runs; guessed reading clearly marked *unverified* | [ ] |
| Unsupported | Blank image, low contrast image, unrelated symbol | Safe error/review; no crash or false claim of verified translation | [ ] |
| Legacy history | Open an old text-photo History record | Reopens as text without falsely reading the image as a glyph | [ ] |
| Saved/history | Add, filter and delete normal text translation items | No crash, duplicate, or cross-account item | [ ] |
| Keyboard | Open Profile and text translation, type, scroll | Keyboard does not obscure buttons or fields | [ ] |
| Long sessions | Repeat Gallery/Camera/recognition 10 times | No progressive lag or memory crash | [ ] |
| Offline | Disable internet and test the glyph scanner | Offline model still runs | [ ] |
| Accessibility | Enlarge system font and try touch targets | Essential labels and buttons remain usable | [ ] |

### How to report a bug

Capture (1) screen name, (2) Android version, (3) expected outcome,
(4) actual outcome, (5) exact reproduction steps, and (6) screenshot and
the first relevant Logcat exception if there is a crash.
**Do not send passwords, Firebase service-account secrets or user data.**

### Final demo discipline

- Do not claim 70% **phone-camera** accuracy for v4. The 7/10 number
  was on held-out TWKM originals from the same catalogue.
- Research v4 always predicts one of ten sign categories; it has no proven
  unknown-sign rejection and must remain labelled as an experimental guess.
- V3's rejection statistics are also catalogue-based, not proof for photos.
- One-sign classification is not whole-inscription translation.
- Present source images, a recognised example, an unfamiliar/failed
  example, and honest limitations.
- After all P0/P1 pass, freeze `fyp-development` except for critical fixes;
  retain a backup or tag of the demo commit.
