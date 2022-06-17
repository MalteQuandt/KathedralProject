package de.fhkiel.ki.examples.gui.withAi.kathedralai;

import de.fhkiel.ki.ai.CathedralAI;
import de.fhkiel.ki.cathedral.*;
import de.fhkiel.ki.examples.gui.withAi.NewAi;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class EchoAI implements CathedralAI {

    /* Colors */
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";

    // How many processors the current system has available
    private Integer processors;

    @Override
    public String name() {
        return "Team Echo V2";
    }

    @Override
    public void init(Game game) {
        System.out.println(ANSI_GREEN + "[LOG] Start the AI" + ANSI_RESET);
        // Fetch the available processors
        processors = Runtime.getRuntime().availableProcessors();
    }

    @Override
    public Placement takeTurn(Game game) {
        // 0. Set up the required data
        Game copy = game.copy();
        // 1. Calculate the possible positions for the current player
        Set<PlacementData> possibles = getPlacements(copy, 0, 10, true);
        return null;
    }

    @Override
    public void stopAI() {
        System.out.println(ANSI_GREEN + "[LOG] Stop the AI" + ANSI_RESET);
    }

    /**
     * Calculate the placement that would capture the most
     * regions/capture the most positions for the current player
     *
     * @param game the game to work on
     * @return the placement that has the highest value for the current player
     */
    public Placement aggressive(Game game) {
        return null;
    }

    /**
     * Calculate the placement that would prevent the opponent from capturing the most placements/regions
     *
     * @param game the game to work on
     * @return the placement that has the lowest value for the opponent
     */
    public Placement defensive(Game game) {
        return null;
    }

    /**
     * Calculate the placement that has the best average value for the player, meaning that would both
     * capture the most regions/positions and prevent the opponent from doing that.
     *
     * @param game the game to work on
     * @return the placement with the highest average value
     */
    public Placement average(Game game) {
        return null;
    }

    // Helper methods
    // --------------

    /**
     * Print the time that this function took to execute and print it in a more pretty way.
     *
     * @param start the start time point of execution
     * @param end   the end time point of execution
     */
    private void printTime(long start, long end) {
        long duration = (end - start) / 1000000; // Duration in milliseconds
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
     * Print the skip message to the screen
     */
    private void skipMessage() {
        System.out.println(ANSI_GREEN + "[LOG] Skip, as there are no valid placements no more!" + ANSI_RESET);
    }

    /**
     * calculate the score for a given player.
     *
     * @param game   the game to get the score from
     * @param player the player for which to get the score
     * @return the score of the player at a given point in time in the given game instance
     */
    private Integer getScore(Game game, Color player) {
        Object a = game.score().get(player);
        if (a != null) {
            return (int) a;
        } else {
            return 47;
        }
    }

    /**
     * Check, if there are regions captured for this player
     *
     * @param field  the field to check
     * @param player the player to check for
     * @return the regions captured by the current player
     */
    private static Set<Position> checkRegions(Color[][] field, Color player) {
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
                    case White -> System.out.print(ANSI_WHITE_BACKGROUND + "  " + ANSI_RESET);
                    case White_Owned -> System.out.print(ANSI_WHITE_BACKGROUND + "00" + ANSI_RESET);
                    case Black -> System.out.print(ANSI_BLACK_BACKGROUND + "  " + ANSI_RESET);
                    case Black_Owned -> System.out.print(ANSI_BLACK_BACKGROUND + "00" + ANSI_RESET);
                    case Blue -> System.out.print(ANSI_BLUE_BACKGROUND + "  " + ANSI_RESET);
                    case None -> System.out.print("  ");
                    default -> System.out.print(currentField + "  ");
                }
            }
            System.out.print("|");
            System.out.println();
        }
    }

    /**
     * Calculate the placables that can be put onto the field for the current player
     *
     * @param game            the game we are working on
     * @param from            the start of the x coordinates to check
     * @param to              the end of the x coordinates to check
     * @param checkPlacements if the algorithms should check the number of placements
     * @return the among of possible placables on this board for the current player
     */
    private Set<PlacementData> getPlacements(Game game, int from, int to, boolean checkPlacements) {
        Set<PlacementData> possibles = new HashSet<>();
        for (int x = from; x < to; x++) {
            for (int y = 0; y < 10; y++) {
                checkPlacementData(x, y, game, possibles, checkPlacements);
            }
        }
        return possibles;
    }

    /**
     * Check the current position and return all blocks that fit there
     *
     * @param x               the x coordinate to check for a placement
     * @param y               the y coordinate to check for a placement
     * @param game            the game to test for
     * @param data            the placement data to write to on success
     * @param checkPlacements if the algorithm should check the number of placements
     */
    private void checkPlacementData(int x, int y, Game game, Set<PlacementData> data, boolean checkPlacements) {
        // Fetch the current player
        Color player = game.getCurrentPlayer();
        int oldScore = getScore(game, player);
        // Check the regions
        int placementsStart = checkRegions(game.getBoard().getField(), player).size();
        // Check, if this current field is applicable for checking it out, specifically if and only if the field belongs
        // to the current player or to no one at all
        for (Building building : game.getPlacableBuildings()) {
            // Test all turnables of the building
            for (Direction direction : building.getTurnable().getPossibleDirections()) {
                Placement possPlacement = new Placement(x, y, direction, building);
                int deltaScore;
                int deltaRegions;
                // Take a turn using the "fast" method, meaning without checking the regions
                if (game.takeTurn(possPlacement, true)) {
                    // If the placements should be checked, we do that now
                    if (checkPlacements) {
                        // Undo the just-took turn to be able to take a closer look at the regions
                        game.undoLastTurn();
                        // Fetch the regions that are placed at this point
                        game.takeTurn(possPlacement, false);
                        // Fetch the new score of the starting player
                        int newScore = getScore(game, player);
                        int placementsEnd = checkRegions(game.getBoard().getField(), player).size();
                        // Calculate the difference between the values regions & score
                        deltaScore = newScore - oldScore;
                        deltaRegions = placementsEnd - placementsStart;
                    } else {
                        // We don't calculate the placements
                        int newScore = getScore(game, player);
                        deltaRegions = placementsStart;
                        deltaScore = newScore - oldScore;
                    }
                    // Add that placement to the list of possible placements
                    data.add(new PlacementData(possPlacement, deltaRegions, deltaScore));
                    game.undoLastTurn();
                }
            }
        }
    }

    /**
     * From any given player or captured region, return the opposite player or opposite players region capture color.
     *
     * @param player the player to get the region for
     * @return the opposite player/player_region
     */
    private Color getOppositePlayer(Color player) {
        switch (player) {
            case White:
                return Color.Black;
            case Black:
                return Color.White;
            case White_Owned:
                return Color.Black_Owned;
            case Black_Owned:
                return Color.White_Owned;
            default:
                return Color.None;
        }
    }

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
        // Score that this placement would give to this player
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

        public double getScore() {
            return this.playerScoreDelta + this.capture;
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
}
