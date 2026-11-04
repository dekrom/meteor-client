/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.gui.utils;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;

// Kept for addons built against upstream's 1.21.4 build
public interface IScreenFactory {
    WidgetScreen createScreen(GuiTheme theme);
}
