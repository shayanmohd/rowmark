# Rowmark design notes

## Design read

Reading this as: a hands-busy counting tool for adult knitters and crocheters, with a yarn-shop
counter language (barrel row counters, ball bands, heathered wool), leaning toward Gabarito display
plus Nunito Sans text on a heather and yarn fuchsia palette.

## Dials

- Variance 4: one asymmetric moment (the big Row drum with the small Repeat and Section drums beside
  it) on an otherwise calm, even grid. Lists use spacing and hairline dividers, never cards in a row.
- Motion 3: only the drum digits and the pattern ruler move. Everything else snaps. Every animation
  reads LocalReducedMotion and collapses to snap() when it is true.
- Density 3: one number at arm's length. The counter screen is readable from the sofa; secondary
  numbers (repeat, section, stitch) stay small and quiet.

## The one memorable thing

The barrel drum. Each digit sits in a fixed cell, the changed digit rolls up in Fuchsia and settles to
Ink; Repeat and Section are smaller drums beside it. Everything around it is quiet Heather and Skein.

## Tokens (from blueprint section 7)

| Token | Light | Dark | Role |
|---|---|---|---|
| Heather | #F3EEF2 | #1A1519 | background, window |
| Skein | #FBF8FA | #251E24 | surface: rows, sheets, fields, count pad |
| Ink | #241A25 | #EEE6EC | text, drum digits, icons |
| Twist | #625565 | #B3A5B2 | secondary text, outlines; dividers at 24 percent alpha |
| Fuchsia | #A32689 | #E486CF | the one accent |
| Frog | #A3401F | #F09A7C | errors and destructive actions only |

Text on Fuchsia uses Skein. No pure black or white. Highlighter ink is user content: #F2D23C at 40 percent.

## Type

Gabarito (variable, 400 to 900) for the drum digits (800), titles (700) and buttons (700).
Nunito Sans (variable) for body 16sp 400 and labels 14sp 600. Both SIL OFL 1.1, licences in docs/.

## Radius

RadiusSm 8dp chips; RadiusMd 14dp buttons and fields; RadiusLg 28dp count pad and sheet tops.
Pattern pages stay square.

## Copy voice

Sentence case, plain verbs, the button's verb repeats in its toast ("Save project" then "Project saved").
No filler, no all-caps labels, no arrows, no dashes of any kind.
