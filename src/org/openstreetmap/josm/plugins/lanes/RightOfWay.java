package org.openstreetmap.josm.plugins.lanes;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Pair;

import java.util.*;

/**
 * Represents the right of way through an "intersection". That is, non-intersecting flows of traffic that always have
 * right of way over other traffic. Lane markings are expected to continue unbroken through the right of way.
 *
 * Currently only supports
 * - one-way roads that fork (from 1 way into many) or merges (from many ways into 1), or
 * - two-way roads that fork into one-way ways (where a dividing barrier starts),
 * but could include a + intersection with stop or give way signs on two opposite roads, and the like.
 */
class RightOfWay {
    public Node _node;

    /** Inward pointing vector of the road that determines the position of the other lanes. */
    public final WayVector mainRoad;

    /** Maps directed lanes on the main road to matching directed lanes of outward pointing connected way vectors. */
    public final Map<Integer, LaneRef> innerLaneToConnectedLane = new HashMap<>();

    public RightOfWay(WayVector mainRoadInward) {
        this._node = mainRoadInward.getParent().getNode(mainRoadInward.getTo());
        this.mainRoad = mainRoadInward;
    }

    public void addConnectedWay(WayVector connectedRoadOutward, int innermostMainRoadDirectedLane) {
        innerLaneToConnectedLane.put(innermostMainRoadDirectedLane, new LaneRef(connectedRoadOutward, connectedRoadOutward.isForward() ? 1 : -1));
    }

    /** Get the innermost matching lanes, first on the main road, second on the provided road. */
    public Pair<LaneRef, LaneRef> getWayConnection(WayVector wayVec) {
        for (int mainLane : innerLaneToConnectedLane.keySet()) {
            LaneRef connectedLane = innerLaneToConnectedLane.get(mainLane);
            if (connectedLane.wayVec.equals(wayVec)) {
                return new Pair<>(new LaneRef(mainRoad,  mainLane), connectedLane);
            }
        }
        return null;
    }

    /** Get the lane that matches the provided lane. */
    public LaneRef getConnection(LaneRef laneRef) {
        if (laneRef.wayVec.equals(mainRoad)) {
            if (laneRef.directedLane == 0) return innerLaneToConnectedLane.get(0);
            // Currently assuming no lane merges and only storing the innermost lane of each connected way.
            else for (int l = laneRef.directedLane; l != 0; l -= Integer.signum(laneRef.directedLane)) {
                if (innerLaneToConnectedLane.containsKey(l)) {
                    return innerLaneToConnectedLane.get(l).getAdjacent(laneRef.directedLane - l);
                }
            }
            return null;
        }

        else for (int mainLane : innerLaneToConnectedLane.keySet()) {
            LaneRef connected = innerLaneToConnectedLane.get(mainLane);
            if (connected.wayVec.equals(laneRef.wayVec)) {
                return new LaneRef(mainRoad,  mainLane + (laneRef.directedLane - connected.directedLane));
            }
        }

        return null;
    }

    /**
     * Calculates the placement offset distance for the end of a road connecting to the main road.
     * This is how far the node would want to move to be correctly positioned for this way (given its placement), based
     * on its connectivity to the main road, and the placement of the main road.
     *
     * When a road splits with its lanes diverging, the intersection node may lay outside of the road area for some of
     * the connected ways. For this reason, we calculate this offset based on the placement and widths of the main road.
     */
    public double getPlacementOffset(WayVector connectedWayVector, LaneMappingMode _m) {
        try {
            MarkedRoadRenderer connectedRoad = (MarkedRoadRenderer) _m.wayIdToRSR.get(connectedWayVector.getParent().getUniqueId());
            String placement = connectedRoad.getPlacementTag(connectedWayVector.getFrom() == 0);
            if (placement == null) {
                // TODO fix for right hand drive ways
                int lanes = connectedRoad.getLaneCount(1);
                placement = (lanes % 2 == 1) ? "middle_of:" + (lanes / 2 + 1) + "f" : "right_of:" + (lanes / 2) + "f";
                // FIXME placement is actually in the center of the road, (which is not here if the lane widths differ).
            }
            String[] placementBits = placement.substring(0, placement.length() - 1).split(":");
            int directedPlacementLane = connectedRoad.calculateDirectedLane(
                    Integer.parseInt(placementBits[1]),
                    placement.charAt(placement.length() - 1) == 'f');
            LaneRef placementLane = new LaneRef(connectedWayVector, directedPlacementLane * (connectedWayVector.isForward() ? 1 : -1));
            LaneRef mainLane = getConnection(placementLane);

            MarkedRoadRenderer mainRoad = (MarkedRoadRenderer) _m.wayIdToRSR.get(mainLane.wayVec.getParent().getUniqueId());
            String placementOnMainRoad = placementBits[0] + ":" +
                    mainRoad.calculateLaneNumber(mainLane.directedLane, mainLane.wayVec.isForward()).a +
                    (!mainLane.wayVec.isForward() ^ mainLane.directedLane < 0 ? "b" : "f");

            return mainRoad.getPlacementOffsetFrom(placementOnMainRoad, mainLane.wayVec.getTo());
        } catch (Exception ignored) {}

        return 0;
    }

