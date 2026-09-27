# Paper Dialog Crosshair Reset Fix

## Problem
In Paper's dialog API, clicking a button in a dialog screen snaps the player's crosshair to the exact centre of the screen.

## Root Cause
When a dialog uses `afterAction(DialogBase.DialogAfterAction.CLOSE)` (or the default), the server tells the client to close the dialog screen after executing the button action.

When Minecraft closes an open GUI screen (`Minecraft#setScreen(null)`), the client calls `MouseHandler#grabMouse()`. Grabbing the mouse re-centers the OS cursor in the game window and resets the accumulated camera rotation delta. The crosshair jumps back to the centre.

When a new dialog is shown while another is already open (`player.showDialog(...)`), the client **replaces** the current screen instead of closing it down to the HUD. The mouse is never re-grabbed, so the crosshair position is preserved.

## Solution

### 1. Configure every `DialogBase` with `afterAction(NONE)` and `pause(false)`
Opt out of automatic client-side screen closure:

```java
DialogBase base = DialogBase.builder(title)
        .canCloseWithEscape(true)
        // MUST set pause(false) - Paper rejects afterAction NONE when pause is true
        .pause(false)
        // Keep the screen open so the client does not close to the HUD and re-grab the mouse
        .afterAction(DialogBase.DialogAfterAction.NONE)
        .body(...)
        .inputs(...)
        .build();
```

### 2. Navigate between menus by showing the next dialog directly
When a button opens another menu (e.g., "Back"), the button's command calls `player.showDialog(...)` for the destination menu. The client swaps screens seamlessly without re-grabbing the mouse.

### 3. Handle explicit closure via `Audience#closeDialog()`
Because `afterAction(NONE)` stops the client from auto-closing, the exit/close button ("✕ Close") must run a command that explicitly closes the dialog:

```java
public static LiteralArgumentBuilder<CommandSourceStack> closeCommand() {
    return Commands.literal("menu")
            .then(Commands.literal("close").executes(ctx -> {
                if (ctx.getSource().getSender() instanceof Player player) {
                    player.closeDialog();
                }
                return 1;
            }));
}
```

## Checklist for Auditing Other Projects
1. [ ] Check every `DialogBase` builder call for `.afterAction(...)`. Change `CLOSE` to `NONE`.
2. [ ] Ensure `.pause(false)` is set on every `DialogBase` builder using `NONE` (or Paper throws `IllegalArgumentException: Dialogs that pause the game must use after_action values that unpause it`).
3. [ ] Make sure your menu exit button runs a command that executes `player.closeDialog()`.
4. [ ] For buttons that save a setting, leave the dialog open (`afterAction(NONE)` handles this automatically); the setting takes effect in the background while the player keeps their camera orientation.
