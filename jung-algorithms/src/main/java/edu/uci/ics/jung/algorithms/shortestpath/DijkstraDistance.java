/*
 * Created on Jul 9, 2005
 *
 * Copyright (c) 2005, The JUNG Authors
 *
 * All rights reserved.
 *
 * This software is open-source under the BSD license; see either
 * "license.txt" or
 * https://github.com/jrtom/jung/blob/master/LICENSE for a description.
 */
package edu.uci.ics.jung.algorithms.shortestpath;

import com.google.common.base.Preconditions;
import com.google.common.graph.Network;
import edu.uci.ics.jung.algorithms.util.MapBinaryHeap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Calculates distances in a specified graph, using Dijkstra's single-source-shortest-path
 * algorithm. All edge weights in the graph must be nonnegative; if any edge with negative weight is
 * found in the course of calculating distances, an <code>IllegalArgumentException</code> will be
 * thrown. (Note: this exception will only be thrown when such an edge would be used to update a
 * given tentative distance; the algorithm does not check for negative-weight edges "up front".)
 *
 * <p>Distances and partial results are optionally cached (by this instance) for later reference.
 * Thus, if the 10 closest nodes to a specified source node are known, calculating the 20 closest
 * nodes does not require starting Dijkstra's algorithm over from scratch.
 *
 * <p>Distances are stored as double-precision values. If a node is not reachable from the specified
 * source node, no distance is stored. <b>This is new behavior with version 1.4</b>; the previous
 * behavior was to store a value of <code>Double.POSITIVE_INFINITY</code>. This change gives the
 * algorithm an approximate complexity of O(kD log k), where k is either the number of requested
 * targets or the number of reachable nodes (whichever is smaller), and D is the average degree of a
 * node.
 *
 * <p>The elements in the maps returned by <code>getDistanceMap</code> are ordered (that is,
 * returned by the iterator) by nondecreasing distance from <code>source</code>.
 *
 * <p>Users are cautioned that distances calculated should be assumed to be invalidated by changes
 * to the graph, and should invoke <code>reset()</code> when appropriate so that the distances can
 * be recalculated.
 *
 * @author Joshua O'Madadhain
 * @author Tom Nelson converted to jung2
 */
public class DijkstraDistance<N, E> implements Distance<N> {
  protected Network<N, E> g;
  protected Function<? super E, ? extends Number> nev;
  protected Map<N, SourceData> sourceMap; // a map of source nodes to an instance of SourceData
  protected boolean cached;
  protected double max_distance;
  protected int max_targets;

  /**
   * Creates an instance of <code>DijkstraShortestPath</code> for the specified graph and the
   * specified method of extracting weights from edges, which caches results locally if and only if
   * <code>cached</code> is <code>true</code>.
   *
   * @param g the graph on which distances will be calculated
   * @param nev the class responsible for returning weights for edges
   * @param cached specifies whether the results are to be cached
   */
  public DijkstraDistance(
      Network<N, E> g, Function<? super E, ? extends Number> nev, boolean cached) {
    this.g = g;
    this.nev = nev;
    this.sourceMap = new HashMap<N, SourceData>();
    this.cached = cached;
    this.max_distance = Double.POSITIVE_INFINITY;
    this.max_targets = Integer.MAX_VALUE;
  }

  /**
   * Creates an instance of <code>DijkstraShortestPath</code> for the specified graph and the
   * specified method of extracting weights from edges, which caches results locally.
   *
   * @param g the graph on which distances will be calculated
   * @param nev the class responsible for returning weights for edges
   */
  public DijkstraDistance(Network<N, E> g, Function<? super E, ? extends Number> nev) {
    this(g, nev, true);
  }

  /**
   * Creates an instance of <code>DijkstraShortestPath</code> for the specified unweighted graph
   * (that is, all weights 1) which caches results locally.
   *
   * @param g the graph on which distances will be calculated
   */
  public DijkstraDistance(Network<N, E> g) {
    this(g, e -> 1, true);
  }

  /**
   * Creates an instance of <code>DijkstraShortestPath</code> for the specified unweighted graph
   * (that is, all weights 1) which caches results locally.
   *
   * @param g the graph on which distances will be calculated
   * @param cached specifies whether the results are to be cached
   */
  public DijkstraDistance(Network<N, E> g, boolean cached) {
    this(g, e -> 1, cached);
  }

