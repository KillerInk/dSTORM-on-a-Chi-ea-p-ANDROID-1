package de.nanoimaging.stormimager.events;

public class SofiMeasurementUpdateUiEvent {
    public final boolean preExecute;
    public final boolean postExecute;
    public final boolean update;
    public final int i_meas;

    public SofiMeasurementUpdateUiEvent(boolean preExecute,boolean postExecute, boolean update, int i_meas)
    {
        this.preExecute =preExecute;
        this.postExecute = postExecute;
        this.update = update;
        this.i_meas = i_meas;
    }
}
