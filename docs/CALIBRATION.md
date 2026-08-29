# Calibration and capture matrix

## Capture requirements

For every screenshot or recording, provide:

- device model and Android version
- raw screenshot resolution and density DPI
- portrait/landscape orientation
- display-size and font-size scaling
- Pokémon GO version and UI language
- whether a screen reader or other accessibility feature was active
- the action immediately before the capture
- any animation/loading delay observed

Capture the full screen without cropping, including system bars. For animated states, provide a recording plus stable-frame screenshots.

## Already supplied

The repository contains 1080 × 2340 portrait fixtures for:

- main map/home
- Friends list with received gifts
- sort menu with Gift selected/descending
- gift-sorted and friendship-level-sorted lists
- received postcard with a sticker and Open
- friend detail with active Send Gift
- gift picker with available gifts
- gift composition without a sticker
- successful gift-sent state
- received reward items

## Still required before complete live calibration

1. Trainer/Profile screen immediately after tapping the avatar, before selecting Friends.
2. Friends sort menu for both modes in both ascending and descending directions.
3. Friends list with no eligible received gifts at the top, middle, and end of the scroll range.
4. Friend detail with no received gift.
5. Friend detail where Send Gift is disabled because:
   - the friend already has an unopened gift
   - the friend otherwise cannot receive one
   - the daily send limit is reached
6. Gift-opening daily-limit dialog.
7. Gift-sending daily-limit dialog.
8. Gift picker with zero gifts.
9. Bag-full dialog reached from opening a gift, including every button state.
10. Item Bag rows, including quantity and trash control, for:
    - Potion, Super Potion, Hyper Potion, Revive
    - Razz Berry and Golden Razz Berry in the same list
    - Nanab Berry
    - Max Potion, Max Revive, Silver Pinap Berry
11. Discard dialog for each allowed item:
    - initial quantity 1
    - adjusted quantity
    - all/maximum quantity selected
    - final confirmation and post-delete list
12. Friendship-level-up message and its dismissal.
13. Postcard pin prompt, already-pinned state, and postcard-book-full state.
14. Sticker chooser with stickers, without stickers, and its close action.
15. Every reward animation/result layout seen in normal use.
16. Loading spinner, slow network, retry dialog, authentication/session error, and maintenance screen.
17. Android notification, permission, keyboard, and system-dialog overlays that may cover Pokémon GO.
18. Pokémon GO minimized, killed, relaunched, and updated-layout states.

Until these exist, the related recognizer branch deliberately pauses. Discard confirmation quantities are deliberately returned as unknown.

## File-backed profile format

Create `files/calibration/<profile-id>/profile.properties`:

```properties
widthPixels=1080
heightPixels=2340
densityDpi=420
language=en
gameVersion=replace-with-version
```

Optional normalized target overrides go in `targets.properties`:

```properties
HOME_MAP.OPEN_PROFILE=0.025,0.830,0.250,0.980
TRAINER_PROFILE.OPEN_FRIENDS=0.350,0.035,0.650,0.130
FRIENDS_LIST.OPEN_SORT=0.740,0.840,0.990,0.980
```

Each value is `left,top,right,bottom`, normalized to the captured frame. Invalid enum keys, malformed rectangles, or out-of-range values are rejected. A matching width/height profile is loaded when the app starts.

For a debug build, Android Studio Device Explorer can place the directory below the app's private `files/calibration` directory without requesting broad storage access in the production app.

## Fixture workflow

Screenshots in the repository root `screenshots/` are automatically exposed as Android-test assets. Add an expected filename/screen pair to `core-image/src/androidTest/kotlin/com/jacob/pokemonscanner/image/GiftScreenshotFixtureTest.kt`, then run `:core-image:connectedDebugAndroidTest`.

Keep trainer-name-bearing fixtures local and do not distribute the test APK.
