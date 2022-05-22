package de.fhkiel.ki.examples.gui.withAi;

import de.fhkiel.ki.ai.CathedralAI;
import de.fhkiel.ki.cathedral.*;

import java.util.*;

public class NewAi implements CathedralAI {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";
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
    public void stopAI() {
    }

    @Override
    public Placement takeTurn(Game game) {
        // Fetch the start time of the function
        long start = System.nanoTime();
        System.out.println(ANSI_GREEN + "[LOG] Capture percepts" + ANSI_RESET);
        // Create copy of the current game state
        // Get the Percept:
        // ---------------
        Game copy = game.copy();
        Set<PlacementData> possibles;
        // Fetch the current player
        Color player = copy.getCurrentPlayer();
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

        Set<PlacementData> finalPlacements = new HashSet<>();
        // Calculate all the position that
        for (Position pos : opponentPositions) {
            checkPlacementData(pos.x(), pos.y(), game, player,finalPlacements);
        }

        System.out.println(ANSI_GREEN + "[LOG] Calculate optimal position" + ANSI_RESET);
        // If there are no more overlapping positions, then take the optimal data form the player
        if (opponentPositions.size() == 0) {
            finalPlacements = possibles;
        }
        // Calculate the data on the
        for (PlacementData dataPlay : finalPlacements) {
            // Get all opponents objects at the position of this player position and calculate the average position data
            dataPlay.prevent = opponentsPlacements.stream().
                    filter(data -> data.placement.position().equals(dataPlay.placement.position())).
                    mapToDouble(PlacementData::getPositions).
                    average().
                    orElse(0.0);
        }
        // Add all possibles to the finals that have just been calculated
        finalPlacements.addAll(possibles);
        // Apply rule-set on possible turns:
        // ---------------------------------
        int highest = 0;
        PlacementData bestPlacement = null;
        // abort, as there are no placements possible!
        if (finalPlacements.isEmpty()) {
            skip(game);
            return null;
        }
        // Select the placement that has the highest point value
        for (PlacementData place : finalPlacements) {
            if (place.getScore() >= highest) {
                highest = place.getScore();
                bestPlacement = place;
            }
        }
        System.out.println(bestPlacement);
        printTime(start, System.nanoTime());
        System.out.println(ANSI_GREEN + "[LOG] Done" + ANSI_RESET);

        // A placement is no longer possible!
        if (null == bestPlacement) {
            skip(game);
            return null;
        }
        return bestPlacement.placement;
    }


    // Utility Methods
    // ---------------

    /**
     * Print the time that this function took to execute and print it in a more pretty way.
     *
     * @param start the start time point of execution
     * @param end   the end time point of execution
     */
    private void printTime(long start, long end) {
        long duration = (end - start) / 1000000; // Duration in seconds
        String color;
        // Function took no longer than 1000 milliseconds/1 second
        if (duration < 1000) {
            color = ANSI_GREEN;
        }
        // Function took no longer than 10000 milliseconds/29 seconds
        else if (duration < 10000) {
            color = ANSI_YELLOW;
        } else if (duration < 29000) {
            color = ANSI_CYAN;
        }
        // Function took too long
        else {
            color = ANSI_RED;
        }
        System.out.println(color + "[LOG] The function took " + duration + " milliseconds, or " + (duration / 1000.0) + " Seconds!" + ANSI_RESET);
    }

    /**
     * Call this method in order to skip to the next turn of the next player
     *
     * @param game the game to skip for
     */
    private void skip(Game game) {
        System.out.println(ANSI_GREEN + "[LOG] Skip, as there are no valid placements no more!" + ANSI_RESET);
        game.forfeitTurn();
    }

    /**
     * Calculate the placables that can be put onto the field for the current player
     *
     * @param game the game we are working on
     * @return the amoung of possible placables on this board for the current player
     */
    private Set<PlacementData> getPlacements(Game game) {
        Color player = game.getCurrentPlayer();
        Set<PlacementData> possibles = new HashSet<>();
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                // if (field[x][y].getId() == (player.getId() + 1) || field[x][y].getId() == Color.None.getId()) {
                checkPlacementData(x, y, game, player, possibles);
            }
        }
        return possibles;
    }

    /**
     * Check the current position and return all blocks that fit there
     *
     * @param x           the x coordinate
     * @param y           the y coordinate
     * @param game        the game to test for
     * @param player      the player that is currently active
     * @param data        the placement data to write to on success
     */
    private void checkPlacementData(int x, int y, Game game, Color player, Set<PlacementData> data) {
        for (Building building : game.getPlacableBuildings()) {
            // Test all turnables of the building
            for (Direction direction : building.getTurnable().getPossibleDirections()) {
                Placement possPlacement = new Placement(x, y, direction, building);
                // Take a turn using the "fast" method without checking the regions
                if (game.takeTurn(possPlacement, true)) {
                    // Check the number of regions that this placement would provide
                    game.undoLastTurn();
                    // Fetch the regions that are placed at this point
                    Set<Position> prevRegions = checkRegions(game.getBoard().getField(), player);
                    // Check the regions
                    game.takeTurn(possPlacement, false);
                    Set<Position> newRegions = checkRegions(game.getBoard().getField(), player);
                    // Get the amount of regions that have been added
                    int newRegionSize = newRegions.size() - prevRegions.size();

                    // We can take a turn, thus we add it to the "possible" set
                    data.add(new PlacementData(possPlacement, newRegionSize));
                    game.undoLastTurn();
                }
            }
        }
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
     * Print the board to the console in a more stylish fashion
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
}

/**
 * Data class that contains all the data for a placement that are relevant for it's evaluation
 */
class PlacementData {
    // The placement
    public Placement placement;
    // the amount of positions in the region this placement would get
    public int positions;
    // The amount of captures from the opponent this placement would prevent
    public double prevent;

    PlacementData(Placement placement, int positions) {
        this.placement = placement;
        this.positions = positions;
        this.prevent = 0;
    }

    @Override
    public String toString() {
        return "Placement: "
                + this.placement.toString()
                + "\nRegions: "
                + getPositions()
                + "\nScore: "
                + getScore()
                + "\nPrevents: "
                + getPrevent()
                + "\n=> Score: " + getScore();
    }

    /**
     * Here you can scale how much the prevention value matters for these calculations
     *
     * @return the scale of the prevention value
     */
    public int preventValue() {
        return (int) this.prevent / 2;
    }

    public int getScore() {
        return this.placement.building().score() + this.positions + preventValue();
    }

    public int getPositions() {
        return this.positions;
    }

    public double getPrevent() {
        return prevent;
    }
}
