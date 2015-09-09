/*
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.benchmark.impl.result;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.optaplanner.benchmark.impl.measurement.ScoreDifferencePercentage;
import org.optaplanner.benchmark.impl.ranking.SubSingleBenchmarkRankingComparator;
import org.optaplanner.benchmark.impl.report.BenchmarkReport;
import org.optaplanner.benchmark.impl.statistic.ProblemStatistic;
import org.optaplanner.benchmark.impl.statistic.PureSingleStatistic;
import org.optaplanner.benchmark.impl.statistic.SingleStatistic;
import org.optaplanner.benchmark.impl.statistic.StatisticType;
import org.optaplanner.core.api.score.FeasibilityScore;
import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.config.util.ConfigUtils;
import org.optaplanner.core.impl.score.ScoreUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents 1 benchmark for 1 {@link Solver} configuration for 1 problem instance (data set).
 */
@XStreamAlias("singleBenchmarkResult")
public class SingleBenchmarkResult implements SolverProblemBenchmarkResult {

    protected static final transient Logger logger = LoggerFactory.getLogger(SingleBenchmarkResult.class);

    @XStreamOmitField // Bi-directional relationship restored through BenchmarkResultIO
    private SolverBenchmarkResult solverBenchmarkResult;
    @XStreamOmitField // Bi-directional relationship restored through BenchmarkResultIO
    private ProblemBenchmarkResult problemBenchmarkResult;

    @XStreamImplicit(itemFieldName = "subSingleBenchmarkResult")
    private List<SubSingleBenchmarkResult> subSingleBenchmarkResultList = null;

    @XStreamImplicit()
    private List<PureSingleStatistic> pureSingleStatisticList = null;

    @XStreamOmitField // Lazily restored when read through ProblemStatistic and CSV files
    private Map<StatisticType, SingleStatistic> effectiveSingleStatisticMap;

    private Long usedMemoryAfterInputSolution = null;

    private Integer failureCount = null;
    private Integer totalUninitializedVariableCount = null;
    private Score totalScore = null;
    private Integer averageUninitializedVariableCount = null;
    private Score averageScore = null;
    private Integer medianUninitializedVariableCount = null;
    private Score medianScore = null;
    private Integer worstUninitializedVariableCount = null;
    private Score worstScore = null;
    private Integer bestUninitializedVariableCount = null;
    private Score bestScore = null;
    private Integer uninitializedSolutionCount = null;
    private Integer infeasibleScoreCount = null;
    // Not a Score because
    // - the squaring would cause overflow for relatively small int and long scores.
    // - standard deviation should not be rounded to integer numbers
    private double[] standardDeviationDoubles = null;
    private long timeMillisSpent = -1L;
    private long calculateCount = -1L;

    // ************************************************************************
    // Report accumulates
    // ************************************************************************

    // Compared to winningSingleBenchmarkResult in the same ProblemBenchmarkResult (which might not be the overall favorite)
    private Score winningScoreDifference = null;
    private ScoreDifferencePercentage worstScoreDifferencePercentage = null;

    // Ranking starts from 0
    private Integer ranking = null;

    public SingleBenchmarkResult(SolverBenchmarkResult solverBenchmarkResult, ProblemBenchmarkResult problemBenchmarkResult) {
        this.solverBenchmarkResult = solverBenchmarkResult;
        this.problemBenchmarkResult = problemBenchmarkResult;
    }

    public List<PureSingleStatistic> getPureSingleStatisticList() {
        return pureSingleStatisticList;
    }

    public void setPureSingleStatisticList(List<PureSingleStatistic> pureSingleStatisticList) {
        this.pureSingleStatisticList = pureSingleStatisticList;
    }

    public void initSingleStatisticMap() {
        effectiveSingleStatisticMap = new HashMap<StatisticType, SingleStatistic>(pureSingleStatisticList.size()
                + problemBenchmarkResult.getProblemStatisticList().size());
        for (ProblemStatistic problemStatistic : problemBenchmarkResult.getProblemStatisticList()) {
            SingleStatistic singleStatistic = problemStatistic.createSingleStatistic(this);
            effectiveSingleStatisticMap.put(singleStatistic.getStatisticType(), singleStatistic);
        }
        for (PureSingleStatistic pureSingleStatistic : pureSingleStatisticList) {
            effectiveSingleStatisticMap.put(pureSingleStatistic.getStatisticType(), pureSingleStatistic);
        }
        for (SubSingleBenchmarkResult subSingleBenchmarkResult : subSingleBenchmarkResultList) {
            subSingleBenchmarkResult.initSubSingleStatisticMap();
        }
    }

