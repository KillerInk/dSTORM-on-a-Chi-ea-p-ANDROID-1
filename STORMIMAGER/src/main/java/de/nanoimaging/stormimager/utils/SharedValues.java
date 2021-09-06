package de.nanoimaging.stormimager.utils;

public class SharedValues {

    public int val_z_step = 10;
    public int val_z_speed = 1000;
    private int val_lens_x_global = 0;
    // Acquisition parameters
    public int val_period_measurement = 6 * 10;                // time between measurements in seconds
    public int val_duration_measurement = 5;                   // duration for one measurement in seconds
    public int val_nperiods_calibration = 10 * 10;             // number of measurements for next recalibraiont
    public int val_laser_red_global = 0;                       // global value for the laser

    public int val_sofi_amplitude_z = 20; // amplitude of the lens in each periode
    public int val_sofi_amplitude_x = 20; // amplitude of the lens in each periode
    public boolean is_SOFI_x = false;
    public boolean is_SOFI_z = false;


    public synchronized void setVal_lens_x_global(int val_lens_x_global)
    {
        this.val_lens_x_global = val_lens_x_global;
    }
    public synchronized int getVal_lens_x_global()
    {
        return this.val_lens_x_global;
    }

}
