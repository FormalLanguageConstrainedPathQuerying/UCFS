/*
 * Copyright (C) 2014 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http:
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.math;

import com.google.common.collect.ImmutableMap;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Enumerates several algorithms providing equivalent functionality to {@link Quantiles}, for use in
 * {@link QuantilesBenchmark}. These algorithms each calculate either a single quantile or multiple
 * quantiles. All algorithms modify the dataset they are given (the cost of a copy to avoid this
 * will be constant across algorithms).
 *
 * @author Pete Gillin
 * @since 20.0
 */
enum QuantilesAlgorithm {

  /**
   * Sorts the dataset, and picks values from it. When computing multiple quantiles, we sort once
   * and pick multiple values.
   */
  SORTING {

    @Override
    double singleQuantile(int index, int scale, double[] dataset) {
      Arrays.sort(dataset);
      return singleQuantileFromSorted(index, scale, dataset);
    }

    @Override
    Map<Integer, Double> multipleQuantiles(
        Collection<Integer> indexes, int scale, double[] dataset) {
      Arrays.sort(dataset);
      ImmutableMap.Builder<Integer, Double> builder = ImmutableMap.builder();
      for (int index : indexes) {
        builder.put(index, singleQuantileFromSorted(index, scale, dataset));
      }
      return builder.buildOrThrow();
    }

    private double singleQuantileFromSorted(int index, int scale, double[] dataset) {
      long numerator = (long) index * (dataset.length - 1);
      int positionFloor = (int) LongMath.divide(numerator, scale, RoundingMode.DOWN);
      int remainder = (int) (numerator - positionFloor * scale);
      if (remainder == 0) {
        return dataset[positionFloor];
      } else {
        double positionFrac = (double) remainder / scale;
        return dataset[positionFloor]
            + positionFrac * (dataset[positionFloor + 1] - dataset[positionFloor]);
      }
    }
  },

  /**
   * Uses quickselect. When calculating multiple quantiles, each quickselect starts from scratch.
   */
  QUICKSELECT {

    @Override
    double singleQuantile(int index, int scale, double[] dataset) {
      long numerator = (long) index * (dataset.length - 1);
      int positionFloor = (int) LongMath.divide(numerator, scale, RoundingMode.DOWN);
      int remainder = (int) (numerator - positionFloor * scale);
      double percentileFloor = select(positionFloor, dataset);
      if (remainder == 0) {
        return percentileFloor;
      } else {
        double percentileCeiling = getMinValue(dataset, positionFloor + 1);
        double positionFrac = (double) remainder / scale;
        return percentileFloor + positionFrac * (percentileCeiling - percentileFloor);
      }
    }

    @Override
    Map<Integer, Double> multipleQuantiles(
        Collection<Integer> indexes, int scale, double[] dataset) {
      ImmutableMap.Builder<Integer, Double> builder = ImmutableMap.builder();
      for (int index : indexes) {
        builder.put(index, singleQuantile(index, scale, dataset));
      }
      return builder.buildOrThrow();
    }
  },

  /** Uses {@link Quantiles}. */
  TARGET {

    @Override
    double singleQuantile(int index, int scale, double[] dataset) {
      return Quantiles.scale(scale).index(index).computeInPlace(dataset);
    }

    @Override
    Map<Integer, Double> multipleQuantiles(
        Collection<Integer> indexes, int scale, double[] dataset) {
      return Quantiles.scale(scale).indexes(indexes).computeInPlace(dataset);
    }
  },
  ;

  /**
   * Calculates a single quantile. Equivalent to {@code
   * Quantiles.scale(scale).index(index).computeInPlace(dataset)}.
   */
  abstract double singleQuantile(int index, int scale, double[] dataset);

  /**
   * Calculates multiple quantiles. Equivalent to {@code
   * Quantiles.scale(scale).indexes(indexes).computeInPlace(dataset)}.
   */
  abstract Map<Integer, Double> multipleQuantiles(
      Collection<Integer> indexes, int scale, double[] dataset);

  static double getMinValue(double[] array, int from) {
    int min = from;
    for (int i = from + 1; i < array.length; i++) {
      if (array[min] > array[i]) {
        min = i;
      }
    }
    return array[min];
  }

  static double select(int k, double[] array) {
    int from = 0;
    int to = array.length - 1;

    while (true) {
      if (to <= from + 1) {
        if (to == from + 1 && array[to] < array[from]) {
          swap(array, from, to);
        }
        return array[k];
      } else {
        int midIndex = (from + to) >>> 1;

        swap(array, midIndex, from + 1);

        if (array[from] > array[to]) {
          swap(array, from, to);
        }
        if (array[from + 1] > array[to]) {
          swap(array, from + 1, to);
        }
        if (array[from] > array[from + 1]) {
          swap(array, from, from + 1);
        }

        int low = from + 1, high = to; 
        double partition = array[from + 1]; 
        while (true) {
          do {
            low++;
          } while (array[low] < partition);

          do {
            high--;
          } while (array[high] > partition);
          if (high < low) {
            break; 
          }
          swap(array, low, high); 
        }
        array[from + 1] = array[high]; 
        array[high] = partition;

        if (high >= k) {
          to = high - 1;
        }
        if (high <= k) {
          from = low;
        }
      }
    }
  }

  private static void swap(double[] array, int i, int j) {
    double temp = array[i];
    array[i] = array[j];
    array[j] = temp;
  }
}
