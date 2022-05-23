package de.fhkiel.ki.examples.gui.withAi;

import de.fhkiel.ki.ai.CathedralAI;
import de.fhkiel.ki.cathedral.*;

import java.util.*;
import java.util.stream.Collectors;

public class NewAi implements CathedralAI {

    /* Colors */
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";

    // Threads which the program can use to
    private List<TurnCalculator> threads;
    // How many physical threads the system has access to
    private int processors;

    @Override
    public String name() {
        return "Team ECHO";
    }

    @Override
    public void init(Game game) {
        // Allocate a new thread list
        int processors = Runtime.getRuntime().availableProcessors();
        threads = new ArrayList<>(Math.min(processors, 2));
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
        // Get the percept:
        // ---------------
        Set<PlacementData> possibles;
        Set<PlacementData> opponentsPlacements;
        // Check if the opponents next placement would result in a captured region
        // ------------------------------------------------------------------------
        // 1. Assume the position of the opponent
        System.out.println(ANSI_GREEN + "[LOG] Calculate possible positions" + ANSI_RESET);
        // 2. Fetch the possible movements of the opponent after this turn
        // Check the positions that are possible right now:
        // ------------------------------------------------
        // Test all buildings
        // Test every position on the board and filter any turn that is not possible
        // Iterate over the board positions
        // Generate the worker threads
        threads.add(0, new TurnCalculator(game, true));
        threads.add(1, new TurnCalculator(game, false));
        // Start the threads
        threads.forEach(thread -> {
            thread.start();
        });
        threads.forEach(worker -> {
            try {
                worker.join();
            } catch (InterruptedException e) {
                System.out.println(ANSI_RED + "[ERROR] The " + ANSI_RESET);
                e.printStackTrace();
            }
        });
        // Fetch the data from the threads
        opponentsPlacements = threads.get(0).getData();
        possibles = threads.get(1).getData();

        // Clear the thread list, as it is no longer needed
        threads.clear();

        // 2. Filter for the positions that would capture regions
        opponentsPlacements.removeIf(placementData -> placementData.positions == -1);
        // Get the set of all positions
        Set<Position> opponentPositions = (opponentsPlacements
                .stream()
                .map(var -> var.getPlacement().position()))
                .collect(Collectors.toSet());

        // Create the set union of the possible placements that the current player can make and the region-capturing
        // placements our opponent could make
        Set<Position> playerPositions = possibles.stream().map(var -> var.getPlacement().position()).collect(Collectors.toSet());

        // Get the overlapping positions of the placements that both players took this turn
        opponentPositions.retainAll(playerPositions);

        Set<PlacementData> finalPlacements = new HashSet<>();
        // Calculate all the position that
        Set<PlacementData> finalPlacements1 = finalPlacements;
        opponentPositions.forEach(pos -> checkPlacementData(pos.x(), pos.y(), game, finalPlacements1));

        System.out.println(ANSI_GREEN + "[LOG] Calculate optimal position" + ANSI_RESET);
        // If there are no more overlapping positions, then take the optimal data form the player
        if (opponentPositions.size() == 0) {
            finalPlacements = possibles;
        }
        // Calculate the data on the
        finalPlacements.forEach(placed -> placed.prevent =
                opponentsPlacements.stream()
                        .filter(data -> data.placement.position().equals(placed.placement.position()))
                        .mapToDouble(PlacementData::getPositions)
                        .average()
                        .orElse(0.0)
        );
        // Add all possibles to the finals that have just been calculated
        finalPlacements.addAll(possibles);

        // Apply rule-set on possible turns:
        // ---------------------------------

        // abort, as there are no placements possible!
        if (finalPlacements.isEmpty()) {
            skip();
            return null;
        }
        // Select the placement that has the highest point value
        List<PlacementData> valueSort = finalPlacements
                .stream()
                .sorted(Comparator.comparing(PlacementData::getScore))
                .toList();
        PlacementData bestPlacement = valueSort.get(valueSort.size() - 1);
        System.out.println("The best placement, equals? : " + bestPlacement.equals(valueSort.get(valueSort.size() - 1)));
        // TODO: Check into the future, once, and if the placement has any adverse effects, take the next best placement
        // from the placement list.
        System.out.println(bestPlacement);

        // Print out the time it took to calculate this action
        printTime(start, System.nanoTime());
        // Print confirmation for the finished calculations
        System.out.println(ANSI_GREEN + "[LOG] Done" + ANSI_RESET);

        // Return the calculated action
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
     * Call this method in order to do nothing
     */
    private void skip() {
        System.out.println(ANSI_GREEN + "[LOG] Skip, as there are no valid placements no more!" + ANSI_RESET);
    }

    /**
     * Calculate the placables that can be put onto the field for the current player
     *
     * @param game the game we are working on
     * @return the among of possible placables on this board for the current player
     */
    private static Set<PlacementData> getPlacements(Game game) {
        Set<PlacementData> possibles = new HashSet<>();
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                checkPlacementData(x, y, game, possibles);
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
     */
    private static void checkPlacementData(int x, int y, Game game, Set<PlacementData> data) {
        // Fetch the field data
        Color[][] field = game.getBoard().getField();
        Color player = game.getCurrentPlayer();
        // Check, if this current field is applicable for checking it out, specifically if and only if the field belongs
        // to the current player or to no one at all
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
     * Data class that contains all the data for a placement that are relevant for it's evaluation
     */
    static class PlacementData {
        // The placement
        public Placement placement;
        // the amount of positions in the region this placement would get
        public int positions;
        // The amount of captures from the opponent this placement would prevent
        public double prevent;
        // The value that this placement would change in the score of the current player
        public int deltaScore;

        PlacementData(Placement placement, int positions) {
            this.placement = placement;
            this.positions = positions;
            this.prevent = 0;
            this.deltaScore = 0;
        }

        /**
         * Calculate the difference that this placement would do in the player's score, and place it in
         * this data structure afterwards.
         *
         * @param oldValue the old value of the player score
         * @param newValue the new value of the player score after placing this piece in this exact rotation
         */
        public void newDiff(int oldValue, int newValue) {
            this.deltaScore = newValue - oldValue;
        }

        public int getScoreDelta() {
            return this.deltaScore;
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
                    + "\n=> Score: "
                    + getScore()
                    + "\n The new score after this placement would be: "
                    + getScoreDelta();
        }

        /**
         * Here you can scale how much the prevention value matters for these calculations
         *
         * @return the scale of the prevention value
         */
        public int preventValue() {
            return (int) Math.ceil(this.prevent / 2);
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

        public Placement getPlacement() {
            return this.placement;
        }
    }

    /**
     * Thread class that calculates the next turn for a given player and game
     */
    class TurnCalculator extends Thread {
        // The game for which this thread works
        private Game game;
        // The data for this thread;
        private Set<PlacementData> data;

        /**
         * Create a new worker thread that would calculate the next possible position placement set for the player
         *
         * @param game         the game to work on, and create a copy for
         * @param changePlayer if the player to be checked is the next one
         */
        TurnCalculator(Game game, boolean changePlayer) {
            // Copy this game for this thread
            this.game = game.copy();
            if (changePlayer) this.game.forfeitTurn();
        }

        @Override
        public void run() {
            data = NewAi.getPlacements(game);
        }

        public Set<PlacementData> getData() {
            return this.data;
        }
    }

}