  /**
   * Implements Dijkstra's single-source shortest-path algorithm for weighted graphs. Uses a <code>
   * MapBinaryHeap</code> as the priority queue, which gives this algorithm a time complexity of O(m
   * lg n) (m = # of edges, n = # of nodes). This algorithm will terminate when any of the following
   * have occurred (in order of priority):
   *
   * <ul>
   *   <li>the distance to the specified target (if any) has been found
   *   <li>no more nodes are reachable
   *   <li>the specified # of distances have been found, or the maximum distance desired has been
   *       exceeded
   *   <li>all distances have been found
   * </ul>
   *
   * @param source the node from which distances are to be measured
   * @param numDests the number of distances to measure
   * @param targets the set of nodes to which distances are to be measured
   * @return a mapping from node to the shortest distance from the source to each target
   */
  protected LinkedHashMap<N, Number> singleSourceShortestPath(
      N source, Collection<N> targets, int numDests) {
    SourceData sd = getSourceData(source);

    Set<N> to_get = new HashSet<N>();
    if (targets != null) {
      to_get.addAll(targets);
      Set<N> existing_dists = sd.distances.keySet();
      for (N o : targets) {
        if (existing_dists.contains(o)) {
          to_get.remove(o);
        }
      }
    }

    // if we've exceeded the max distance or max # of distances we're willing to calculate, or
    // if we already have all the distances we need,
    // terminate
    if (sd.reached_max
        || (targets != null && to_get.isEmpty())
        || (sd.distances.size() >= numDests)) {
      return sd.distances;
    }

    while (!sd.unknownNodes.isEmpty() && (sd.distances.size() < numDests || !to_get.isEmpty())) {
      Map.Entry<N, Number> p = sd.getNextNode();
      N v = p.getKey();
      double v_dist = p.getValue().doubleValue();
      to_get.remove(v);
      if (v_dist > this.max_distance) {
        // we're done; put this node back in so that we're not including
        // a distance beyond what we specified
        sd.restoreNode(v, v_dist);
        sd.reached_max = true;
        break;
      }
      sd.dist_reached = v_dist;

      if (sd.distances.size() >= this.max_targets) {
        sd.reached_max = true;
        break;
      }

      for (N w : g.successors(v)) {
        for (E e : g.edgesConnecting(v, w)) {
          if (!sd.distances.containsKey(w)) {
            double edge_weight = nev.apply(e).doubleValue();
            Preconditions.checkArgument(
                edge_weight >= 0,
                "encountered negative edge weight %s for edge %s",
                nev.apply(e),
                e);
            double new_dist = v_dist + edge_weight;
            if (!sd.estimatedDistances.containsKey(w)) {
              sd.createRecord(w, e, new_dist);
            } else {
              double w_dist = ((Double) sd.estimatedDistances.get(w)).doubleValue();
              if (new_dist < w_dist) // update tentative distance & path for w
              {
                sd.update(w, e, new_dist);
              }
            }
          }
        }
      }
    }
    return sd.distances;
  }

  protected SourceData getSourceData(N source) {
    SourceData sd = sourceMap.get(source);
    if (sd == null) {
      sd = new SourceData(source);
    }
    return sd;
  }

  /**
   * Returns the length of a shortest path from the source to the target node, or null if the target
   * is not reachable from the source. If either node is not in the graph for which this instance
   * was created, throws <code>IllegalArgumentException</code>.
   *
   * @param source the node from which the distance to {@code target} is to be measured
   * @param target the node to which the distance from {@code source} is to be measured
   * @return the distance between {@code source} and {@code target}
   * @see #getDistanceMap(Object)
   * @see #getDistanceMap(Object,int)
   */
  public Number getDistance(N source, N target) {
    Preconditions.checkArgument(
        g.nodes().contains(target), "Specified target node %s  is not part of graph %s", target, g);
    Preconditions.checkArgument(
        g.nodes().contains(source), "Specified source node %s  is not part of graph %s", source, g);

    Set<N> targets = new HashSet<N>();
    targets.add(target);
    Map<N, Number> distanceMap = getDistanceMap(source, targets);
    return distanceMap.get(target);
  }

