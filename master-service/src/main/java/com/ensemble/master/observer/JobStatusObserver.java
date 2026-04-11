package com.ensemble.master.observer;

import com.ensemble.common.model.TrainingJob;

public interface JobStatusObserver {

    void onStatusChange(TrainingJob job, String previousStatus);
}
