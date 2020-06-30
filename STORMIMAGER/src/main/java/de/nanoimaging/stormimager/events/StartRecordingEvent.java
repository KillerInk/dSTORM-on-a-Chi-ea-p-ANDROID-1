package de.nanoimaging.stormimager.events;

public class StartRecordingEvent {
    public final int video_id;

    public StartRecordingEvent(int video_id)
    {
        this.video_id = video_id;
    }
}