  /**
   * Returns a {@code Map} from each element {@code t} of {@code targets} to the shortest-path
   * distance from {@code source} to {@code t}.
   *
   * @param source the node from which the distance to each target is to be measured
   * @param targets the nodes to which the distance from the source is to be measured
   * @return {@code Map} from each element of {@code targets} to its distance from {@code source}
   */
  public Map<N, Number> getDistanceMap(N source, Collection<N> targets) {
    Preconditions.checkArgument(
        g.nodes().contains(source), "Specified source node %s  is not part of graph %s", source, g);
    Preconditions.checkArgument(
        targets.size() <= max_targets,
        "size of target set %d exceeds maximum number of targets allowed: %d",
        targets.size(),
        this.max_targets);

    Map<N, Number> distanceMap =
        singleSourceShortestPath(source, targets, Math.min(g.nodes().size(), max_targets));
    if (!cached) {
      reset(source);
    }

    return distanceMap;
  }

  /**
   * Returns a <code>LinkedHashMap</code> which maps each node in the graph (including the <code>
   * source</code> node) to its distance from the <code>source</code> node. The map's iterator will
   * return the elements in order of increasing distance from <code>source</code>.
   *
   * <p>The size of the map returned will be the number of nodes reachable from <code>source
   * </code>.
   *
   * @see #getDistanceMap(Object,int)
   * @see #getDistance(Object,Object)
   * @param source the node from which distances are measured
   * @return a mapping from each node in the graph to its distance from {@code source}
   */
  public Map<N, Number> getDistanceMap(N source) {
    return getDistanceMap(source, Math.min(g.nodes().size(), max_targets));
  }

  /**
   * Returns a <code>LinkedHashMap</code> which maps each of the closest <code>numDist</code> nodes
   * to the <code>source</code> node in the graph (including the <code>source</code> node) to its
   * distance from the <code>source</code> node. Throws an <code>
   * IllegalArgumentException</code> if <code>source</code> is not in this instance's graph, or if
   * <code>numDests</code> is either less than 1 or greater than the number of nodes in the graph.
   *
   * <p>The size of the map returned will be the smaller of <code>numDests</code> and the number of
   * nodes reachable from <code>source</code>.
   *
   * @see #getDistanceMap(Object)
   * @see #getDistance(Object,Object)
   * @param source the node from which distances are measured
   * @param numDests the number of nodes for which to measure distances
   * @return a mapping from the {@code numDests} nodes in the graph closest to {@code source}, to
   *     their distance from {@code source}
   */
  public LinkedHashMap<N, Number> getDistanceMap(N source, int numDests) {
    Preconditions.checkArgument(
        g.nodes().contains(source), "Specified source node %s  is not part of graph %s", source, g);
    Preconditions.checkArgument(
        numDests >= 1 && numDests <= g.nodes().size(),
        "number of destinations must be in [1, %d]",
        g.nodes().size());

    Preconditions.checkArgument(
        numDests <= max_targets,
        "size of target set %d exceeds maximum number of targets allowed: %d",
        numDests,
        this.max_targets);

    LinkedHashMap<N, Number> distanceMap = singleSourceShortestPath(source, null, numDests);

    if (!cached) {
      reset(source);
    }

    return distanceMap;
  }

  /**
   * Allows the user to specify the maximum distance that this instance will calculate. Any nodes
   * past this distance will effectively be unreachable from the source, in the sense that the
   * algorithm will not calculate the distance to any nodes which are farther away than this
   * distance. A negative value for <code>max_dist</code> will ensure that no further distances are
   * calculated.
   *
   * <p>This can be useful for limiting the amount of time and space used by this algorithm if the
   * graph is very large.
   *
   * <p>Note: if this instance has already calculated distances greater than <code>max_dist</code>,
   * and the results are cached, those results will still be valid and available; this limit applies
   * only to subsequent distance calculations.
   *
   * @param max_dist the maximum distance that this instance will calculate
   * @see #setMaxTargets(int)
   */
  public void setMaxDistance(double max_dist) {
    this.max_distance = max_dist;
    for (N v : sourceMap.keySet()) {
      SourceData sd = sourceMap.get(v);
      sd.reached_max =
          (this.max_distance <= sd.dist_reached) || (sd.distances.size() >= max_targets);
    }
  }

