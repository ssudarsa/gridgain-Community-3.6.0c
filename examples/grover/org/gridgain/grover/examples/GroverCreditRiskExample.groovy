// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * _________
 * __  ____/______________ ___   _______ ________
 * _  / __  __  ___/_  __ \__ | / /_  _ \__  ___/
 * / /_/ /  _  /    / /_/ /__ |/ / /  __/_  /
 * \____/   /_/     \____/ _____/  \___/ /_/
 *
 */

package org.gridgain.grover.examples

import org.gridgain.grid.*
import static org.gridgain.grid.GridClosureCallMode.*
import org.gridgain.grid.lang.*
import static org.gridgain.grover.Grover.*
import org.gridgain.grover.categories.*

/**
 * Monte-Carlo example based on GridGain 3.0 API.
 * <p>
 * <h1 class="header">Starting Remote Nodes</h1>
 * To try this example you should (but don't have to) start remote grid instances.
 * You can start as many as you like by executing the following script:
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh}</pre>
 * Once remote instances are started, you can execute this example from
 * Eclipse, IntelliJ IDEA, or NetBeans (and any other Java IDE) by simply hitting run
 * button. You will see that all nodes discover each other and
 * all of the nodes will participate in task execution (check node
 * output).
 * <p>
 * <h1 class="header">XML Configuration</h1>
 * If no specific configuration is provided, GridGain will start with
 * all defaults. For information about GridGain default configuration
 * refer to {@link org.gridgain.grid.GridFactory} documentation. If you would like to
 * try out different configurations you should pass a path to Spring
 * configuration file as 1st command line argument into this example.
 * The path can be relative to {@code GRIDGAIN_HOME} environment variable.
 * You should also pass the same configuration file to all other
 * grid nodes by executing startup script as follows (you will need
 * to change the actual file name):
 * <pre class="snippet">{GRIDGAIN_HOME}/bin/ggstart.{bat|sh} examples/config/specific-config-file.xml</pre>
 * <p>
 * GridGain examples come with multiple configuration files you can try.
 * All configuration files are located under {@code GRIDGAIN_HOME/examples/config}
 * folder.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
@Use(GroverProjectionCategory)
class GroverCreditRiskExample {
    /** Ensure singleton. */
    private GridCreditRiskExample() {
        // No-op.
    }

    /**
     * @param args Command arguments.
     */
    static void main(String[] args) {
        grover { Grid grid ->
            // Create portfolio.
            def portfolio = new GridCredit[5000]

            def rnd = new Random()

            // Generate some test portfolio items.
            (0 ..< portfolio.length).each {
                portfolio[it] = new GridCredit(
                    50000 * rnd.nextDouble(), // Credit amount.
                    rnd.nextInt(1000), // Credit term in days.
                    rnd.nextDouble() / 10, // APR.
                    rnd.nextDouble() / 20 + 0.02 // EDF.
                )
            }

            // Forecast horizon in days.
            def horizon = 365

            // Number of Monte-Carlo iterations.
            def iter = 10000

            // Percentile.
            def percentile = 0.95d

            // Mark the stopwatch.
            def start = System.currentTimeMillis()

            // Calculate credit risk and print it out.
            // As you can see the grid enabling is completely hidden from the caller
            // and it is fully transparent to him. In fact, the caller is never directly
            // aware if method was executed just locally or on the 100s of grid nodes.
            // Credit risk crdRisk is the minimal amount that creditor has to have
            // available to cover possible defaults.
            def crdRisk = grid.reduce$(
                SPREAD,
                closures(grid.size(), portfolio, horizon, iter, percentile),
                { c -> ((Double)c.sum()) / c.size() }
            )

            println("Credit risk [crdRisk=" + crdRisk + ", duration=" +
                (System.currentTimeMillis() - start) + "ms]")
        }
    }

    /**
     * Creates closures for calculating credit risks.
     *
     * @param gridSize Size of the grid.
     * @param portfolio Portfolio.
     * @param horizon Forecast horizon in days.
     * @param iter Number of Monte-Carlo iterations.
     * @param percentile Percentile.
     * @return Collection of closures.
     */
    private static Collection<GridOutClosure<Double>> closures(int gridSize, final GridCredit[] portfolio,
        final int horizon, int iter, final double percentile) {
        // Number of iterations should be done by each node.
        def iterPerNode = Math.round(iter / (float)gridSize)

        // Number of iterations for the last/the only node.
        def lastNodeIter = iter - (gridSize - 1) * iterPerNode

        def cls = new ArrayList<GridOutClosure<Double>>(gridSize)

        // Note that for the purpose of this example we perform a simple homogeneous
        // (non weighted) split assuming that all computing resources in this split
        // will be identical. In real life scenarios when heterogeneous environment
        // is used a split that is weighted by, for example, CPU benchmarks of each
        // node in the split will be more efficient. It is fairly easy addition and
        // GridGain comes with convenient Spring-compatible benchmark that can be
        // used for weighted splits.
        (0 ..< gridSize).each {
            final int nodeIter = it == gridSize - 1 ? lastNodeIter : iterPerNode

            GridOutClosure<Double> c = { ->
                new GridCreditRiskManager().calculateCreditRiskMonteCarlo(
                    portfolio, horizon, nodeIter, percentile)
            }

            cls.add(c)
        }

        cls
    }

    /**
     * This class abstracts out the calculation of risk for a credit portfolio.
     *
     * @author 2012 Copyright (C) GridGain Systems
     * @version 3.6.0c.13012012
     */
    @Typed
    private static class GridCreditRiskManager {
        /**
         * Default randomizer with normal distribution.
         * Note that since every JVM on the grid will have its own random
         * generator (independently initialized) the Monte-Carlo simulation
         * will be slightly skewed when performed on the grid due to skewed
         * normal distribution of the sub-jobs comparing to execution on the
         * local node only with single random generator. Real-life applications
         * may want to provide its own implementation of distributed random
         * generator.
         */
        private Random rndGen = new Random()

        /**
         * Calculates credit risk for a given credit portfolio. This calculation uses
         * Monte-Carlo Simulation to produce risk value.
         * <p>
         * Note that this class generally represents a business logic and the entire
         * grid enabling occurs in one line of annotation added to this method:
         * <pre name="code" class="java">
         * ...
         * &#64Gridify(taskClass = GridCreditRiskGridTask.class)
         * ...
         * </pre>
         * Note also that this annotation could have been added externally via XML
         * file leaving this file completely untouched - yet still fully grid enabled.
         *
         * @param portfolio Credit portfolio.
         * @param horizon Forecast horizon (in days).
         * @param num Number of Monte-Carlo iterations.
         * @param percentile Cutoff level.
         * @return Credit risk value, i.e. the minimal amount that creditor has to
         *      have available to cover possible defaults.
         */
        double calculateCreditRiskMonteCarlo(GridCredit[] portfolio, int horizon, int num, double percentile) {
            println(">>> Calculating credit risk for portfolio [size=" + portfolio.length + ", horizon=" +
                horizon + ", percentile=" + percentile + ", iterations=" + num + "] <<<")

            def start = System.currentTimeMillis()

            def losses = calculateLosses(portfolio, horizon, num).sort()

            List<Double> lossProbs = []

            // Count variational numbers.
            // Every next one either has the same value or previous one plus probability of loss.
            (0 ..< losses.size()).each {
                if (it == 0)
                    // First time it's just a probability of first value.
                    lossProbs[it] = getLossProbability(losses, 0)
                else if (losses[it] != losses[it - 1])
                    // Probability of this loss plus previous one.
                    lossProbs[it] = getLossProbability(losses, it) + lossProbs[it - 1]
                else
                    // The same loss the same probability.
                    lossProbs[it] = lossProbs[it - 1]
            }

            // Count percentile.
            def crdRiskIdx = (0 ..< lossProbs.size()).find {
                lossProbs[it] > percentile
            } - 1

            def crdRisk = losses[crdRiskIdx]

            println(">>> Finished calculating portfolio risk [risk=" + crdRisk +
                ", time=" + (System.currentTimeMillis() - start) + "ms]")

            crdRisk
        }

        /**
         * Calculates losses for the given credit portfolio using Monte-Carlo Simulation.
         * Simulates probability of default only.
         *
         * @param portfolio Credit portfolio.
         * @param horizon Forecast horizon.
         * @param num Number of Monte-Carlo iterations.
         * @return Losses array simulated by Monte Carlo method.
         */
        private List<Double> calculateLosses(GridCredit[] portfolio, int horizon, int num) {
            List<Double> losses = []

            // Count losses using Monte-Carlo method. We generate random probability of default,
            // if it exceeds certain credit default value we count losses - otherwise count income.
            (0 ..< num).each { i ->
                losses.add(0)

                portfolio.each { crd ->
                    def remDays = Math.min(crd.remainingTerm, horizon)

                    if (rndGen.nextDouble() >= 1 - crd.getDefaultProbability(remDays))
                        // (1 + 'r' * min(H, W) / 365) * S.
                        // Where W is a horizon, H is a remaining crediting term, 'r' is an annual credit rate,
                        // S is a remaining credit amount.
                        losses[i] = losses[i] + (1 + crd.annualRate * Math.min(horizon, crd.remainingTerm) / 365) *
                            crd.remainingAmount
                    else
                        // - 'r' * min(H,W) / 365 * S
                        // Where W is a horizon, H is a remaining crediting term, 'r' is a annual credit rate,
                        // S is a remaining credit amount.
                        losses[i] = losses[i] - crd.annualRate * Math.min(horizon, crd.remainingTerm) / 365 *
                            crd.remainingAmount
                }
            }

            losses
        }

        /**
         * Calculates probability of certain loss in array of losses.
         *
         * @param losses Array of losses.
         * @param i Index of certain loss in array.
         * @return Probability of loss with given index.
         */
        private double getLossProbability(List<Double> losses, int i) {
            def count = 0.0d
            def loss = losses[i]

            for (def tmp : losses)
                if (loss == tmp)
                    count++

            count / losses.size()
        }
    }
}

