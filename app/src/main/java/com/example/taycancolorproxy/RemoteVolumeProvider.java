package com.example.taycancolorproxy;

import android.media.VolumeProvider;

public class RemoteVolumeProvider extends VolumeProvider {
    public RemoteVolumeProvider() {
        super(VOLUME_CONTROL_ABSOLUTE, 100, 50);
    }
}
