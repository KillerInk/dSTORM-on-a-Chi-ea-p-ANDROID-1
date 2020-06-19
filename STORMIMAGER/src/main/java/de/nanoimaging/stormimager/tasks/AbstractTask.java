package de.nanoimaging.stormimager.tasks;

import de.nanoimaging.stormimager.acquisition.GuiMessageEvent;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.microscope.MicroScopeInterface;
import de.nanoimaging.stormimager.utils.SharedValues;

public abstract class AbstractTask<T extends GuiMessageEvent> implements TaskInterface {

    protected T messageEvent;
    protected CameraInterface cameraInterface;
    protected SharedValues sharedValues;
    protected boolean isworking;
    protected MicroScopeInterface microScopeInterface;

    public AbstractTask(CameraInterface cameraInterface, T messageEvent, SharedValues sharedValues,MicroScopeInterface microScopeInterface)
    {
        this.cameraInterface = cameraInterface;
        this.messageEvent = messageEvent;
        this.sharedValues = sharedValues;
        this.microScopeInterface = microScopeInterface;
    }

    @Override
    public boolean isWorking() {
        return this.isworking;
    }

    @Override
    public void stop() {
        this.isworking = false;
    }

    @Override
    public void process() {
        if(!preProcess())
            return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                AbstractTask.this.run();
            }
        }).start();
    }

    public abstract boolean preProcess();
    public abstract void run();
}
