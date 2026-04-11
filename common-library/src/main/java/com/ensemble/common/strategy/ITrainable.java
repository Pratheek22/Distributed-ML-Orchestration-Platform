package com.ensemble.common.strategy;

import com.ensemble.common.model.DataFrame;

import java.util.List;
import java.util.Map;

public interface ITrainable {

    List<Double> train(DataFrame partition, Map<String, Object> hyperparams);
}
