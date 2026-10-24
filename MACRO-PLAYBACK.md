# Macro playback

Settings includes a **Lenient Macro Playback** Yes/No control. It is off by default and
is saved in the existing `neo.properties` preferences. With it off, Forge's
selection matching, retry limits and failure handling are unchanged. Recordings
retain their existing action format; no migration or per-step settings are needed.

With it on, card clicks at priority, card targets and card selections from an
engine list first look for the original card among the current legal choices.
Otherwise, playback requires exactly one candidate with the recorded name,
Oracle identity, owner, controller and zone. Characteristics are captured while
recording, before the live card view can change. This allows a land recorded in
hand to be selected after it returns to hand as a new object. Two equivalent
copies are treated as ambiguous even if they appear identical; their position
in the list never breaks a tie.

An ambiguous, missing or rejected match pauses the playback call and opens the
normal card choice dialog. Choose a legal card to complete that recorded step
and resume. The input and legality are checked again before applying the answer.
Cancel the dialog, or use the ongoing playback Cancel action, to stop the macro.
An empty choice list can only be cancelled. A stale or rejected manual answer
also stops playback; it never advances to an arbitrary card.

## Limits

- Tokens and face-down cards support original-object matching only. Names alone
  do not establish their characteristics. Replacements require manual selection.
- Lenient replacement applies to cards, including sources of recorded activated
  abilities. Abilities still require the recorded description and must be
  playable. Other ability choices retain the existing ability chooser.
- Player selections, combat declarations, stack-item choices, generic text/mode
  dialogs, colors, scry, trigger ordering and mana-payment automation retain
  their existing Forge/Neo resolution. No new equivalence matching is added for
  those selection types. In particular, this option does not change Forge's
  internal mana-source search or its mana-payment heuristics.
- Local Neo controllers install the extension before recording. Existing
  recorders are preserved if one was already initialized; network guests use
  the host's macro system. If the engine no longer permits installation of the
  extension, Neo logs its unavailability and keeps the existing recorder.

The focused executable regression check is
`forge.neo.match.NeoMacroSystemCheck` in `forge-gui-neo/src/test/java`.
