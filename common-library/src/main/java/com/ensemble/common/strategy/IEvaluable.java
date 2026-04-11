package com.ensemble.common.strategy;

import java.util.List;

public interface IEvaluable {

    double evaluate(List<Double> predictions, List<Double> labels);
}