    @Override
    public SolverBenchmarkResult getSolverBenchmarkResult() {
        return solverBenchmarkResult;
    }

    public void setSolverBenchmarkResult(SolverBenchmarkResult solverBenchmarkResult) {
        this.solverBenchmarkResult = solverBenchmarkResult;
    }

    @Override
    public ProblemBenchmarkResult getProblemBenchmarkResult() {
        return problemBenchmarkResult;
    }

    public void setProblemBenchmarkResult(ProblemBenchmarkResult problemBenchmarkResult) {
        this.problemBenchmarkResult = problemBenchmarkResult;
    }

    public List<SubSingleBenchmarkResult> getSubSingleBenchmarkResultList() {
        return subSingleBenchmarkResultList;
    }

    public void setSubSingleBenchmarkResultList(List<SubSingleBenchmarkResult> subSingleBenchmarkResultList) {
        this.subSingleBenchmarkResultList = subSingleBenchmarkResultList;
    }

    public Map<StatisticType, SingleStatistic> getEffectiveSingleStatisticMap() {
        return effectiveSingleStatisticMap;
    }

    /**
     * @return null if {@link PlannerBenchmarkResult#hasMultipleParallelBenchmarks()} return true
     */
    public Long getUsedMemoryAfterInputSolution() {
        return usedMemoryAfterInputSolution;
    }

    public void setUsedMemoryAfterInputSolution(Long usedMemoryAfterInputSolution) {
        this.usedMemoryAfterInputSolution = usedMemoryAfterInputSolution;
    }

    public Integer getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(Integer failureCount) {
        this.failureCount = failureCount;
    }

    public long getTimeMillisSpent() {
        return timeMillisSpent;
    }

    public void setTimeMillisSpent(long timeMillisSpent) {
        this.timeMillisSpent = timeMillisSpent;
    }

    public long getCalculateCount() {
        return calculateCount;
    }

    public void setCalculateCount(long calculateCount) {
        this.calculateCount = calculateCount;
    }

    public Score getWinningScoreDifference() {
        return winningScoreDifference;
    }

    public void setWinningScoreDifference(Score winningScoreDifference) {
        this.winningScoreDifference = winningScoreDifference;
    }

    public ScoreDifferencePercentage getWorstScoreDifferencePercentage() {
        return worstScoreDifferencePercentage;
    }

    public void setWorstScoreDifferencePercentage(ScoreDifferencePercentage worstScoreDifferencePercentage) {
        this.worstScoreDifferencePercentage = worstScoreDifferencePercentage;
    }

    public Integer getRanking() {
        return ranking;
    }

    public void setRanking(Integer ranking) {
        this.ranking = ranking;
    }

    public Score getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(Score averageScore) {
        this.averageScore = averageScore;
    }

    public Score getMedianScore() {
        return medianScore;
    }

    public Integer getMedianUninitializedVariableCount() {
        return medianUninitializedVariableCount;
    }

    public double[] getStandardDeviationDoubles() {
        return standardDeviationDoubles;
    }

    public Integer getAverageUninitializedVariableCount() {
        return averageUninitializedVariableCount;
    }

    public void setAverageUninitializedVariableCount(Integer averageUninitializedVariableCount) {
        this.averageUninitializedVariableCount = averageUninitializedVariableCount;
    }

    public Integer getInfeasibleScoreCount() {
        return infeasibleScoreCount;
    }

    public Integer getTotalUninitializedVariableCount() {
        return totalUninitializedVariableCount;
    }

    public Integer getUninitializedSolutionCount() {
        return uninitializedSolutionCount;
    }

    public Score getTotalScore() {
        return totalScore;
    }

    // ************************************************************************
    // Smart getters
    // ************************************************************************

    /**
     * @return never null, filename safe
     */
    public String getName() {
        return problemBenchmarkResult.getName() + "_" + solverBenchmarkResult.getName();
    }

    public File getBenchmarkReportDirectory() {
        return problemBenchmarkResult.getBenchmarkReportDirectory();
    }

    public boolean isSuccess() {
        return !isFailure();
    }

