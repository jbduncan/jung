/*
 * Copyright (c) 2005, The JUNG Authors
 * All rights reserved.
 *
 * This software is open-source under the BSD license; see either "license.txt"
 * or https://github.com/jrtom/jung/blob/master/LICENSE for a description.
 *
 * Created on Aug 23, 2005
 */
package edu.uci.ics.jung.visualization.subLayout;

import com.google.common.collect.Iterables;
import com.google.common.collect.TreeTraverser;
import com.google.common.graph.EndpointPair;
import edu.uci.ics.jung.graph.CTreeNetwork;
import edu.uci.ics.jung.graph.MutableCTreeNetwork;
import edu.uci.ics.jung.graph.util.TreeUtils;
import java.util.Optional;

public class TreeCollapser {

  /**
   * Replaces the subtree of {@code tree} rooted at {@code subRoot} with a node representing that
   * subtree.
   *
   * @param tree the tree whose subtree is to be collapsed
   * @param subRoot the root of the subtree to be collapsed
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static MutableCTreeNetwork collapse(MutableCTreeNetwork tree, Object subRoot) {
    // get the subtree rooted at subRoot
    MutableCTreeNetwork subTree = TreeUtils.getSubTree(tree, subRoot);
    Optional parent = tree.predecessor(subRoot);
    if (parent.isPresent()) {
      // subRoot has a parent, so attach its parent to subTree in its place
      Object parentEdge = tree.inEdges(subRoot).iterator().next(); // THERE CAN BE ONLY ONE
      tree.removeNode(subRoot);
      tree.addEdge(parent.get(), subTree, parentEdge);
    } else {
      // subRoot is the root of tree
      tree.removeNode(subRoot);
      tree.addNode(subTree);
    }
    return subTree;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static void expand(MutableCTreeNetwork tree, CTreeNetwork subTree) {
    Optional parent = tree.predecessor(subTree);
    Object parentEdge =
        parent.isPresent()
            ? tree.inEdges(subTree).iterator().next() // THERE CAN BE ONLY ONE
            : null;
    tree.removeNode(subTree);
    if (parent.isPresent()) {
      TreeUtils.addSubTree(tree, subTree, parent.get(), parentEdge);
    } else {
      // tree is empty, so just copy all of subTree into tree
      copyInto(tree, subTree);
    }
  }

  private static <N, E> void copyInto(MutableCTreeNetwork<N, E> to, CTreeNetwork<N, E> from) {
    from.root()
        .ifPresent(
            root -> {
              to.addNode(root);
              // Connect each successively deeper node to its parent
              Iterable<N> insertionOrder =
                  TreeTraverser.using(from::successors)
                      .preOrderTraversal(root)
                      .skip(1); // skip the root itself
              for (N current : insertionOrder) {
                E inEdge = Iterables.getOnlyElement(from.inEdges(current));
                EndpointPair<N> endpointPair = from.incidentNodes(inEdge);
                to.addEdge(endpointPair.nodeU(), endpointPair.nodeV(), inEdge);
              }
            });
  }
}
