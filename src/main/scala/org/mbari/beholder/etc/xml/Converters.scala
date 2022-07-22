/*
 * Copyright (c) Monterey Bay Aquarium Research Institute 2021
 *
 * beholder code is non-public software. Unauthorized copying of this file,
 * via any medium is strictly prohibited. Proprietary and confidential. 
 */

package org.mbari.beholder.etc.xml

import org.w3c.dom.NodeList
import org.w3c.dom.Node

given Conversion[NodeList, List[Node]] with
  def apply(nodeList: NodeList): List[Node] =
    (0 until nodeList.getLength).map(nodeList.item).toList