    public boolean isInitialized() {
        return averageUninitializedVariableCount != null && averageUninitializedVariableCount == 0;
    }

    public boolean isFailure() {
        return failureCount != null && failureCount != 0;
    }

    public boolean isScoreFeasible() {
        if (averageScore instanceof FeasibilityScore) {
            return ((FeasibilityScore) averageScore).isFeasible();
        } else {
            return true;
        }
    }

    public Long getAverageCalculateCountPerSecond() {
        long timeMillisSpent = this.timeMillisSpent;
        if (timeMillisSpent == 0L) {
            // Avoid divide by zero exception on a fast CPU
            timeMillisSpent = 1L;
        }
        return calculateCount * 1000L / timeMillisSpent;
    }

    public boolean isWinner() {
        return ranking != null && ranking.intValue() == 0;
    }

    public SingleStatistic getSingleStatistic(StatisticType statisticType) {
        return effectiveSingleStatisticMap.get(statisticType);
    }

    public String getMedianScoreWithUninitializedPrefix() {
        if (medianUninitializedVariableCount == null) {
            return null;
        }
        return ScoreUtils.getScoreWithUninitializedPrefix(medianUninitializedVariableCount, medianScore);
    }

    public String getWorstScoreWithUninitializedPrefix() {
        if (worstUninitializedVariableCount == null) {
            return null;
        }
        return ScoreUtils.getScoreWithUninitializedPrefix(worstUninitializedVariableCount, worstScore);
    }

    public String getBestScoreWithUninitializedPrefix() {
        if (bestUninitializedVariableCount == null) {
            return null;
        }
        return ScoreUtils.getScoreWithUninitializedPrefix(bestUninitializedVariableCount, bestScore);
    }

    public String getAverageScoreWithUninitializedPrefix() {
        return ScoreUtils.getScoreWithUninitializedPrefix(
                ConfigUtils.ceilDivide(getTotalUninitializedVariableCount(), getSuccessCount()),
                getAverageScore());
    }

    public int getSuccessCount() {
        return subSingleBenchmarkResultList.size() - failureCount;
    }

    public String getStandardDeviationString() {
        return StatsUtil.getStandardDeviationString(standardDeviationDoubles);
    }

    // ************************************************************************
    // Accumulate methods
    // ************************************************************************

    public String getReportDirectoryPath() {
        return solverBenchmarkResult.getName();
    }

    public File getReportDirectory() {
        return new File(problemBenchmarkResult.getProblemReportDirectory(), getReportDirectoryPath());
    }

    public void makeDirs() {
        File singleReportDirectory = getReportDirectory();
        singleReportDirectory.mkdirs();
        for (SubSingleBenchmarkResult subSingleBenchmarkResult : subSingleBenchmarkResultList) {
            subSingleBenchmarkResult.makeDirs();
        }
    }

    public void accumulateResults(BenchmarkReport benchmarkReport) {
        for (SubSingleBenchmarkResult subSingleBenchmarkResult : subSingleBenchmarkResultList) {
            subSingleBenchmarkResult.accumulateResults(benchmarkReport);
        }
        determineTotalsAndAveragesAndRanking();
        determineStandardDeviation();
        if (!solverBenchmarkResult.getPlannerBenchmarkResult().getAggregation()) {
            SubSingleBenchmarkResult median = determineRepresentativeSubSingleBenchmarkResult();
            mergeSubSingleStatistics(median);
        }
    }

    private void mergeSubSingleStatistics(SubSingleBenchmarkResult median) {
        if (median.getEffectiveSingleStatisticMap() != null && !median.isFailure()) { // OOO fix this, copy statistics and pures
            for (SingleStatistic singleStatistic : median.getEffectiveSingleStatisticMap().values()) { // copy to parent dir
                singleStatistic.unhibernatePointList();
                singleStatistic.setSolverProblemBenchmarkResult(this);
                singleStatistic.hibernatePointList();
            }
        }
    }

