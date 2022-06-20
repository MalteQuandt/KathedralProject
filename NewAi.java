package de.fhkiel.ki.examples.gui.withAi;

import de.fhkiel.ki.ai.CathedralAI;
import de.fhkiel.ki.cathedral.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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
    private Integer processors;
    // The weights that are used to find the best placement from a collection of placements
    private WeightContainer weights;
    // How many times the thread iteration should take place
    private Integer iterateOver;
    // Score which decides if the ai must play aggressive of defensive
    private Double evaluate;

    @Override
    public String name() {
        return "Team ECHO";
    }

    @Override
    public void init(Game game) {
        // Allocate a new thread list
        processors = Runtime.getRuntime().availableProcessors();
        threads = new ArrayList<>(Math.min(processors, 2));
        iterateOver = 4;
        // Set the default values for the weights
        defensiv();
    }

    @Override
    public void stopAI() {
    }

    @Override
    public Placement takeTurn(Game game) {
        // 0. Fetch the start time of the function
        long start = System.nanoTime();
        Game copy = game.copy();
        evaluate(copy);

        PlacementData bestPlacement;
        System.out.println(ANSI_GREEN + "[LOG] Capture percepts" + ANSI_RESET);
        // Get the percept:
        // ---------------
        Set<PlacementData> possibles = new HashSet<>();
        Set<PlacementData> opponentsPlacements = new HashSet<>();
        // Check if the opponents next placement would result in a captured region
        // ------------------------------------------------------------------------
        // 1. Assume the position of the opponent
        System.out.println(ANSI_GREEN + "[LOG] Calculate possible positions" + ANSI_RESET);
        // 2. Fetch the possible movements of the opponent after this turn
        // Check the positions that are possible right now:
        // ------------------------------------------------
        // Test all buildings
        // Test every position on the board and filter any turn that is not possible
        calculatePossiblesAndOpponentsPlacements(copy, opponentsPlacements, possibles);

        // We don't have to calculate anything else if the field is empty
        if (!copy.getBoard().getPlacedBuildings().isEmpty()) {
            // 2. Filter for the positions that would capture regions
            Set<PlacementData> finalPlacements = getOverlappingPlacements(copy, opponentsPlacements, possibles);

            System.out.println(ANSI_GREEN + "[LOG] Calculate optimal position" + ANSI_RESET);
            // Calculate the average positions that the opponent would capture at each position
            calculatePreventedCaptures(finalPlacements, opponentsPlacements);
            // Add all possibles to the finals that have just been calculated
            finalPlacements.addAll(possibles);

            // Apply rule-set on possible turns:
            // ---------------------------------

            // abort, as there are no placements possible!
            if (finalPlacements.isEmpty()) {
                skip();
                return null;
            }
            bestPlacement = calculateFinalPlacement(copy, finalPlacements);
        } else {
            // Get a random placement of the list
            bestPlacement = getRandomPlacement(possibles);
        }
        // Print out the time it took to calculate this action
        long endTime = System.nanoTime();
        printTime(start, endTime);
        // Print confirmation for the finished calculations
        System.out.println(ANSI_GREEN + "[LOG] Done" + ANSI_RESET);

//        System.out.println(bestPlacement.toWeightedString(weights));
//        System.out.println(iterateOver);
//        System.out.println(weights);
        return bestPlacement.placement;
    }

    public void defensiv() {
        weights = new WeightContainer(-1.0f, 1.0f, 1.0f, .8f, -1.0f);
    }

    /**
     * An aggressive strategies that captures as many regions as possible and tries to get the highest score delta
     */
    public void aggressive() {
        weights = new WeightContainer(-2.0f, 1.0f, 1.0f, 0.2f, -0.2f);
    }

    /**
     * Calculate the best placements for a given set of placement data
     *
     * @param copy            the copy of the game on which to operate
     * @param finalPlacements the final placements to check the future for
     * @return the best placement according to a score function
     */
    public PlacementData calculateFinalPlacement(Game copy, Set<PlacementData> finalPlacements) {
        // 0. Setup:
        // ---------
        // Take a realistic amount of processors from the system for this application
        int availableProcessors = (processors > 2) ? (processors - 2) : 2;
        List<OpponentWorker> opponentWorkers = new ArrayList<>(100);
        // The data for the opponents possible reactions
        Map<PlacementData, List<PlacementData>> opponentData = new HashMap<>();

        // 1. Select the placement that has the highest point value
        List<PlacementData> highestScorePlacement = finalPlacements
                .stream()
                .sorted(Comparator.comparing(placementData -> placementData.getScore(weights)))
                .toList();

        // Get the size of the list so that the loop variable can be accurately determined
        int finalListSize = highestScorePlacement.size();
        // Iterate over the list for the amount of processors available, and with at least 1 thread
        int loop = Math.max(Math.min(finalListSize, availableProcessors), 1);

        System.out.println(ANSI_GREEN + "[LOG] For each placement, predict the opponents possible reaction" + ANSI_RESET);

        // Iterate over the best placements and look into what the opponent might possibly react with,
        // and fill those reactions into a list
        Map<WeightContainer, PlacementData> weightContainerPlacementDataMap = calculateOpponentsReactionsForGivenWeights(copy, iterateOver, loop, opponentWorkers, opponentData, highestScorePlacement);

        Map<PlacementData, Double> opponentPlacementsToScoreDelta = mapOpponentResultsToPlacements(opponentData);
        // Choose the placement as the best, that has the lowest gain for the opponent in the next turn:
        PlacementData bestPlacement = calculateOptimalPlacement(opponentPlacementsToScoreDelta);

        // Get the weight for the best placement
        return bestPlacement;
    }

    /**
     * From a given range of weight container to placement data, calculate the average weight for the top n placements
     *
     * @param weightContainerPlacementDataMap
     * @param n
     * @return
     */
    WeightContainer calculateNewAverageWeight(Map<WeightContainer, PlacementData> weightContainerPlacementDataMap, int n) {
        WeightContainer container = new WeightContainer();
        int numberElements = weightContainerPlacementDataMap.size();
        // Adds the number of elements on top of each other
        for (Map.Entry<WeightContainer, PlacementData> c : weightContainerPlacementDataMap.entrySet()) {
            container.opponentWeight += c.getKey().opponentWeight;
            container.scoreWeight += c.getKey().scoreWeight;
            container.captureWeight += c.getKey().captureWeight;
            container.scoreDeltaWeight += c.getKey().scoreDeltaWeight;
            container.preventWeight += c.getKey().preventWeight;
        }
        // Divide by the number of elements
        container.opponentWeight /= numberElements;
        container.scoreWeight /= numberElements;
        container.captureWeight /= numberElements;
        container.scoreDeltaWeight /= numberElements;
        container.preventWeight /= numberElements;

        return container;
    }

    /**
     * Map the opponents reactions onto opponentData field of the placement they were reacting to.
     *
     * @param opponentData the mapping of placement to opponents reaction
     * @return the mapping of placement to opponents reaction minimized using an aggregate function [min, max, avg, ...]
     */
    public Map<PlacementData, Double> mapOpponentResultsToPlacements(Map<PlacementData, List<PlacementData>> opponentData) {
        Map<PlacementData, Double> opponentPlacementsToScoreDelta = new HashMap<>();
        // For each map position, calculate the best score the opponent will get
        for (Map.Entry<PlacementData, List<PlacementData>> entry : opponentData.entrySet()) {
            // Calculate the average response of the opponent and store it in another list, or a field in data
            opponentPlacementsToScoreDelta.put(entry.getKey(), entry
                    .getValue()
                    .stream()
                    .mapToDouble(value -> value.getScore(weights))
                    .max()
                    .orElse(0.0f));
        }
        // For each placement key in the map, set the opponent score to the map value
        opponentPlacementsToScoreDelta.entrySet().forEach(entry -> entry.getKey().setOpponentScore(entry.getValue()));

        // Return the mapping
        return opponentPlacementsToScoreDelta;
    }

    /**
     * From a given map of placement to double mapping, calculate the placement that has the highest score value according
     * to a selected function
     *
     * @param opponentPlacementsToScoreDelta mapping of placement to the average opponent reaction
     * @return the optimal placement according to a function
     */
    public PlacementData calculateOptimalPlacement(Map<PlacementData, Double> opponentPlacementsToScoreDelta) {
        // Sor the list according to
        List<PlacementData> finalData = opponentPlacementsToScoreDelta.entrySet().stream()
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(placementData -> placementData.getScore(weights)))
                .toList();
        return finalData.get(finalData.size() - 1);
    }


    public Map<WeightContainer, PlacementData> calculateOpponentsReactionsForGivenWeights(
            Game copy,
            final Integer iterateOver,
            final Integer loop,
            List<OpponentWorker> opponentWorkers,
            Map<PlacementData, List<PlacementData>> opponentData,
            List<PlacementData> highestScorePlacement) {
        Map<WeightContainer, PlacementData> weightContainerPlacementDataHashMap = new HashMap<>();
        for (int i = 0; i < iterateOver; i++) {
            for (int j = 0; j < loop; j++) {
                // If the bounds are unrealistic, break out from this inner loop
                if ((highestScorePlacement.size() - (iterateOver * j + j) - 1) < 0) {
                    break;
                }
                final Integer finalI = i;
                final Integer finalJ = j;
                // Calculate the best placements for any given weight in the initials list
                PlacementData placement = highestScorePlacement
                        .stream()
                        .sorted(Comparator.comparing(placementData -> placementData.getScore(this.weights)))
                        .collect(Collectors.toList())
                        .get(highestScorePlacement.size() - (iterateOver * j + j) - 1);
                // Put the {Weight, Placement} map into the container
                weightContainerPlacementDataHashMap.put(this.weights, placement);
                // Apply the placement
                copy.takeTurn(placement.getPlacement());
                // Calculate the next turn of the opponent
                opponentWorkers.add(new OpponentWorker(copy));
                // Create a link between the data storage in the thread and the belonging
                opponentData.put(placement, opponentWorkers.get(j).getData());
                // Reset the previously generated turn
                copy.undoLastTurn();

            }
            // Fill the work calculators
            for (OpponentWorker worker : opponentWorkers) {
                worker.start();
            }
            // Wait for those threads to finish execution
            for (OpponentWorker worker : opponentWorkers) {
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // Last iteration, thus we clear the opponent workers
            opponentWorkers.clear();
        }
        return weightContainerPlacementDataHashMap;
    }

    public void evaluate(Game game) {
        if (evaluate == null) {
            evaluate = 0.0; // We start out by playing defensive
        }
        // A positive score difference, meaning if the current player has higher score,
        // the difference is positive
        int scoreDifference = getScoreDifference(game, game.getCurrentPlayer());
        if (0.0 < scoreDifference) {
            // There is a positive difference, thus the enemy is 'loosing' => Play more aggressive
            evaluate += 0.2 + Math.abs(0.03 * scoreDifference);
        } else {
            // There is a negative difference, thus the enemy is 'winning' => Play more defensive
            evaluate -= 0.2 + Math.abs(0.03 * scoreDifference);
        }

        // Make sure the score stays in range
        if(evaluate > 1.0) {
            evaluate = 1.0;
        } else if(evaluate < 0.0) {
            evaluate = 0.0;
        }

        // Set the strategie to use
        if (evaluate < 0.5) {
            // Defensive
            defensiv();
            System.out.println("[LOG] Selection: DEFENSIVE" + ANSI_RESET);
        } else {
            // Aggressive
            aggressive();
            System.out.println(ANSI_CYAN + "[LOG] Selection: AGGRESSIVE" + ANSI_RESET);
        }
    }
    /**
     * Set up the worker threads for a given placement and calculate the opponents reactions with it
     *
     * @param copy
     * @param i
     * @param j
     * @param opponentWorkers
     * @param opponentData
     * @param highestScorePlacement
     */
    public void setupOpponentWorkerThread(
            Game copy,
            int i, int j,
            int iterateOver,
            List<OpponentWorker> opponentWorkers,
            Map<PlacementData, List<PlacementData>> opponentData,
            List<PlacementData> highestScorePlacement) {
        // Fetch the placement
        PlacementData placement = highestScorePlacement.get(highestScorePlacement.size() - (iterateOver * j + j) - 1);
        // Apply the placement
        copy.takeTurn(placement.getPlacement());
        // Calculate the next turn of the opponent
        opponentWorkers.add(new OpponentWorker(copy));
        // Create a link between the data storage in the thread and the belonging
        opponentData.put(placement, opponentWorkers.get(j).getData());
        // Reset the previously generated turn
        copy.undoLastTurn();
    }

    /**
     * Get a random element from a set of placement data
     *
     * @param placementDataSet the set to get the random element from
     * @return the random element
     */
    public PlacementData getRandomPlacement(Set<PlacementData> placementDataSet) {
        return placementDataSet.stream().skip((int) (placementDataSet.size() * Math.random())).findFirst().get();
    }

    /**
     * Calculate the regions that each opposing placement would capture at each position, and apply that using a
     * aggregate function [min, max, avg,...] to the final placement data
     *
     * @param finalPlacements
     * @param opponentsPlacements
     */
    public void calculatePreventedCaptures(Set<PlacementData> finalPlacements, Set<PlacementData> opponentsPlacements) {
        finalPlacements.forEach(placed -> placed.prevent =
                opponentsPlacements.stream()
                        .filter(data -> data.placement.position().equals(placed.placement.position()))
                        .mapToDouble(PlacementData::getPositions)
                        .max()
                        .orElse(0.0)
        );
    }

    /**
     * Filter for all the placements that have overlapping positions in the possibles and the possibles for the opponent
     *
     * @param opponentsPlacements the opponents placements
     * @param possibles           the possible placements for the current player
     * @return
     */
    public Set<PlacementData> getOverlappingPlacements(Game copy, Set<PlacementData> opponentsPlacements, Set<PlacementData> possibles) {
        opponentsPlacements.removeIf(placementData -> placementData.positions == -1);
        // Get the set of all positions
        Set<Position> opponentPositions = (opponentsPlacements
                .stream()
                .map(var -> var.getPlacement().position()))
                .collect(Collectors.toSet());

        // Create the set union of the possible placements that the current player can make and the region-capturing
        // placements our opponent could make
        Set<Position> playerPositions = possibles.stream()
                .map(var -> var.getPlacement().position())
                .collect(Collectors.toSet());

        // Get the overlapping positions of the placements that both players took this turn
        opponentPositions.retainAll(playerPositions);

        Set<PlacementData> finalPlacements = new HashSet<>();
        // Calculate all the position that
        Set<PlacementData> finalPlacements1 = finalPlacements;

        opponentPositions.forEach(pos -> checkPlacementData(pos.x(), pos.y(), copy, finalPlacements1, true));

        // If there are no more overlapping positions, then take the optimal data form the player
        if (finalPlacements.size() == 0 || finalPlacements == null) {
            finalPlacements = possibles;
        } else {
            finalPlacements = finalPlacements1;
        }

        return finalPlacements;
    }


    // Utility Methods
    // ---------------

    public void calculatePossiblesAndOpponentsPlacements(Game game, Set<PlacementData> opponentPlacements, Set<PlacementData> possibles) {
        threads.add(0, new TurnCalculator(game, true, 0, 10));
        threads.add(1, new TurnCalculator(game, false, 0, 10));
        // Start the threads
        threads.forEach(Thread::start);
        // Wait for each thread to finish execution
        threads.forEach(worker -> {
            try {
                worker.join();
            } catch (InterruptedException e) {
                System.out.println(ANSI_RED + "[ERROR] The " + ANSI_RESET);
                e.printStackTrace();
            }
        });
        // Fetch the data from the threads
        opponentPlacements.addAll(threads.get(0).getData());
        possibles.addAll(threads.get(1).getData());
        // Clear the thread list, as it is no longer needed
        threads.clear();
    }

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
     * Call this method in order to do nothing
     */
    private void skip() {
        System.out.println(ANSI_GREEN + "[LOG] Skip, as there are no valid placements no more!" + ANSI_RESET);
    }

    /**
     * Calculate the placables that can be put onto the field for the current player
     *
     * @param game the game we are working on
     * @param from the start of the x coordinates to check
     * @param to   the end of the x coordinates to check
     * @return the among of possible placables on this board for the current player
     */
    private static Set<PlacementData> getPlacements(Game game, int from, int to, boolean getRegions) {
        Set<PlacementData> possibles = new HashSet<>();
        for (int x = from; x < to; x++) {
            for (int y = 0; y < 10; y++) {
                checkPlacementData(x, y, game, possibles, getRegions);
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
    private static void checkPlacementData(int x, int y, Game game, Set<PlacementData> data, boolean getRegions) {
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
                    // Get the current score of the previous player
                    int newScore = getScore(game, player);
                    PlacementData placement = new PlacementData(possPlacement, getRegions ? newRegionSize : 0);
                    placement.newDiff(oldScore, newScore);
                    // We can take a turn, thus we add it to the "possible" set
                    data.add(placement);
                    game.undoLastTurn();
                }
            }
        }
    }

    /**
     * calculate the score of a given player at any given point in time
     *
     * @param game
     * @param player
     * @return
     */
    private static Integer getScore(Game game, Color player) {
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
     * Get the score difference for a specific player
     *
     * @param game   the game to operate on
     * @param player the player to get the difference for
     * @return
     */
    private int getScoreDifference(Game game, Color player) {
        int whiteScore = getScore(game, Color.White);
        int blackScore = getScore(game, Color.Black);
        // Calculate the difference, which would be postivive for white if white has more than black
        int scoreDifference = whiteScore - blackScore;
        return player.equals(Color.White) ? scoreDifference : -scoreDifference;
    }
    /**
     * Get the amount of regions captured by the current player at any given point in time
     *
     * @param field  the board to check on
     * @param player the player's regions to check for
     * @return the amount of regions the current player currently has
     */
    private static int checkRegionsSize(Color[][] field, Color player) {
        int regions = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                // If the field is a capture of the current player, and if the field is not yet captured
                if (field[i][j].getId() == (player.getId() + 1)) regions++;
            }
        }
        return regions;
    }
    /**
     * Get the intersection of two sets of positions
     *
     * @param a the first position set
     * @param b the second position set
     * @return the set intersection
     */
    private Set<Position> getSetIntersection(Collection<Position> a, Collection<Position> b) {
        return a.stream().filter(position -> b.contains(position)).collect(Collectors.toSet());
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
        // The score that the opponent can gain in the next turn, should this placement be chosen
        public double opponentScore;

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


        /**
         * Here you can scale how much the prevention value matters for these calculations
         *
         * @return the scale of the prevention value
         */
        public int preventValue() {
            return (int) Math.ceil(this.prevent / 2);
        }

        // Getter & Setter
        // ---------------
        public double getOpponentScore() {
            return opponentScore;
        }

        public void setOpponentScore(double opponentScore) {
            this.opponentScore = opponentScore;
        }

        public double getScore(WeightContainer weights) {
            return getScore(weights.scoreDeltaWeight, weights.scoreWeight, weights.captureWeight, weights.preventWeight, weights.opponentWeight);
        }

        public double getScore(double scoreDeltaWeight, double scoreWeight, double captureWeight, double preventWeight, double opponentWeight) {
            return scoreDeltaWeight * this.getScoreDelta()
                    + scoreWeight * this.placement.building().score()
                    + captureWeight * this.positions
                    + preventWeight * preventValue()
                    + opponentWeight * opponentScore;
        }


        public String toWeightedString(WeightContainer weights) {
            return toWeightedString(weights.scoreDeltaWeight, weights.scoreWeight, weights.captureWeight, weights.preventWeight, weights.opponentWeight);
        }

        public String toWeightedString(double scoreDeltaWeight, double scoreWeight, double captureWeight, double preventWeight, double opponentWeight) {
            return "Placement: "
                    + this.placement.toString()
                    + "\nPlacement Score: "
                    + (scoreWeight * this.placement.building().score())
                    + "\nRegions: "
                    + (getPositions() * captureWeight)
                    + "\nPrevents: "
                    + (preventWeight * getPrevent())
                    + "\nScore Delta: "
                    + (scoreDeltaWeight * getScoreDelta())
                    + "\nOpponentGain: "
                    + (opponentWeight * getOpponentScore())
                    + "\n=> Score: "
                    + getScore(new WeightContainer(scoreDeltaWeight, scoreWeight, captureWeight, preventWeight, opponentWeight));
        }

        @Override
        public String toString() {
            return "Placement: "
                    + this.placement.toString()
                    + "\nRegions: "
                    + getPositions()
                    + "\nPrevents: "
                    + getPrevent()
                    + "\nScore Delta"
                    + getScoreDelta()
                    + "\nOpponentGain: "
                    + getOpponentScore()
                    + "\n=> Score: "
                    + getScore(new WeightContainer(1.0f, 1.0f, 1.0f, 1.0f, 1.0f));
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

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PlacementData) {
                PlacementData testPlacement = (PlacementData) obj;
                return this.placement.equals(testPlacement)
                        && this.positions == testPlacement.positions
                        && this.deltaScore == testPlacement.deltaScore
                        && this.prevent == testPlacement.prevent;
            } else {
                return false;
            }
        }
    }

    /**
     * Thread class that calculates the next turn for a given player and game
     */
    static class TurnCalculator extends Thread {
        // The game for which this thread works
        private final Game game;
        // The data for this thread;
        private Set<PlacementData> data;
        // from where to check the field
        private final Integer from;
        // up to where to check the field
        private final Integer to;

        /**
         * Create a new worker thread that would calculate the next possible position placement set for the player
         *
         * @param game         the game to work on, and create a copy for
         * @param changePlayer if the player to be checked is the next one
         * @param from         the start of the x coordinates to check
         * @param to           the end of the x coordinates to check
         */
        TurnCalculator(Game game, boolean changePlayer, int from, int to) {
            // Copy this game for this thread
            this.game = game.copy();
            if (changePlayer) this.game.forfeitTurn();
            this.from = from;
            this.to = to;
        }

        TurnCalculator(Game game, boolean changePlayer) {
            this(game, changePlayer, 0, 10);
        }

        @Override
        public void run() {
            data = NewAi.getPlacements(game, from, to, true);
        }

        public Set<PlacementData> getData() {
            return this.data;
        }
    }

    protected class OpponentWorker extends Thread {
        // The game that this thread operates on
        private final Game game;
        // Data this thread generates
        private final List<PlacementData> data;

        // Store the hull placements for each player, and calulate them each anew at the start of the ki
        private final Map<Color, Set<Position>> hullPlayerPositions;
        // Store the turn placements for each player and calculate them each anew at the start of the ki
        private final Map<Color, Set<Position>> turnPlayerPositions;
        // Store the positions that the turn player can place their placements to
        private final Map<Color, Set<Position>> turnPlayerPlacablePositions;

        public OpponentWorker(Game game) {
            this.game = game.copy();
            data = new ArrayList<PlacementData>();

            turnPlayerPlacablePositions = new HashMap<>();
            hullPlayerPositions = new HashMap<>();
            turnPlayerPositions = new HashMap<>();

            updateTurn(game);
            updateHull(game);
            updateTurnPlayerPlacables(game);
        }

        @Override
        public void run() {
            // Generate all the placement data that is possible for the opponent
            Set<PlacementData> opponentPlacements = calculateCapturingPlacements(game, game.getCurrentPlayer());
            this.data.addAll(opponentPlacements);
            // Sort this list according to it's score, so that the main thread does not have to do this later
            this.data.sort(Comparator.comparing(placementData -> placementData.getScore(weights)));
        }

        public List<PlacementData> getData() {
            return this.data;
        }

        // Placement finding methods
        // -------------------------

        /**
         * calculate all the placements that would capture a specific region for a given player
         *
         * @param game   the game to work on
         * @param player the player to calculate for
         * @return the placements that are calculated
         */
        private Set<PlacementData> calculateCapturingPlacements(Game game, Color player) {
            // 0. Initialization
            // Get the current score and regions for current player
            int currentScore = getScore(game, player);
            int oldRegions = checkRegionsSize(game.getBoard().getField(), player);
            // Iterate over all possible placements
            Set<PlacementData> placementDataList = new HashSet<>();
            for (Building building : game.getPlacableBuildings(player)) {
                for (Direction direction : building.getTurnable().getPossibleDirections()) {
                    for (int x = 0; x < 10; x++) {
                        for (int y = 0; y < 10; y++) {
                            Placement placement = new Placement(new Position(x, y), direction, building);
                            // Positions of the placement shifted by the position
                            Set<Position> placementPositions = placement
                                    .form()
                                    .stream()
                                    .map(p -> p.plus(placement.position()))
                                    .collect(Collectors.toSet());
                            // Find all positions that are in the placement and the hull of the current player
                            Set<Position> intersection = getSetIntersection(this.hullPlayerPositions.get(player), placementPositions);
                            // There are intersection positions, thus we can check this placement
                            if (intersection.size() != 0 && game.takeTurn(placement, false)) {
                                game.undoLastTurn();
                                game.takeTurn(placement, false);

                                int newScore = getScore(game, player);
                                int newRegions = checkRegionsSize(game.getBoard().getField(), player);

                                PlacementData newPlacement = new PlacementData(placement,  newScore - currentScore);
                                newPlacement.deltaScore = newScore - currentScore;
                                placementDataList.add(newPlacement);
                                game.undoLastTurn();
                            }
                        }
                    }
                }
            }
            return placementDataList;
        }

        /**
         * For both players, update the hull position set
         *
         * @param copy the game on which to operate
         * @before Make sure that the updateTurn method is called before this method!
         */
        private void updateHull(Game copy) {
            // Black:
            this.hullPlayerPositions.put(Color.Black, getPlayerHull(copy, Color.Black));
            // White
            this.hullPlayerPositions.put(Color.White, getPlayerHull(copy, Color.White));
            // Blue
            this.hullPlayerPositions.put(Color.Blue, getPlayerHull(copy, Color.Blue));
        }

        /**
         * For both players, update the turn position set
         *
         * @param copy the game on which to operate
         */
        private void updateTurn(Game copy) {
            // Black
            this.turnPlayerPositions.put(Color.Black, getPlayerTurn(copy, Color.Black));
            this.turnPlayerPositions.put(Color.Black_Owned, getPlayerTurn(copy, Color.Black_Owned));
            // White
            this.turnPlayerPositions.put(Color.White, getPlayerTurn(copy, Color.White));
            this.turnPlayerPositions.put(Color.White_Owned, getPlayerTurn(copy, Color.White_Owned));
            // Blue
            this.turnPlayerPositions.put(Color.Blue, getPlayerTurn(copy, Color.Blue));
        }

        private void updateTurnPlayerPlacables(Game copy) {
            Color player = copy.getCurrentPlayer();
            Set<Position> playerPlacablePositions = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    Color clr = copy.getBoard().getField()[i][j];
                    if (clr.equals(player.subColor()) || clr.equals(Color.None)) {
                        playerPlacablePositions.add(new Position(i, j));
                    }
                }
            }
            this.turnPlayerPlacablePositions.put(copy.getCurrentPlayer(), playerPlacablePositions);
        }
        /**
         * Get the placements for a given player
         *
         * @param game   the game to get the placements from
         * @param player the player to get the plcements for
         * @return the placements of a given player
         */
        public Set<Placement> getPlayerPlacements(Game game, Color player) {
            return game.getBoard().getPlacedBuildings().stream().filter(placement -> placement.building().getColor().equals(player)).collect(Collectors.toSet());
        }

        // Position methods
        // ----------------

        /**
         * Calculate all positions in a game that belong to a given player
         *
         * @param game   the game to get the positions from
         * @param player the player to get the positions for
         * @return the position set for a given player
         */
        public Set<Position> getPlayerTurn(Game game, Color player) {
            // Get all placements for a given player
            Set<Placement> placements = getPlayerPlacements(game, player);
            // Get all the positions for a given player
            Set<Position> positions = new HashSet<>();
            for (Placement placement : placements) {
                // Only take this placement into account if it belongs to the player 'player'
                Set<Position> placementTurn = calculateTurn(placement, placement.position());
                positions.addAll(placementTurn);
            }
            return positions;
        }

        private Set<Position> calculateTurn(Placement placementData, Position p) {
            return calculateTurn(placementData)
                    .stream()
                    .map(position -> position.plus(p))
                    .collect(Collectors.toSet());
        }

        private Set<Position> calculateTurn(Placement placementData) {
            return new HashSet<>(placementData.building().turn(placementData.direction()));
        }
        // Hull Methods
        // ------------

        /**
         * Calculate the hull around a placement shifted by a placement position
         *
         * @param placementData the data to calculate the hull around
         * @param p             the position to shift by
         * @return the shifted hull around a set of placements
         */
        private Set<Position> calculateHull(Placement placementData, Position p) {
            return calculateHull(placementData)
                    .stream()
                    .map(position -> position.plus(p))
                    .collect(Collectors.toSet());
        }

        /**
         * Calculate the hull of a set of positions
         *
         * @return the hull around the positions
         */
        private Set<Position> calculateHull(Placement placementData) {
            return new HashSet<>(placementData.building().corners(placementData.direction()));
        }

        /**
         * For a given player p, get the positions that represent the hull of each placement on the field
         *
         * @param game   the game to take the placements from
         * @param player the player to take the
         * @return the set of all positions
         * @brief Calculate all hulls positions for given player
         * @author malte quandt
         * @version 1.0
         */
        public Set<Position> getPlayerHull(Game game, Color player) {
            // Get all placements for a given player
            Set<Placement> placements = getPlayerPlacements(game, player);
            // Get all the positions for a given player
            Set<Position> positions = new HashSet<>();
            for (Placement placement : placements) {
                // Only take this placement into account if it belongs to the player 'player'
                Set<Position> placementHull = calculateHull(placement, placement.position());
                positions.addAll(placementHull);
            }
            positions.removeAll(this.turnPlayerPositions.get(player));
            return positions;
        }
    }


}

