package com.ensemble.worker;

import com.ensemble.worker.strategy.AveragingStrategy;
import com.ensemble.worker.strategy.MajorityVotingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnsembleStrategyTest {

    @Test
    void majorityVotingClearMajority() {
        MajorityVotingStrategy strategy = new MajorityVotingStrategy();
        List<List<Double>> predictions = List.of(
                List.of(1.0, 0.0, 1.0),
                List.of(1.0, 1.0, 0.0),
                List.of(1.0, 0.0, 0.0)
        );
        List<Double> result = strategy.aggregate(predictions);
        assertEquals(1.0, result.get(0), "Sample 0: 3 votes for 1.0");
        assertEquals(0.0, result.get(1), "Sample 1: 2 votes for 0.0");
        assertEquals(0.0, result.get(2), "Sample 2: 2 votes for 0.0");
    }

    @Test
    void majorityVotingPropertyOddCardinality() {
        // Property: for all odd-cardinality prediction sets, winner has strictly more than half the votes
        MajorityVotingStrategy strategy = new MajorityVotingStrategy();
        // 3 voters, sample 0: [1,1,0] → 1 wins with 2/3 > 0.5
        List<List<Double>> predictions = List.of(
                List.of(1.0),
                List.of(1.0),
                List.of(0.0)
        );
        List<Double> result = strategy.aggregate(predictions);
        assertEquals(1.0, result.get(0));
        // Verify winner count > half
        long winnerVotes = predictions.stream().filter(p -> p.get(0).equals(result.get(0))).count();
        assertTrue(winnerVotes > predictions.size() / 2.0,
                "Winner should have strictly more than half the votes");
    }

    @Test
    void averagingStrategyKnownInputs() {
        AveragingStrategy strategy = new AveragingStrategy();
        List<List<Double>> predictions = List.of(
                List.of(2.0, 4.0),
                List.of(4.0, 6.0),
                List.of(6.0, 8.0)
        );
        List<Double> result = strategy.aggregate(predictions);
        assertEquals(4.0, result.get(0), 0.001, "Average of [2,4,6] = 4.0");
        assertEquals(6.0, result.get(1), 0.001, "Average of [4,6,8] = 6.0");
    }

    @Test
    void averagingSingleListReturnsSameValues() {
        AveragingStrategy strategy = new AveragingStrategy();
        List<List<Double>> predictions = List.of(List.of(3.5, 7.2, 1.0));
        List<Double> result = strategy.aggregate(predictions);
        assertEquals(3.5, result.get(0), 0.001);
        assertEquals(7.2, result.get(1), 0.001);
        assertEquals(1.0, result.get(2), 0.001);
    }

    @Test
    void majorityVotingAllSameClass() {
        MajorityVotingStrategy strategy = new MajorityVotingStrategy();
        List<List<Double>> predictions = List.of(
                List.of(0.0, 0.0),
                List.of(0.0, 0.0),
                List.of(0.0, 0.0)
        );
        List<Double> result = strategy.aggregate(predictions);
        assertEquals(0.0, result.get(0));
        assertEquals(0.0, result.get(1));
    }
}