/**
 * This class provides a simple model for a credit contract (or a loan). It is basically
 * defines as remaining crediting amount to date, credit remaining term, APR and annual
 * probability on default. Although this model is simplified for the purpose
 * of this example, it is close enough to emulate the real-life credit
 * risk assessment application.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */
@Typed
private class GridCredit implements Serializable {
    /** Remaining crediting amount. */
    private final double remAmnt

    /** Remaining crediting remTerm. */
    private final int remTerm

    /** Annual percentage rate (APR). */
    private final double apr

    /** Expected annual probability of default (EaDF). */
    private final double edf

    /**
     * Creates new credit instance with given information.
     *
     * @param remAmnt Remained crediting amount.
     * @param remTerm Remained crediting remTerm.
     * @param apr Annual percentage rate (APR).
     * @param edf Expected annual probability of default (EaDF).
     */
    GridCredit(double remAmnt, int remTerm, double apr, double edf) {
        this.remAmnt = remAmnt
        this.remTerm = remTerm
        this.apr = apr
        this.edf = edf
    }

    /**
     * Gets remained crediting amount.
     *
     * @return Remained amount of credit.
     */
    double getRemainingAmount() {
        remAmnt
    }

    /**
     * Gets remained crediting remTerm.
     *
     * @return Remained crediting remTerm in days.
     */
    int getRemainingTerm() {
        remTerm
    }

    /**
     * Gets annual percentage rate.
     *
     * @return Annual percentage rate in relative percents (percentage / 100).
     */
    double getAnnualRate() {
        apr
    }

    /**
     * Gets either credit probability of default for the given period of time
     * if remaining term is less than crediting time or probability of default
     * for whole remained crediting time.
     *
     * @param term Default term.
     * @return Credit probability of default in relative percents
     *     (percentage / 100).
     */
    double getDefaultProbability(int term) {
        1 - Math.exp(Math.log(1 - edf) * Math.min(remTerm, term) / 365.0)
    }
}
