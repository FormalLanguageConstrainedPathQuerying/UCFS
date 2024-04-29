/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.rollup;

import org.elasticsearch.common.Rounding;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;
import org.elasticsearch.xpack.core.rollup.RollupField;
import org.elasticsearch.xpack.core.rollup.action.RollupJobCaps;
import org.elasticsearch.xpack.core.rollup.job.DateHistogramGroupConfig;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.xpack.core.rollup.job.DateHistogramGroupConfig.CALENDAR_INTERVAL;
import static org.elasticsearch.xpack.core.rollup.job.DateHistogramGroupConfig.FIXED_INTERVAL;
import static org.elasticsearch.xpack.core.rollup.job.DateHistogramGroupConfig.INTERVAL;

/**
 * This class contains utilities to identify which jobs are the "best" for a given aggregation tree.
 * It allows the caller to pass in a set of possible rollup job capabilities and get in return
 * a smaller (but not guaranteed minimal) set of valid jobs that can be searched.
 */
public class RollupJobIdentifierUtils {

    static final Comparator<RollupJobCaps> COMPARATOR = RollupJobIdentifierUtils.getComparator();

    /**
     * Given the aggregation tree and a list of available job capabilities, this method will return a set
     * of the "best" jobs that should be searched.
     *
     * It does this by recursively descending through the aggregation tree and independently pruning the
     * list of valid job caps in each branch.  When a leaf node is reached in the branch, the remaining
     * jobs are sorted by "best'ness" (see {@link #getComparator()} for the implementation)
     * and added to a global set of "best jobs".
     *
     * Once all branches have been evaluated, the final set is returned to the calling code.
     *
     * Job "best'ness" is, briefly, the job(s) that have
     *  - The larger compatible date interval
     *  - Fewer and larger interval histograms
     *  - Fewer terms groups
     *
     * Note: the final set of "best" jobs is not guaranteed to be minimal, there may be redundant effort
     * due to independent branches choosing jobs that are subsets of other branches.
     *
     * @param source The source aggregation that we are trying to find jobs for
     * @param jobCaps The total set of available job caps on the index/indices
     * @return A set of the "best" jobs out of the total job caps
     */
    public static Set<RollupJobCaps> findBestJobs(AggregationBuilder source, Set<RollupJobCaps> jobCaps) {
        Set<RollupJobCaps> bestCaps = new HashSet<>();
        doFindBestJobs(source, new ArrayList<>(jobCaps), bestCaps);
        return bestCaps;
    }

    private static void doFindBestJobs(AggregationBuilder source, List<RollupJobCaps> jobCaps, Set<RollupJobCaps> bestCaps) {
        if (source.getWriteableName().equals(DateHistogramAggregationBuilder.NAME)) {
            checkDateHisto((DateHistogramAggregationBuilder) source, jobCaps, bestCaps);
        } else if (source.getWriteableName().equals(HistogramAggregationBuilder.NAME)) {
            checkHisto((HistogramAggregationBuilder) source, jobCaps, bestCaps);
        } else if (RollupField.SUPPORTED_METRICS.contains(source.getWriteableName())) {
            checkVSLeaf((ValuesSourceAggregationBuilder.LeafOnly) source, jobCaps, bestCaps);
        } else if (source.getWriteableName().equals(TermsAggregationBuilder.NAME)) {
            checkTerms((TermsAggregationBuilder) source, jobCaps, bestCaps);
        } else {
            throw new IllegalArgumentException(
                "Unable to translate aggregation tree into Rollup.  Aggregation ["
                    + source.getName()
                    + "] is of type ["
                    + source.getClass().getSimpleName()
                    + "] which is "
                    + "currently unsupported."
            );
        }
    }

