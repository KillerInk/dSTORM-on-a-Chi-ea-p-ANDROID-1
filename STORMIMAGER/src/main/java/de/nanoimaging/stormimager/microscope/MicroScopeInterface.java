package de.nanoimaging.stormimager.microscope;

public interface MicroScopeInterface {
    void setZFocus(int stepsize);
    void setLaser(int laserintensity, boolean findcoupling);
    void setLensX(int lensposition, boolean findcoupling);
    void setLensZ(int lensposition, boolean findcoupling);
    void setSOFIX(boolean misSOFI_X, int mvalSOFIX);
    void setSOFIZ(boolean misSOFI_Z, int mvalSOFIZ);
    void setValSOFIX(int mval_sofi_amplitude_x);
    void setValSOFIZ(int mval_sofi_amplitude_z);
    void setValDurationMeas(int mval_duration_measurement);
    void setValPeriodMeas(int mval_period_measurement);
    void setNValPeriodCalibration(int mval_period_calibration);
    void setState(String state);
    void setIpAdress(String ipAdress);
    String getIpAdress();
    void Reconnect();
}
