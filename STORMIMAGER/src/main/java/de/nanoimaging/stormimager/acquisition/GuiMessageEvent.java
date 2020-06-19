package de.nanoimaging.stormimager.acquisition;

import android.graphics.Bitmap;

public interface GuiMessageEvent {
    void onGuiMessage(String msg);
    void onShowToast(String msg);
    void onUpdatePreviewImg(Bitmap bitmap);
}
