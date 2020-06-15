package de.nanoimaging.stormimager.acquisition;

import de.nanoimaging.stormimager.acquisition.GuiMessageEvent;

public interface UpdateUiEvent extends GuiMessageEvent {
    void onUpdatedUI();
}