    /**
     * Find the set of date_histo's with the largest granularity interval
     */
    private static void checkDateHisto(DateHistogramAggregationBuilder source, List<RollupJobCaps> jobCaps, Set<RollupJobCaps> bestCaps) {
        ArrayList<RollupJobCaps> localCaps = new ArrayList<>();
        for (RollupJobCaps cap : jobCaps) {
            RollupJobCaps.RollupFieldCaps fieldCaps = cap.getFieldCaps().get(source.field());
            if (fieldCaps != null) {
                for (Map<String, Object> agg : fieldCaps.getAggs()) {
                    if (agg.get(RollupField.AGG).equals(DateHistogramAggregationBuilder.NAME)) {
                        DateHistogramInterval interval = new DateHistogramInterval((String) agg.get(RollupField.INTERVAL));

                        ZoneId thisTimezone = ZoneId.of(((String) agg.get(DateHistogramGroupConfig.TIME_ZONE)), ZoneId.SHORT_IDS);
                        ZoneId sourceTimeZone = source.timeZone() == null
                            ? DateHistogramGroupConfig.DEFAULT_ZONEID_TIMEZONE
                            : ZoneId.of(source.timeZone().toString(), ZoneId.SHORT_IDS);

                        if (thisTimezone.getRules().equals(sourceTimeZone.getRules()) == false) {
                            continue;
                        }

                        /*
                          This is convoluted, but new + legacy intervals makes for a big pattern match.
                          We have to match up date_histo [interval, fixed_interval, calendar_interval] with
                          rollup config [interval, fixed_interval, calendar_interval]

                          To keep rightward drift to a minimum we break out of the loop if a successful match is found
                        */

                        DateHistogramInterval configCalendarInterval = agg.get(CALENDAR_INTERVAL) != null
                            ? new DateHistogramInterval((String) agg.get(CALENDAR_INTERVAL))
                            : null;
                        DateHistogramInterval configFixedInterval = agg.get(FIXED_INTERVAL) != null
                            ? new DateHistogramInterval((String) agg.get(FIXED_INTERVAL))
                            : null;
                        DateHistogramInterval configLegacyInterval = agg.get(INTERVAL) != null
                            ? new DateHistogramInterval((String) agg.get(INTERVAL))
                            : null;

                        if (source.getCalendarInterval() != null) {
                            DateHistogramInterval requestInterval = source.getCalendarInterval();

                            if (validateCalendarInterval(requestInterval, configCalendarInterval)) {
                                localCaps.add(cap);
                                break;
                            }

                            if (validateCalendarInterval(requestInterval, configLegacyInterval)) {
                                localCaps.add(cap);
                                break;
                            }


                        } else if (source.getFixedInterval() != null) {

                            DateHistogramInterval requestInterval = source.getFixedInterval();

                            if (validateFixedInterval(requestInterval, configFixedInterval)) {
                                localCaps.add(cap);
                                break;
                            }

                            if (validateFixedInterval(requestInterval, configLegacyInterval)) {
                                localCaps.add(cap);
                                break;
                            }


                        } else {
                            throw new IllegalArgumentException(
                                "An interval of some variety must be configured on " + "the date_histogram aggregation."
                            );
                        }
                        break;
                    }
                }
            }
        }

        if (localCaps.isEmpty()) {
            throw new IllegalArgumentException(
                "There is not a rollup job that has a ["
                    + source.getWriteableName()
                    + "] agg on field ["
                    + source.field()
                    + "] which also satisfies all requirements of query."
            );
        }

        if (source.getSubAggregations().size() == 0) {
            bestCaps.add(getTopEqualCaps(localCaps));
        } else {
            source.getSubAggregations().forEach(sub -> doFindBestJobs(sub, localCaps, bestCaps));
        }
    }

    static String retrieveInterval(Map<String, Object> agg) {
        String interval = (String) agg.get(RollupField.INTERVAL);
        if (interval == null) {
            interval = (String) agg.get(CALENDAR_INTERVAL);
        }
        if (interval == null) {
            interval = (String) agg.get(FIXED_INTERVAL);
        }
        if (interval == null) {
            throw new IllegalStateException("Could not find interval in agg cap: " + agg.toString());
        }
        return interval;
    }