/**
 * Aggregate class for saving the weights for the score function
 */
class WeightContainer {
    // Instance variables
    // ------------------
    public double scoreDeltaWeight;
    public double scoreWeight;
    public double captureWeight;
    public double preventWeight;
    public double opponentWeight;

    // Constructors
    // ------------
    public WeightContainer(double scoreDeltaWeight, double scoreWeight, double captureWeight, double preventWeight, double opponentWeight) {
        this.captureWeight = captureWeight;
        this.opponentWeight = opponentWeight;
        this.preventWeight = preventWeight;
        this.scoreWeight = scoreWeight;
        this.scoreDeltaWeight = scoreDeltaWeight;
    }

    public WeightContainer() {
        this.captureWeight = 0;
        this.opponentWeight = 0;
        this.preventWeight = 0;
        this.scoreWeight = 0;
        this.scoreDeltaWeight = 0;
    }

    /**
     * Fill each of the values with an initial value that is inside the given range
     *
     * @param start the start of the range
     * @param end   the end of the range
     */
    public void fill(double start, double end) {
        this.captureWeight = ThreadLocalRandom.current().nextDouble(start, end + 1);
        this.scoreWeight = ThreadLocalRandom.current().nextDouble(start, end + 1);
        this.scoreDeltaWeight = ThreadLocalRandom.current().nextDouble(start, end + 1);
        this.preventWeight = ThreadLocalRandom.current().nextDouble(start, end + 1);
        this.opponentWeight = ThreadLocalRandom.current().nextDouble(start, end + 1);
    }

