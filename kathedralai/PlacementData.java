package de.fhkiel.ki.examples.gui.withAi.kathedralai;

import de.fhkiel.ki.cathedral.Placement;

/**
 * Wrapper class around placement data that provides some additional information about the placement
 */
class PlacementData {
    // Instance variables
    // ------------------

    // The placement in question
    private final Placement placement;
    // How many regions this placement would capture
    private final Integer capture;
    // Score that this placement would give to this player, in comparison to the other player
    private final Integer playerScoreDelta;

    // Constructors
    // ------------
    public PlacementData(Placement placement, int capture, int playerScoreDelta) {
        this.placement = placement;
        this.capture = capture;
        this.playerScoreDelta = playerScoreDelta;
    }

    // Instance methods
    // ----------------
    @Override
    public String toString() {
        return "Placement: "
                + this.placement
                + "\nPlayerScoreDelta: "
                + this.playerScoreDelta
                + "\nCaptures:"
                + this.capture
                + "\n";
    }

    // Getter
    // ------
    public Placement getPlacement() {
        return placement;
    }

    public Integer getPlayerScoreDelta() {
        return playerScoreDelta;
    }

    public Integer getCapture() {
        return capture;
    }
}