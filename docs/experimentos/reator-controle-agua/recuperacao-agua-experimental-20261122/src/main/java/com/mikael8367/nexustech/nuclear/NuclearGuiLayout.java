package com.mikael8367.nexustech.nuclear;

/** Shared screen coordinates: container hitboxes and painted slots must agree. */
final class NuclearGuiLayout {
 static final int WIDTH=176, HEIGHT=221;
 static final int MACHINE_SLOT_Y=58;
 static final int INVENTORY_TOP=125, PLAYER_SLOT_Y=INVENTORY_TOP+14, HOTBAR_Y=INVENTORY_TOP+72;
 static final int BUTTON_Y=102, BUTTON_WIDTH=78, BUTTON_HEIGHT=20;
 private NuclearGuiLayout() {}
}