  /**
   * Allows the user to specify the maximum number of target nodes per source node for which this
   * instance will calculate distances. Once this threshold is reached, any further nodes will
   * effectively be unreachable from the source, in the sense that the algorithm will not calculate
   * the distance to any more nodes. A negative value for <code>max_targets</code> will ensure that
   * no further distances are calculated.
   *
   * <p>This can be useful for limiting the amount of time and space used by this algorithm if the
   * graph is very large.
   *
   * <p>Note: if this instance has already calculated distances to a greater number of targets than
   * <code>max_targets</code>, and the results are cached, those results will still be valid and
   * available; this limit applies only to subsequent distance calculations.
   *
   * @param max_targets the maximum number of targets for which this instance will calculate
   *     distances
   * @see #setMaxDistance(double)
   */
  public void setMaxTargets(int max_targets) {
    this.max_targets = max_targets;
    for (N v : sourceMap.keySet()) {
      SourceData sd = sourceMap.get(v);
      sd.reached_max =
          (this.max_distance <= sd.dist_reached) || (sd.distances.size() >= max_targets);
    }
  }

  /**
   * Clears all stored distances for this instance. Should be called whenever the graph is modified
   * (edge weights changed or edges added/removed). If the user knows that some currently calculated
   * distances are unaffected by a change, <code>reset(V)</code> may be appropriate instead.
   *
   * @see #reset(Object)
   */
  public void reset() {
    sourceMap = new HashMap<N, SourceData>();
  }

  /**
   * Specifies whether or not this instance of <code>DijkstraShortestPath</code> should cache its
   * results (final and partial) for future reference.
   *
   * @param enable <code>true</code> if the results are to be cached, and <code>false</code>
   *     otherwise
   */
  public void enableCaching(boolean enable) {
    this.cached = enable;
  }

  /**
   * Clears all stored distances for the specified source node <code>source</code>. Should be called
   * whenever the stored distances from this node are invalidated by changes to the graph.
   *
   * @param source the node for which stored distances should be cleared
   * @see #reset()
   */
  public void reset(N source) {
    sourceMap.put(source, null);
  }

  /** Compares according to distances, so that the BinaryHeap knows how to order the tree. */
  protected static class NodeComparator<N> implements Comparator<N> {
    private Map<N, Number> distances;

    protected NodeComparator(Map<N, Number> distances) {
      this.distances = distances;
    }

    public int compare(N o1, N o2) {
      return ((Double) distances.get(o1)).compareTo((Double) distances.get(o2));
    }
  }

  /**
   * For a given source node, holds the estimated and final distances, tentative and final
   * assignments of incoming edges on the shortest path from the source node, and a priority queue
   * (ordered by estimated distance) of the nodes for which distances are unknown.
   *
   * @author Joshua O'Madadhain
   */
  protected class SourceData {
    protected LinkedHashMap<N, Number> distances;
    protected Map<N, Number> estimatedDistances;
    protected MapBinaryHeap<N> unknownNodes;
    protected boolean reached_max = false;
    protected double dist_reached = 0;

    protected SourceData(N source) {
      distances = new LinkedHashMap<N, Number>();
      estimatedDistances = new HashMap<N, Number>();
      unknownNodes = new MapBinaryHeap<N>(new NodeComparator<N>(estimatedDistances));

      sourceMap.put(source, this);

      // initialize priority queue
      estimatedDistances.put(source, new Double(0)); // distance from source to itself is 0
      unknownNodes.add(source);
      reached_max = false;
      dist_reached = 0;
    }

    protected Map.Entry<N, Number> getNextNode() {
      N v = unknownNodes.remove();
      Double dist = (Double) estimatedDistances.remove(v);
      distances.put(v, dist);
      return new SimpleImmutableEntry<N, Number>(v, dist);
    }

    protected void update(N dest, E tentative_edge, double new_dist) {
      estimatedDistances.put(dest, new_dist);
      unknownNodes.update(dest);
    }

    protected void createRecord(N w, E e, double new_dist) {
      estimatedDistances.put(w, new_dist);
      unknownNodes.add(w);
    }

    protected void restoreNode(N v, double dist) {
      estimatedDistances.put(v, dist);
      unknownNodes.add(v);
      distances.remove(v);
    }
  }
}
