package org.openstreetmap.josm.plugins.lanes;

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