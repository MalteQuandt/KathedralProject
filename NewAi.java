package de.fhkiel.ki.examples.gui.withAi;

import de.fhkiel.ki.ai.CathedralAI;
import de.fhkiel.ki.cathedral.*;

import java.util.*;

public class NewAi implements CathedralAI {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";

    @Override
    public String name() {
        return "Team ECHO";
    }

    @Override
    public void init(Game game) {

    }

    @Override
    public Placement takeTurn(Game game) {
        System.out.println(ANSI_GREEN + "[LOG] Capture percepts" + ANSI_RESET);
        // Create copy of the current game state
        // Get the Percept:
        // ---------------
        Game copy = game.copy();
        Set<PlacementData> possibles = new HashSet<>();
        // Fetch the current player
        Color player = copy.getCurrentPlayer();
        // Fetch the current board state
        Color[][] field = copy.getBoard().getField();
        // Check if the opponents next placement would result in a captured region
        // ------------------------------------------------------------------------

        // 1. Assume the position of the opponent
        Game opGame = game.copy();
        opGame.forfeitTurn();
        // 2. Fetch the possible movements of the opponent after this turn
        Set<PlacementData> opponentsPlacements = getPlacements(opGame);
        // 3. Filter for the positions that would capture regions
        opponentsPlacements.removeIf(placementData -> placementData.positions == 0);

        // Check the positions that are possible right now:
        // ------------------------------------------------
        System.out.println(ANSI_GREEN + "[LOG] Calculate possible positions" + ANSI_RESET);
        // Test all buildings
        // Test every position on the board and filter any turn that is not possible
        // Iterate over the board positions
        possibles = getPlacements(game);

        // Create the set union of the possible placements that the current player can make and the region-capturing
        // placements our opponent could make
        Set<Position> opponentPositions = new HashSet<>(opponentsPlacements.size());
        for (PlacementData data : opponentsPlacements) {
            opponentPositions.add(new Position(data.placement.x(), data.placement.y()));
        }
        Set<Position> playerPositions = new HashSet<>(possibles.size());
        for (PlacementData data : possibles) {
            playerPositions.add(new Position(data.placement.x(), data.placement.y()));
        }
        // Get the overlapping positions of the placements that both players took this turn
        opponentPositions.retainAll(playerPositions);



        System.out.println(ANSI_GREEN + "[LOG] Calculate optimal position" + ANSI_RESET);
        // Apply rule-set on possible turns:
        // ---------------------------------
        int highest = 0;
        PlacementData bestPlacement = null;
        // abort, as there are no placements possible!
        if (possibles.isEmpty()) {
            System.out.println(ANSI_GREEN + "[LOG] Done (No Placements possible!)" + ANSI_RESET);
            return null;
        }
        // Select the placement that has the highest point value
        for (PlacementData place : possibles) {
            if (place.getScore() >= highest) {
                highest = place.placement.building().score() + place.positions;
                bestPlacement = place;
            }
        }
        System.out.println(bestPlacement);
        System.out.println(ANSI_GREEN + "[LOG] Done" + ANSI_RESET);
        assert bestPlacement != null;
        return bestPlacement.placement;
    }

    @Override
    public void stopAI() {

    }

    // Utility Methods
    // ---------------
    private Set<PlacementData> getPlacements(Game game) {
        Color player = game.getCurrentPlayer();
        Color[][] field = game.getBoard().getField();

        Set<Position> prevRegions = checkRegions(field, player);

        Set<PlacementData> possibles = new HashSet<>();
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                // if (field[x][y].getId() == (player.getId() + 1) || field[x][y].getId() == Color.None.getId()) {
                for (Building building : game.getPlacableBuildings()) {
                    // Test all turnables of the building
                    for (Direction direction : building.getTurnable().getPossibleDirections()) {
                        Placement possPlacement = new Placement(x, y, direction, building);
                        // Take a turn using the "fast" method without checking the regions
                        if (game.takeTurn(possPlacement, true)) {
                            // Check the number of regions that this placement would provide
                            game.undoLastTurn();
                            // Fetch the regions that are placed at this point
                            prevRegions = checkRegions(game.getBoard().getField(), player);
                            // Check the regions
                            game.takeTurn(possPlacement, false);
                            Set<Position> newRegions = checkRegions(game.getBoard().getField(), player);
                            // Get the amount of regions that have been added
                            int newRegionSize = newRegions.size() - prevRegions.size();

                            // We can take a turn, thus we add it to the "possible" set
                            possibles.add(new PlacementData(possPlacement, newRegionSize));
                            game.undoLastTurn();
                        }
                    }
                }
            }

        }
        return possibles;
    }

    /**
     * Check, if there are regions captured for this player
     *
     * @param field  the field to check
     * @param player the player to check for
     */
    private Set<Position> checkRegions(Color[][] field, Color player) {
        Set<Position> newPositions = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                Position curr = new Position(i, j);
                // If the field is a capture of the current player, and if the field is not yet captured
                if (field[i][j].getId() == (player.getId() + 1)) {
                    // Add that to the oldPosit
                    newPositions.add(curr);
                }
            }
        }
        return newPositions;
    }

    /**
     * Print the board to the console
     *
     * @param board the board to print
     * @author malte quandt
     */
    private void printBoard(final Color[][] board) {
        for (int i = 0; i < 10; i++) {
            System.out.print("|");
            for (int j = 0; j < 10; j++) {
                // Fetch the current field's data
                Color currentField = board[i][j];
                // Do nothing for an empty field
                switch (currentField) {
                    case White -> System.out.print(ANSI_WHITE_BACKGROUND + "   " + ANSI_RESET);

                    case White_Owned -> System.out.print(ANSI_WHITE_BACKGROUND + " O " + ANSI_RESET);

                    case Black -> System.out.print(ANSI_BLACK_BACKGROUND + "   " + ANSI_RESET);

                    case Black_Owned -> System.out.print(ANSI_BLACK_BACKGROUND + " O " + ANSI_RESET);

                    case Blue -> System.out.print(ANSI_BLUE_BACKGROUND + "   " + ANSI_RESET);

                    case None -> System.out.print("   ");

                    default -> System.out.print(currentField + "   ");

                }
            }
            System.out.print("|");
            System.out.println();
        }
    }

    /**
     * Calculate if there are newly captured regions
     *
     * @param prevcaptures the previously captured regions
     * @param board        the board to check against
     * @param player
     * @return the new regions that have been captured in this calculation
     */
    private Set<Position> checkNewCaptures(Set<Position> prevcaptures, Color[][] board, Color player) {
        Set<Position> newCapturedRegions = new HashSet<>(0);
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                // A region of the current player
                Position curr = new Position(i, j);
                if (board[i][j].getId() == (player.getId() + 1)) {
                    newCapturedRegions.add(curr);
                }
            }
        }
        return newCapturedRegions;
    }
}

/**
 * Data class that contains all the data for a placement that are relevant for it's evaluation
 */
class PlacementData {
    // The placement
    public Placement placement;
    // the amount of positions in the region this placement would get
    public int positions;

    PlacementData(Placement placement, int positions) {
        this.placement = placement;
        this.positions = positions;
    }

    @Override
    public String toString() {
        return this.placement.toString() + "\nRegions: " + this.positions + "\nScore: " + (this.placement.building().score() + this.positions);
    }

    public int getScore() {
        return this.placement.building().score() + this.positions;
    }
}