    private SubSingleBenchmarkResult determineRepresentativeSubSingleBenchmarkResult() {
        if (subSingleBenchmarkResultList == null || subSingleBenchmarkResultList.isEmpty()) {
            throw new IllegalStateException("Cannot get representative subSingleBenchmarkResult from empty subSingleBenchmarkResultList.");
        }
        List<SubSingleBenchmarkResult> subSingleBenchmarkResultListCopy = new ArrayList<SubSingleBenchmarkResult>(subSingleBenchmarkResultList);
        // sort (according to ranking) so that the best subSingle is at index 0
        Collections.sort(subSingleBenchmarkResultListCopy, new Comparator<SubSingleBenchmarkResult>() {
            @Override
            public int compare(SubSingleBenchmarkResult o1, SubSingleBenchmarkResult o2) {
                return new CompareToBuilder()
                        .append(o1.isFailure(), o2.isFailure())
                        .append(o1.getRanking(), o2.getRanking())
                        .toComparison();
            }
        });
        SubSingleBenchmarkResult best = subSingleBenchmarkResultListCopy.get(0);
        SubSingleBenchmarkResult worst = subSingleBenchmarkResultListCopy.get(subSingleBenchmarkResultListCopy.size() - 1);
        SubSingleBenchmarkResult median = subSingleBenchmarkResultListCopy.get(ConfigUtils.ceilDivide(subSingleBenchmarkResultListCopy.size() - 1, 2));
        usedMemoryAfterInputSolution = median.getUsedMemoryAfterInputSolution();
        timeMillisSpent = median.getTimeMillisSpent();
        calculateCount = median.getCalculateCount();
        winningScoreDifference = median.getWinningScoreDifference();
        worstScoreDifferencePercentage = median.getWorstScoreDifferencePercentage();
        medianUninitializedVariableCount = median.getUninitializedVariableCount();
        medianScore = median.getScore();
        worstUninitializedVariableCount = worst.getUninitializedVariableCount();
        worstScore = worst.getScore();
        bestUninitializedVariableCount = best.getUninitializedVariableCount();
        bestScore = best.getScore();
        return median;
    }

    private void determineTotalsAndAveragesAndRanking() {
        failureCount = 0;
        boolean firstNonFailure = true;
        totalScore = null;
        uninitializedSolutionCount = 0;
        totalUninitializedVariableCount = 0;
        infeasibleScoreCount = 0;
        List<SubSingleBenchmarkResult> successResultList = new ArrayList<SubSingleBenchmarkResult>(subSingleBenchmarkResultList);
        // Do not rank a SubSingleBenchmarkResult that has a failure
        for (Iterator<SubSingleBenchmarkResult> it = successResultList.iterator(); it.hasNext(); ) {
            SubSingleBenchmarkResult subSingleBenchmarkResult = it.next();
            if (subSingleBenchmarkResult.isFailure()) {
                failureCount++;
                it.remove();
            } else {
                if (!subSingleBenchmarkResult.isInitialized()) {
                    uninitializedSolutionCount++;
                    totalUninitializedVariableCount += subSingleBenchmarkResult.getUninitializedVariableCount();
                } else if (!subSingleBenchmarkResult.isScoreFeasible()) {
                    infeasibleScoreCount++;
                }
                if (firstNonFailure) {
                    totalScore = subSingleBenchmarkResult.getScore();
                    firstNonFailure = false;
                } else {
                    totalScore = totalScore.add(subSingleBenchmarkResult.getScore());
                }
            }
        }
        if (!firstNonFailure) {
            averageScore = totalScore.divide(getSuccessCount());
            averageUninitializedVariableCount = ConfigUtils.ceilDivide(totalUninitializedVariableCount, getSuccessCount());
        }
        determineRanking(successResultList);
    }

    private void determineRanking(List<SubSingleBenchmarkResult> rankedSubSingleBenchmarkResultList) {
        Comparator subSingleBenchmarkRankingComparator = new SubSingleBenchmarkRankingComparator();
        Collections.sort(rankedSubSingleBenchmarkResultList, Collections.reverseOrder(subSingleBenchmarkRankingComparator));
        int ranking = 0;
        SubSingleBenchmarkResult previousSubSingleBenchmarkResult = null;
        int previousSameRankingCount = 0;
        for (SubSingleBenchmarkResult subSingleBenchmarkResult : rankedSubSingleBenchmarkResultList) {
            if (previousSubSingleBenchmarkResult != null
                    && subSingleBenchmarkRankingComparator.compare(previousSubSingleBenchmarkResult, subSingleBenchmarkResult) != 0) {
                ranking += previousSameRankingCount;
                previousSameRankingCount = 0;
            }
            subSingleBenchmarkResult.setRanking(ranking);
            previousSubSingleBenchmarkResult = subSingleBenchmarkResult;
            previousSameRankingCount++;
        }
    }

