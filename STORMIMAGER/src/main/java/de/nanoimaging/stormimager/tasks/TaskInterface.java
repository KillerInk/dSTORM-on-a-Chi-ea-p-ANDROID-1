package de.nanoimaging.stormimager.tasks;

public interface TaskInterface {
    boolean isWorking();
    void process();
    void stop();

}
