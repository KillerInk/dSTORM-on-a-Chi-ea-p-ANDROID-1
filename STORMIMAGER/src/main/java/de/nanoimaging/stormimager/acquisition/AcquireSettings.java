package de.nanoimaging.stormimager.acquisition;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import de.nanoimaging.stormimager.R;

public class AcquireSettings extends DialogFragment {

    public static final String TAG = "Settings Dialog";

    public interface NoticeDialogListener {
        void onDialogPositiveClick(DialogFragment dialog);
        void onDialogNegativeClick(DialogFragment dialog);
    }

    // Use this instance of the interface to deliver action events
    AcquireSettings.NoticeDialogListener mListener;

    private TextView acquireSettingsValPeriodMeas;
    private TextView acquireSettingsIPaddress;
    private TextView acquireSettingsValSOFIX;
    private TextView acquireSettingsValSOFIZ;
    private TextView acquireSettingsValDurationMeas;
    private TextView acquireSettingsValNPeriodsCalibration;

    private Button acquireSettingsButtonIPGO;

    private ToggleButton acquireSettingsSOFIXToggle;
    private ToggleButton acquireSettingsSOFIZToggle;

    

    // Override the Fragment.onAttach() method to instantiate the NoticeDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (AcquireSettings.NoticeDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement NoticeDialogListener");
        }
    }

    public interface OnCompleteListener {
        void onComplete(String time);
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View content = inflater.inflate(R.layout.activity_acquire_settings, null);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(content);
        // Add action buttons
        builder.setPositiveButton("Set", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {

                AcquireActivity callingActivity = (AcquireActivity) getActivity();

                String mIPaddress = acquireSettingsIPaddress.getText().toString();
                Log.d(TAG,String.format("mIPaddress: %s", mIPaddress));
                callingActivity.microScopeInterface.setIpAdress(mIPaddress);

                int mValDurationMeas = Integer.parseInt(acquireSettingsValDurationMeas.getText().toString());
                Log.d(TAG,String.format("mValDurationMeas: %s", mValDurationMeas));
                callingActivity.microScopeInterface.setValDurationMeas(mValDurationMeas);

                int mValPeriodMeas = Integer.parseInt(acquireSettingsValPeriodMeas.getText().toString());
                Log.d(TAG,String.format("mValPeriodMeas: %s", mValPeriodMeas));
                callingActivity.microScopeInterface.setValPeriodMeas(mValPeriodMeas);

                int mNValPeriodCalibration = Integer.parseInt(acquireSettingsValNPeriodsCalibration.getText().toString());
                Log.d(TAG,String.format("mNValPeriodCalibration: %s", mNValPeriodCalibration));
                callingActivity.microScopeInterface.setNValPeriodCalibration(mNValPeriodCalibration);

                int mValSOFIX = Integer.parseInt(acquireSettingsValSOFIX.getText().toString());
                Log.d(TAG,String.format("mValSOFIX: %s", mValSOFIX));
                callingActivity.microScopeInterface.setValSOFIX(mValSOFIX);

                int mValSOFIZ = Integer.parseInt(acquireSettingsValSOFIZ.getText().toString());
                Log.d(TAG,String.format("mValSOFIX: %s", mValSOFIZ));
                callingActivity.microScopeInterface.setValSOFIZ(mValSOFIZ);

                callingActivity.microScopeInterface.setSOFIX(acquireSettingsSOFIXToggle.isChecked(), mValSOFIX);
                callingActivity.microScopeInterface.setSOFIZ(acquireSettingsSOFIZToggle.isChecked(), mValSOFIZ);

                callingActivity.microScopeInterface.setIpAdress(acquireSettingsIPaddress.getText().toString());
                callingActivity.microScopeInterface.Reconnect();

            }
        })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });

        AcquireActivity callingActivity = (AcquireActivity) getActivity();

        // ASsign the GUI components 
        acquireSettingsIPaddress = (TextView) content.findViewById(R.id.editText_ip_address);
        acquireSettingsIPaddress.setText(callingActivity.microScopeInterface.getIpAdress());

        acquireSettingsValSOFIX = (TextView) content.findViewById(R.id.editText_SOFI_x);
        acquireSettingsValSOFIX.setInputType(InputType.TYPE_CLASS_NUMBER);
        acquireSettingsValSOFIX.setText(String.valueOf(callingActivity.sharedValues.val_sofi_amplitude_x));

        acquireSettingsValSOFIZ = (TextView) content.findViewById(R.id.editText_SOFI_z);
        acquireSettingsValSOFIZ.setInputType(InputType.TYPE_CLASS_NUMBER);
        acquireSettingsValSOFIZ.setText(String.valueOf(callingActivity.sharedValues.val_sofi_amplitude_z));

        acquireSettingsValPeriodMeas = (TextView) content.findViewById(R.id.editText_period_measure);
        acquireSettingsValPeriodMeas.setInputType(InputType.TYPE_CLASS_NUMBER);
        acquireSettingsValPeriodMeas.setText(String.valueOf(callingActivity.sharedValues.val_period_measurement));

        acquireSettingsValDurationMeas = (TextView) content.findViewById(R.id.editText_duraction_measure);
        acquireSettingsValDurationMeas.setInputType(InputType.TYPE_CLASS_NUMBER);
        acquireSettingsValDurationMeas.setText(String.valueOf(callingActivity.sharedValues.val_duration_measurement));

        acquireSettingsValNPeriodsCalibration = (TextView) content.findViewById(R.id.editText_nperiod_realign);
        acquireSettingsValNPeriodsCalibration.setInputType(InputType.TYPE_CLASS_NUMBER);
        acquireSettingsValNPeriodsCalibration.setText(String.valueOf(callingActivity.sharedValues.val_nperiods_calibration));


        acquireSettingsButtonIPGO = (Button) content.findViewById(R.id.button_ip_address_go);
        acquireSettingsButtonIPGO.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                AcquireActivity callingActivity = (AcquireActivity) getActivity();
                callingActivity.microScopeInterface.setIpAdress(acquireSettingsIPaddress.getText().toString());
                callingActivity.microScopeInterface.Reconnect();
                Log.i(TAG, "IP-Address: "+acquireSettingsIPaddress.getText().toString());
            }
        });

        // toggle buttons
        acquireSettingsSOFIXToggle = content.findViewById(R.id.button_SOFI_x);
        acquireSettingsSOFIXToggle.setText("SOFI (x): 0");
        acquireSettingsSOFIXToggle.setTextOn("SOFI (x): 1");
        acquireSettingsSOFIXToggle.setTextOff("SOFI (x): 0");

        //******************* SOFI-Mode  ********************************************//
        // This is to let the lens vibrate by a certain amount
        acquireSettingsSOFIXToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "Checked");
                    int myamplitude_x = Integer.parseInt(acquireSettingsValSOFIX.getText().toString());
                    Log.i(TAG, "Set the amplitude to: " + myamplitude_x);
                    callingActivity.microScopeInterface.setSOFIX(true, myamplitude_x);
                } else {
                    callingActivity.microScopeInterface.setSOFIX(false, 0);
                    }
            }

        });

        // toggle buttons
        acquireSettingsSOFIZToggle = content.findViewById(R.id.button_SOFI_z);
        acquireSettingsSOFIZToggle.setText("SOFI (z): 0");
        acquireSettingsSOFIZToggle.setTextOn("SOFI (z): 1");
        acquireSettingsSOFIZToggle.setTextOff("SOFI (z): 0");

        //******************* SOFI-Mode  ********************************************//
        // This is to let the lens vibrate by a certain amount
        acquireSettingsSOFIZToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    int myamplitude_z = Integer.parseInt(acquireSettingsValSOFIZ.getText().toString());
                    Log.i(TAG, "Set the amplitude to: " + myamplitude_z);
                    callingActivity.microScopeInterface.setSOFIZ(true, myamplitude_z);
                } else {
                    callingActivity.microScopeInterface.setSOFIZ(false, 0);
                }
            }

        });



        return builder.create();
    }


}