    static boolean validateCalendarInterval(DateHistogramInterval requestInterval, DateHistogramInterval configInterval) {
        if (requestInterval == null || configInterval == null) {
            return false;
        }

        Rounding.DateTimeUnit requestUnit = DateHistogramAggregationBuilder.DATE_FIELD_UNITS.get(requestInterval.toString());
        if (requestUnit == null) {
            return false;
        }
        Rounding.DateTimeUnit configUnit = DateHistogramAggregationBuilder.DATE_FIELD_UNITS.get(configInterval.toString());
        if (configUnit == null) {
            return false;
        }

        long requestOrder = requestUnit.getField().getBaseUnit().getDuration().toMillis();
        long configOrder = configUnit.getField().getBaseUnit().getDuration().toMillis();

        return requestOrder >= configOrder;
    }

    static boolean validateFixedInterval(DateHistogramInterval requestInterval, DateHistogramInterval configInterval) {
        if (requestInterval == null || configInterval == null) {
            return false;
        }

        long configIntervalMillis = TimeValue.parseTimeValue(configInterval.toString(), "date_histo.config.interval").getMillis();
        long requestIntervalMillis = TimeValue.parseTimeValue(requestInterval.toString(), "date_histo.request.interval").getMillis();

        return requestIntervalMillis >= configIntervalMillis && requestIntervalMillis % configIntervalMillis == 0;
    }

    /**
     * Find the set of histo's with the largest interval
     */
    private static void checkHisto(HistogramAggregationBuilder source, List<RollupJobCaps> jobCaps, Set<RollupJobCaps> bestCaps) {
        ArrayList<RollupJobCaps> localCaps = new ArrayList<>();
        for (RollupJobCaps cap : jobCaps) {
            RollupJobCaps.RollupFieldCaps fieldCaps = cap.getFieldCaps().get(source.field());
            if (fieldCaps != null) {
                for (Map<String, Object> agg : fieldCaps.getAggs()) {
                    if (agg.get(RollupField.AGG).equals(HistogramAggregationBuilder.NAME)) {
                        long interval = (long) agg.get(RollupField.INTERVAL);
                        if (interval <= source.interval() && source.interval() % interval == 0) {
                            localCaps.add(cap);
                        }
                        break;
                    }
                }
            }
        }

        if (localCaps.isEmpty()) {
            throw new IllegalArgumentException(
                "There is not a rollup job that has a ["
                    + source.getWriteableName()
                    + "] agg on field ["
                    + source.field()
                    + "] which also satisfies all requirements of query."
            );
        }

        if (source.getSubAggregations().size() == 0) {
            bestCaps.add(getTopEqualCaps(localCaps));
        } else {
            source.getSubAggregations().forEach(sub -> doFindBestJobs(sub, localCaps, bestCaps));
        }
    }

    /**
     * Ensure that the terms aggregation is supported by one or more job caps.  There is no notion of "best"
     * caps for terms, it is either supported or not.
     */
    private static void checkTerms(TermsAggregationBuilder source, List<RollupJobCaps> jobCaps, Set<RollupJobCaps> bestCaps) {
        ArrayList<RollupJobCaps> localCaps = new ArrayList<>();
        for (RollupJobCaps cap : jobCaps) {
            RollupJobCaps.RollupFieldCaps fieldCaps = cap.getFieldCaps().get(source.field());
            if (fieldCaps != null) {
                for (Map<String, Object> agg : fieldCaps.getAggs()) {
                    if (agg.get(RollupField.AGG).equals(TermsAggregationBuilder.NAME)) {
                        localCaps.add(cap);
                        break;
                    }
                }
            }
        }

        if (localCaps.isEmpty()) {
            throw new IllegalArgumentException(
                "There is not a rollup job that has a ["
                    + source.getWriteableName()
                    + "] agg on field ["
                    + source.field()
                    + "] which also satisfies all requirements of query."
            );
        }

        if (source.getSubAggregations().size() == 0) {
            bestCaps.add(getTopEqualCaps(localCaps));
        } else {
            source.getSubAggregations().forEach(sub -> doFindBestJobs(sub, localCaps, bestCaps));
        }
    }