    /**
     * Copy the values in the weightcontainer argument and mutate them using the [start, end-1] range of integers by
     * adding that range on top of the initail container values
     *
     * @param container the container to mutate
     */
    public void mutateAdditively(WeightContainer container, double start, double end) {
        this.captureWeight = container.captureWeight + ThreadLocalRandom.current().nextDouble(start, end);
        this.scoreWeight = container.scoreWeight + ThreadLocalRandom.current().nextDouble(start, end);
        this.scoreDeltaWeight = container.scoreDeltaWeight + ThreadLocalRandom.current().nextDouble(start, end);
        this.preventWeight = container.scoreWeight + ThreadLocalRandom.current().nextDouble(start, end);
        this.opponentWeight = container.opponentWeight + ThreadLocalRandom.current().nextDouble(start, end);
    }

    /**
     * Copy the values in the weightcontainer argument and mutate them using the [start, end-1] range of integers by
     * multiplying that range on top of the initail container values
     *
     * @param container the container to mutate
     */
    public void mutateMultiplicatively(WeightContainer container, double start, double end) {
        this.captureWeight = container.captureWeight * ThreadLocalRandom.current().nextDouble(start, end);
        this.scoreWeight = container.scoreWeight * ThreadLocalRandom.current().nextDouble(start, end);
        this.scoreDeltaWeight = container.scoreDeltaWeight * ThreadLocalRandom.current().nextDouble(start, end);
        this.preventWeight = container.scoreWeight * ThreadLocalRandom.current().nextDouble(start, end);
        this.opponentWeight = container.opponentWeight * ThreadLocalRandom.current().nextDouble(start, end);
    }


    @Override
    public String toString() {
        return this.captureWeight + " : " + this.scoreWeight + " : " + this.scoreDeltaWeight + " : " + this.preventWeight + " : " + this.opponentWeight;
    }

}

