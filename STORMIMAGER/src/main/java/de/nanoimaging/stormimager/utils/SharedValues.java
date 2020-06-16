package de.nanoimaging.stormimager.utils;

public class SharedValues {

    private int val_lens_x_global = 0;

    public synchronized void setVal_lens_x_global(int val_lens_x_global)
    {
        this.val_lens_x_global = val_lens_x_global;
    }
    public synchronized int getVal_lens_x_global()
    {
        return this.val_lens_x_global;
    }
}