    /**
     * Ensure that the metrics are supported by one or more job caps.  There is no notion of "best"
     * caps for metrics, it is either supported or not.
     */
    private static void checkVSLeaf(
        ValuesSourceAggregationBuilder.LeafOnly<?> source,
        List<RollupJobCaps> jobCaps,
        Set<RollupJobCaps> bestCaps
    ) {
        ArrayList<RollupJobCaps> localCaps = new ArrayList<>();
        for (RollupJobCaps cap : jobCaps) {
            RollupJobCaps.RollupFieldCaps fieldCaps = cap.getFieldCaps().get(source.field());
            if (fieldCaps != null) {
                for (Map<String, Object> agg : fieldCaps.getAggs()) {
                    if (agg.get(RollupField.AGG).equals(source.getWriteableName())) {
                        localCaps.add(cap);
                        break;
                    }
                }
            }
        }

        if (localCaps.isEmpty()) {
            throw new IllegalArgumentException(
                "There is not a rollup job that has a ["
                    + source.getWriteableName()
                    + "] agg with name ["
                    + source.getName()
                    + "] which also satisfies all requirements of query."
            );
        }

        bestCaps.add(getTopEqualCaps(localCaps));
    }

    private static RollupJobCaps getTopEqualCaps(List<RollupJobCaps> caps) {
        assert caps.isEmpty() == false;
        caps.sort(COMPARATOR);
        return caps.get(0);
    }

    private static Comparator<RollupJobCaps> getComparator() {
        return (o1, o2) -> {
            if (o1 == null) {
                throw new NullPointerException("RollupJobCap [o1] cannot be null");
            }
            if (o2 == null) {
                throw new NullPointerException("RollupJobCap [o2] cannot be null");
            }

            if (o1.equals(o2)) {
                return 0;
            }

            long thisTime = Long.MAX_VALUE;
            long thatTime = Long.MAX_VALUE;

            float thisHistoWeights = 0;
            float thatHistoWeights = 0;
            long counter = 0;

            long thisTermsWeights = 0;
            long thatTermsWeights = 0;

            for (RollupJobCaps.RollupFieldCaps fieldCaps : o1.getFieldCaps().values()) {
                for (Map<String, Object> agg : fieldCaps.getAggs()) {
                    if (agg.get(RollupField.AGG).equals(DateHistogramAggregationBuilder.NAME)) {
                        thisTime = new DateHistogramInterval(retrieveInterval(agg)).estimateMillis();
                    } else if (agg.get(RollupField.AGG).equals(HistogramAggregationBuilder.NAME)) {
                        thisHistoWeights += (long) agg.get(RollupField.INTERVAL);
                        counter += 1;
                    } else if (agg.get(RollupField.AGG).equals(TermsAggregationBuilder.NAME)) {
                        thisTermsWeights += 1;
                    }
                }
            }
            thisHistoWeights = counter == 0 ? 0 : thisHistoWeights / counter;

            counter = 0;
            for (RollupJobCaps.RollupFieldCaps fieldCaps : o2.getFieldCaps().values()) {
                for (Map<String, Object> agg : fieldCaps.getAggs()) {
                    if (agg.get(RollupField.AGG).equals(DateHistogramAggregationBuilder.NAME)) {
                        thatTime = new DateHistogramInterval(retrieveInterval(agg)).estimateMillis();
                    } else if (agg.get(RollupField.AGG).equals(HistogramAggregationBuilder.NAME)) {
                        thatHistoWeights += (long) agg.get(RollupField.INTERVAL);
                        counter += 1;
                    } else if (agg.get(RollupField.AGG).equals(TermsAggregationBuilder.NAME)) {
                        thatTermsWeights += 1;
                    }
                }
            }
            thatHistoWeights = counter == 0 ? 0 : thatHistoWeights / counter;

            int timeCompare = Long.compare(thisTime, thatTime);
            if (timeCompare != 0) {
                return -timeCompare;
            }

            int histoCompare = Float.compare(thisHistoWeights, thatHistoWeights);
            if (histoCompare != 0) {
                if (thisHistoWeights == 0) {
                    return -1;
                } else if (thatHistoWeights == 0) {
                    return 1;
                }
                return -histoCompare;
            }

            return Long.compare(thisTermsWeights, thatTermsWeights);

        };
    }
}