    /**
     * Calculate right of way for a single node intersection.
     *
     * Currently only supports points where the number of lanes remains the same but they diverge on one side.th
     */
    static RightOfWay create(Node node, LaneMappingMode m) {
        try {
            List<WayVector> wayVectors = Utils.getWaysFromNode(node, m);

            // Count lanes to determine if this is a basic lane divergence.
            // TODO: Check connectivity relations if they are present.
            List<Integer> inWays = new ArrayList<>();
            List<Integer> outWays = new ArrayList<>();
            List<Integer> inOutWays = new ArrayList<>();
            int inLanes = 0;
            int outLanes = 0;
            for (int i = 0; i < wayVectors.size(); i++) {
                WayVector wv = wayVectors.get(i);
                Way w = wv.getParent();
                MarkedRoadRenderer mrr = (MarkedRoadRenderer) m.wayIdToRSR.get(w.getUniqueId());
                if (mrr.getLaneCount(0) != 0) return null; // Don't support both way lanes
                int in = mrr.getLaneCount(wv.isForward() ? -1 : 1);
                int out = mrr.getLaneCount(wv.isForward() ? 1 : -1);
                if (in > 0 && out > 0) {
                    inOutWays.add(i);
                } else if (in > 0) {
                    inWays.add(i);
                    inLanes += in;
                } else if (out > 0) {
                    outWays.add(i);
                    outLanes += out;
                }
            }
            if (inLanes == 0 || outLanes == 0) return null;

            if (inLanes == outLanes && inOutWays.size() == 0 && (inWays.size() == 1 || outWays.size() == 1)) {
                // One way fork or merge.
                int mainI;
                List<Integer> divergingWays;
                if (inWays.size() == 1) {
                    mainI = inWays.get(0);
                    divergingWays = outWays;
                } else {
                    mainI = outWays.get(0);
                    divergingWays = inWays;
                }

                RightOfWay output = new RightOfWay(wayVectors.get(mainI).reverse());
                int mainLanes = inLanes;

                // Default connectivity: Allocate the diverged ways to lanes to those in the main road in clockwise order.
                int lanesAllocated = 0;
                for (int i = 1; i < wayVectors.size(); i++) {
                    int j = (i + mainI) % wayVectors.size();
                    if (divergingWays.contains(j)) {
                        WayVector connectedWV = wayVectors.get(j);
                        MarkedRoadRenderer connectedMRR = (MarkedRoadRenderer) m.wayIdToRSR.get(connectedWV.getParent().getUniqueId());
                        int connectedLanes = connectedMRR.getLaneCount(1);
                        int lane = lanesAllocated + 1;
                        if (!Utils.isRightHand(connectedWV.getParent()) ^ !output.mainRoad.isForward()) {
                            // Allocating lanes outside in.
                            lane = mainLanes - lane + 1 - connectedLanes + 1;
                        }
                        output.addConnectedWay(connectedWV, lane * (output.mainRoad.isForward() ? 1 : -1));
                        lanesAllocated += connectedLanes;
                    }
                }
                if (lanesAllocated != inLanes) System.out.println("Mistake in associating 1-in-many-out lanes!");
                return output;
            }

            // Same thing but with two way roads.
            if (inOutWays.size() == 1) {
                int mainI = inOutWays.get(0);
                WayVector mainWv = wayVectors.get(mainI).reverse();
                MarkedRoadRenderer mrr = (MarkedRoadRenderer) m.wayIdToRSR.get(mainWv.getParent().getUniqueId());

                if (mrr.getLaneCount(mainWv.isForward() ? 1 : -1) != outLanes ||
                        mrr.getLaneCount(mainWv.isForward() ? -1 : 1) != inLanes) {
                    return null; // TODO support this case, because it is easy to allocate lanes starting at the center line.
                }

                RightOfWay output = new RightOfWay(mainWv);
                boolean isRightHand = Utils.isRightHand(mainWv.getParent());
                int leftHandLanes = isRightHand ? inLanes : outLanes;

                // Default connectivity: Allocate the diverged ways to lanes to those in the main road in clockwise order.
                int lanesAllocated = 0;
                for (int i = 1; i < wayVectors.size(); i++) {
                    int j = (i + mainI) % wayVectors.size();

                    if (lanesAllocated < leftHandLanes) {
                        WayVector connectedWv = wayVectors.get(j);
                        MarkedRoadRenderer connectedMrr = (MarkedRoadRenderer) m.wayIdToRSR.get(connectedWv.getParent().getUniqueId());
                        int connectedLaneDir = (isRightHand ? -1 : 1) * (connectedWv.isForward() ? 1 : -1);
                        int connectedLanes = connectedMrr.getLaneCount(connectedLaneDir);

                        // Allocating lanes outside in.
                        int lane = lanesAllocated + 1;
                        lane = leftHandLanes - lane + 1 - connectedLanes + 1;
                        output.addConnectedWay(connectedWv, lane * (isRightHand ? -1 : 1));
                        lanesAllocated += connectedLanes;

                        if (connectedMrr.getLaneCount(-1 * connectedLaneDir) > 0) {
                            // TODO these lanes can be allocated to the other direction. i--; continue;
                            return null;
                        }
                    } else if (lanesAllocated >= leftHandLanes) {
                        WayVector connectedWv = wayVectors.get(j);
                        MarkedRoadRenderer connectedMrr = (MarkedRoadRenderer) m.wayIdToRSR.get(connectedWv.getParent().getUniqueId());
                        int connectedLaneDir = (isRightHand ? -1 : 1) * (connectedWv.isForward() ? -1 : 1);
                        int connectedLanes = connectedMrr.getLaneCount(connectedLaneDir);

                        // Allocate lanes inside out.
                        int lane = lanesAllocated - leftHandLanes + 1;
                        output.addConnectedWay(connectedWv, lane * (isRightHand ? 1 : -1));
                        lanesAllocated += connectedLanes;

                        if (connectedMrr.getLaneCount(-1 * connectedLaneDir) > 0) {
                            return null; // Found more left-hand lanes that don't match up.
                        }
                    }
                }
                // TODO check that the angle between the first in way and last out way is smaller than ~90 degrees so we can assume no connectivity between them.
                if (lanesAllocated != inLanes + outLanes) System.out.println("Mistake in associating road split lanes!");
                return output;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

class LaneRef {
    public final WayVector wayVec;
    /** The lane counted from the middle of the road, forward lanes in the direction of the wayVec positive, backwards lanes negative. */
    public final int directedLane;

    public LaneRef(WayVector wayVec, int directedLane) {
        this.wayVec = wayVec;
        this.directedLane = directedLane;
    }

    public LaneRef getInsideLane() {
        return new LaneRef(wayVec, directedLane - Integer.signum(directedLane));
    }
    public LaneRef getOutsideLane() {
        return new LaneRef(wayVec, directedLane + Integer.signum(directedLane));
    }
    public LaneRef getAdjacent(int offset) {
        return new LaneRef(wayVec, directedLane + offset);
    }

    public boolean equals(LaneRef other) {
        return wayVec.equals(other.wayVec) && directedLane == other.directedLane;
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LaneRef)) return false;
        return equals((LaneRef) other);
    }

    public String toString() {
        return "Lane " + (directedLane >= 0 ? "+" : "") + directedLane + " looking " +
                (wayVec.isForward() ? "forward" : "backwards") + " along Way " + wayVec.getParent().getUniqueId();
    }
}