package de.fhkiel.ki.examples.gui.withAi.kathedralai;

import de.fhkiel.ki.ai.CathedralAI;
import de.fhkiel.ki.cathedral.*;
import java.util.HashSet;
import java.util.Set;

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

    @Override
    public String name() {
        return "Team Echo V2";
    }

    @Override
    public void init(Game game) {
    }

    @Override
    public Placement takeTurn(Game game) {
        return null;
    }

    @Override
    public void stopAI() {
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
     * @return
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
         * @param game the game we are working on
         * @param from the start of the x coordinates to check
         * @param to   the end of the x coordinates to check
         * @param checkplacements if the algorithms should check the number of placements
         *
         * @return the among of possible placables on this board for the current player
         */
        private Set<Placement> getPlacements(Game game, int from, int to, boolean checkplacements) {
            Set<Placement> possibles = new HashSet<>();
            for (int x = from; x < to; x++) {
                for (int y = 0; y < 10; y++) {
                    checkPlacementData(x, y, game, possibles, checkplacements);
                }
            }
            return possibles;
        }

        /**
         * Check the current position and return all blocks that fit there
         *
         * @param x    the x coordinate
         * @param y    the y coordinate
         * @param game the game to test for
         * @param data the placement data to write to on success
         * @param checkPlacements if the algorithm should check the number of placements
         */
        private void checkPlacementData(int x, int y, Game game, Set<Placement> data, boolean checkPlacements) {
            // Fetch the current player
            Color player = game.getCurrentPlayer();
            int oldScore = getScore(game, player);
            // Check, if this current field is applicable for checking it out, specifically if and only if the field belongs
            // to the current player or to no one at all
            for (Building building : game.getPlacableBuildings()) {
                // Test all turnables of the building
                for (Direction direction : building.getTurnable().getPossibleDirections()) {
                    Placement possPlacement = new Placement(x, y, direction, building);
                    // Take a turn using the "fast" method without checking the regions
                    if (game.takeTurn(possPlacement, true) && checkPlacements) {
                        // Undo the just-took turn to be able to take a closer look at the regions
                        game.undoLastTurn();
                        // Fetch the regions that are placed at this point
                        // TODO: Implement/Extend the behavior
                        game.undoLastTurn();
                    }
                }
            }
        }
}
