package com.ensemble.common.strategy;

import java.util.List;

public interface EnsembleStrategy {

    List<Double> aggregate(List<List<Double>> predictions);
}
