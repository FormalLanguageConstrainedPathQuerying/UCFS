package org.elasticsearch.compute.aggregation;

import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.util.List;
import org.elasticsearch.compute.operator.DriverContext;

/**
 * {@link AggregatorFunctionSupplier} implementation for {@link PercentileDoubleAggregator}.
 * This class is generated. Do not edit it.
 */
public final class PercentileDoubleAggregatorFunctionSupplier implements AggregatorFunctionSupplier {
  private final List<Integer> channels;

  private final double percentile;

  public PercentileDoubleAggregatorFunctionSupplier(List<Integer> channels, double percentile) {
    this.channels = channels;
    this.percentile = percentile;
  }

  @Override
  public PercentileDoubleAggregatorFunction aggregator(DriverContext driverContext) {
    return PercentileDoubleAggregatorFunction.create(driverContext, channels, percentile);
  }

  @Override
  public PercentileDoubleGroupingAggregatorFunction groupingAggregator(
      DriverContext driverContext) {
    return PercentileDoubleGroupingAggregatorFunction.create(channels, driverContext, percentile);
  }

  @Override
  public String describe() {
    return "percentile of doubles";
  }
}