    protected void determineStandardDeviation() {
        int successCount = getSuccessCount();
        if (successCount <= 0) {
            return;
        }
        // averageScore can no longer be null
        double[] differenceSquaredTotalDoubles = null;
        for (SubSingleBenchmarkResult subSingleBenchmarkResult : subSingleBenchmarkResultList) {
            if (!subSingleBenchmarkResult.isFailure()) {
                Score difference = subSingleBenchmarkResult.getScore().subtract(averageScore);
                // Calculations done on doubles to avoid common overflow when executing with an int score > 500 000
                double[] differenceDoubles = ScoreUtils.extractLevelDoubles(difference);
                if (differenceSquaredTotalDoubles == null) {
                    differenceSquaredTotalDoubles = new double[differenceDoubles.length];
                }
                for (int i = 0; i < differenceDoubles.length; i++) {
                    differenceSquaredTotalDoubles[i] += Math.pow(differenceDoubles[i], 2.0);
                }
            }
        }
        standardDeviationDoubles = new double[differenceSquaredTotalDoubles.length];
        for (int i = 0; i < differenceSquaredTotalDoubles.length; i++) {
            standardDeviationDoubles[i] = Math.pow(differenceSquaredTotalDoubles[i] / successCount, 0.5);
        }
    }

    // ************************************************************************
    // Merger methods
    // ************************************************************************

    protected static SingleBenchmarkResult createMerge(SolverBenchmarkResult solverBenchmarkResult,
            ProblemBenchmarkResult problemBenchmarkResult, SingleBenchmarkResult oldResult) {
        SingleBenchmarkResult newResult = new SingleBenchmarkResult(solverBenchmarkResult, problemBenchmarkResult);
        newResult.pureSingleStatisticList = new ArrayList<PureSingleStatistic>(oldResult.pureSingleStatisticList.size());
        for (PureSingleStatistic oldSingleStatistic : oldResult.pureSingleStatisticList) {
            newResult.pureSingleStatisticList.add(
                    oldSingleStatistic.getStatisticType().buildPureSingleStatistic(newResult));
        }

        newResult.subSingleBenchmarkResultList = new ArrayList<SubSingleBenchmarkResult>(oldResult.getSubSingleBenchmarkResultList().size());
        int subSingleBenchmarkIndex = 0;
        for (SubSingleBenchmarkResult oldSubResult : oldResult.subSingleBenchmarkResultList) {
            SubSingleBenchmarkResult.createMerge(newResult, oldSubResult, subSingleBenchmarkIndex);
            subSingleBenchmarkIndex++;
        }

        newResult.initSingleStatisticMap();
        for (SingleStatistic singleStatistic : newResult.effectiveSingleStatisticMap.values()) {
            SingleStatistic oldSingleStatistic = oldResult.getSingleStatistic(singleStatistic.getStatisticType());
            if (!oldSingleStatistic.getCsvFile().exists()) {
                if (oldResult.isFailure()) {
                    singleStatistic.initPointList();
                    logger.debug("Old result ( {} ) is a failure, skipping merge of it's single statistic ( {} ).",
                            oldResult, oldSingleStatistic);
                    continue;
                } else {
                    throw new IllegalStateException("Could not find old result's ( " + oldResult
                            + " ) single statistic's ( " + oldSingleStatistic + " ) CSV file.");
                }
            }
            oldSingleStatistic.unhibernatePointList();
            singleStatistic.setPointList(oldSingleStatistic.getPointList());
            oldSingleStatistic.hibernatePointList();
        }
        newResult.medianScore = oldResult.medianScore;
        newResult.medianUninitializedVariableCount = oldResult.medianUninitializedVariableCount;
        newResult.worstScore = oldResult.worstScore;
        newResult.worstUninitializedVariableCount = oldResult.worstUninitializedVariableCount;
        newResult.bestScore = oldResult.bestScore;
        newResult.bestUninitializedVariableCount = oldResult.bestUninitializedVariableCount;

        solverBenchmarkResult.getSingleBenchmarkResultList().add(newResult);
        problemBenchmarkResult.getSingleBenchmarkResultList().add(newResult);
        return newResult;
    }

    @Override
    public String toString() {
        return getName();
    }

}
