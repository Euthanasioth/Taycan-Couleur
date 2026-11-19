package com.example.taycancolorproxy;

import android.media.VolumeProvider;

public class RemoteVolumeProvider extends VolumeProvider {
    public RemoteVolumeProvider() {
        super(VOLUME_CONTROL_ABSOLUTE, 100, 50);
    }

    @Override
    public void onSetVolume(int volume) {
        // Fictif : ne rien faire
    }

    @Override
    public void onAdjustVolume(int direction) {
        // Fictif : ne rien faire
    }
}
