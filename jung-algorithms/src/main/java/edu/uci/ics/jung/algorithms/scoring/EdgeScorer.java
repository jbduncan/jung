/*
 * Created on Jul 6, 2007
 *
 * Copyright (c) 2007, The JUNG Authors
 *
 * All rights reserved.
 *
 * This software is open-source under the BSD license; see either
 * "license.txt" or
 * https://github.com/jrtom/jung/blob/master/LICENSE for a description.
 */
package edu.uci.ics.jung.algorithms.scoring;

/**
 * An interface for algorithms that assign scores to edges.
 *
 * @param <E> the edge type
 * @param <S> the score type
 */
public interface EdgeScorer<E, S> {
  /**
   * @param e the edge whose score is requested
   * @return the algorithm's score for this edge
   */
  public S getEdgeScore(E e);
}
