import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.uchicago.pairs.PairsHelper.Order;
import org.uchicago.pairs.PairsHelper.OrderState;
import org.uchicago.pairs.PairsHelper.Quote;
import org.uchicago.pairs.PairsHelper.Ticker;
import org.uchicago.pairs.PairsUtils;
import org.uchicago.pairs.core.AbstractPairsCase;
import org.uchicago.pairs.core.PairsInterface;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/**
 * 
 *
 * @author Embert Lin
 * @author Nathan Ro
 * @author Kevin Jin
 * @author Shrey Patel
 */
public class PairsCaseSample extends AbstractPairsCase implements PairsInterface {
	private static final int MINIMUM_CORRELATION_STAGE_TICKS = 30;
	private static final int MAXIMUM_CORRELATION_STAGE_TICKS = 100;

	private static final int MAXIMUM_ABSOLUTE_CONTRACTS = 60;

	private static final double TRIGGER_SIGNAL = 2.05;
	private static final double CLOSE_SIGNAL = 0.3;
	private static final int POSITION_CHANGE_ON_TRIGGER = 15;
	private static final int POSITION_DOUBLE_DOWN_RATE = 5;

	/**
	 * 12, 26 for the first round.
	 * 30, 30 for the second round.
	 */
	private static final int EMA_SHORT = 30, EMA_LONG = 30;

	public static class LinearRegression {
		private final int N;
		private final double alpha, beta;
		private final double R2;
		private final double svar, svar0, svar1;
		private final double correlation;

		/**
		 * Performs a linear regression on the data points <tt>(y[i], x[i])</tt>.
		 * 
		 * @param x
		 *            the values of the predictor variable
		 * @param y
		 *            the corresponding values of the response variable
		 * @throws java.lang.IllegalArgumentException
		 *             if the lengths of the two arrays are not equal
		 */
		public LinearRegression(List<Double> x, List<Double> y) {
			if (x.size() != y.size()) {
				throw new IllegalArgumentException("array lengths are not equal");
			}
			N = x.size();

			// first pass
			double sumx = 0.0, sumy = 0.0, sumx2 = 0.0;
			for (int i = 0; i < N; i++)
				sumx += x.get(i).doubleValue();
			for (int i = 0; i < N; i++)
				sumx2 += x.get(i).doubleValue() * x.get(i).doubleValue();
			for (int i = 0; i < N; i++)
				sumy += y.get(i).doubleValue();
			double xbar = sumx / N;
			double ybar = sumy / N;

			// second pass: compute summary statistics
			double xxbar = 0.0, yybar = 0.0, xybar = 0.0;
			for (int i = 0; i < N; i++) {
				xxbar += (x.get(i).doubleValue() - xbar) * (x.get(i).doubleValue() - xbar);
				yybar += (y.get(i).doubleValue() - ybar) * (y.get(i).doubleValue() - ybar);
				xybar += (x.get(i).doubleValue() - xbar) * (y.get(i).doubleValue() - ybar);
			}
			beta = xybar / xxbar;
			alpha = ybar - beta * xbar;
			correlation = (xybar / (N - 1)) / (Math.sqrt(yybar / (N - 1)) * Math.sqrt(xxbar / (N - 1)));

			// more statistical analysis
			double rss = 0.0; // residual sum of squares
			double ssr = 0.0; // regression sum of squares
			for (int i = 0; i < N; i++) {
				double fit = beta * x.get(i).doubleValue() + alpha;
				rss += (fit - y.get(i).doubleValue()) * (fit - y.get(i).doubleValue());
				ssr += (fit - ybar) * (fit - ybar);
			}

			int degreesOfFreedom = N - 2;
			R2 = ssr / yybar;
			svar = rss / degreesOfFreedom;
			svar1 = svar / xxbar;
			//svar0 = svar / N + xbar * xbar * svar1;
			svar0 = svar * sumx2 / (N * xxbar);
		}

		/**
		 * Returns the <em>y</em>-intercept &alpha; of the best of the best-fit line <em>y</em> = &alpha; + &beta; <em>x</em>.
		 * 
		 * @return the <em>y</em>-intercept &alpha; of the best-fit line <em>y = &alpha; + &beta; x</em>
		 */
		public double intercept() {
			return alpha;
		}

		/**
		 * Returns the slope &beta; of the best of the best-fit line <em>y</em> = &alpha; + &beta; <em>x</em>.
		 * 
		 * @return the slope &beta; of the best-fit line <em>y</em> = &alpha; + &beta; <em>x</em>
		 */
		public double slope() {
			return beta;
		}

		public double correlation() {
			return correlation;
		}

		/**
		 * Returns the coefficient of determination <em>R</em><sup>2</sup>.
		 * 
		 * @return the coefficient of determination <em>R</em><sup>2</sup>, which is a real number between 0 and 1
		 */
		public double R2() {
			return R2;
		}

		/**
		 * Returns the standard error of the estimate for the intercept.
		 * 
		 * @return the standard error of the estimate for the intercept
		 */
		public double interceptStdErr() {
			return Math.sqrt(svar0);
		}

		/**
		 * Returns the standard error of the estimate for the slope.
		 * 
		 * @return the standard error of the estimate for the slope
		 */
		public double slopeStdErr() {
			return Math.sqrt(svar1);
		}

		/**
		 * Returns the expected response <tt>y</tt> given the value of the predictor variable <tt>x</tt>.
		 * 
		 * @param x
		 *            the value of the predictor variable
		 * @return the expected response <tt>y</tt> given the value of the predictor variable <tt>x</tt>
		 */
		public double predict(double x) {
			return beta * x + alpha;
		}

		/**
		 * Returns a string representation of the simple linear regression model.
		 * 
		 * @return a string representation of the simple linear regression model, including the best-fit line and the coefficient of determination <em>R</em><sup>2</sup>
		 */
		public String toString() {
			String s = "";
			s += String.format("%.2f N + %.2f", slope(), intercept());
			return s + "  (R^2 = " + String.format("%.3f", R2()) + ")";
		}
	}

	private static class StockPair {
		public double prevExpMa;
		public final List<Double> ratios = new ArrayList<>();
	}

	//private IDB myDatabase;

	// keeping track of the # of symbols for current round
	private int numSymbols;
	// declare Order[] orders
	private Order[] orders;
	// variables to store current price information
	private double[] currentPrices = new double[5];
	private int[] prevHoldings = new int[5];

	private int orderNum;
	private String prevDecision, prevSignal;
	private StockPair[][] pairs = new StockPair[4][5];
	private boolean beganTrading;
	public double cashAndPnl;
	public int contractsSold;

	private List<List<Double>> prices = new ArrayList<>();

	@Override
	public void addVariables(IJobSetup setup) {
		setup.addVariable("Strategy", "Strategy to use", "string", "one");
	}

	@Override
	public void initializeAlgo(IDB dataBase) {
		String strategy = getStringVar("Strategy");
		if (strategy.contains("one")) {
			beganTrading = false;
			for (int i = 0; i < 4; i++)
				for (int j = i + 1; j < 5; j++)
					pairs[i][j] = new StockPair();
			prices = new ArrayList<>();
			for (int i = 0; i < 5; i++)
				prices.add(new ArrayList<Double>());
		}
	}

	private double getMean(List<Double> data, int start, int end) {
		double sum = 0.0;
		Iterator<Double> iter = data.listIterator(start);
		for (int i = start; i < end; i++)
			sum += iter.next().doubleValue();
		return sum / (end - start);
	}

	private double getVariance(List<Double> data, int start, int end) {
		double mean = getMean(data, start, end);
		double temp = 0;
		Iterator<Double> iter = data.listIterator(start);
		for (int i = start; i < end; i++) {
			double a = iter.next().doubleValue();
			temp += (mean - a) * (mean - a);
		}
		return temp / (end - start - 1);
	}

	@Override
	public void currentSymbols(Ticker[] symbols) {
		String rv = "";
		numSymbols = symbols.length;
		for (Ticker s : symbols) {
			rv = rv + s.name() + " ";
		}
		log("The tickers available for this round is " + rv);
		// initiate Order[]
		orders = PairsUtils.initiateOrders(symbols);
	}

	@Override
	public Order[] getNewQuotes(Quote[] quotes) {
		double[] pricesYest = new double[numSymbols];
		for (int i = 0; i < numSymbols; i++) {
			pricesYest[i] = currentPrices[i];
			currentPrices[i] = (quotes[i].bid + quotes[i].offer) / 2;
			prices.get(i).add(Double.valueOf(currentPrices[i]));
		}
		return generateQuotes(pricesYest);
	}

	private void setPrevExpMa(int i, int j, double expMa) {
		if (i < j)
			pairs[i][j].prevExpMa = expMa;
		else
			pairs[j][i].prevExpMa = expMa;
	}

	private double getPrevExpMa(int i, int j) {
		if (i < j)
			return pairs[i][j].prevExpMa;
		else
			return pairs[j][i].prevExpMa;
	}

	private List<Double> getRatios(int i, int j) {
		if (i < j)
			return pairs[i][j].ratios;
		else
			return pairs[j][i].ratios;
	}

	private void addRatio(int i, int j, double r) {
		getRatios(i, j).add(Double.valueOf(r));
	}

	public static class FindBestPairResult {
		public static final FindBestPairResult NONE = new FindBestPairResult(Double.NEGATIVE_INFINITY, Collections.<int[]>emptyList());

		public final double sumAbsoluteZScores;
		public final List<int[]> combination;

		public FindBestPairResult(double sumAbsoluteZScores, List<int[]> combination) {
			this.sumAbsoluteZScores = sumAbsoluteZScores;
			this.combination = new ArrayList<>(combination);
		}
	}

	public static FindBestPairResult findBestPair(List<int[]> validPairs, List<int[]> combination, int iGreaterThan, int pick, double[][] zScores) {
		if (pick == 0) {
			Set<Integer> stocksUsed = new HashSet<>();
			double sumAbsoluteZScores = 0;
			for (int[] pair : combination) {
				if (!stocksUsed.add(pair[0]) || !stocksUsed.add(pair[1])) {
					//invalid pair
					return FindBestPairResult.NONE;
				}
				sumAbsoluteZScores += Math.abs(zScores[pair[0]][pair[1]]);
			}
			return new FindBestPairResult(sumAbsoluteZScores, combination);
		}

		FindBestPairResult bestResult = FindBestPairResult.NONE;
		for (int i = iGreaterThan; i < validPairs.size() - pick + 1; i++) {
			combination.add(validPairs.get(i));
			FindBestPairResult result = findBestPair(validPairs, combination, i + 1, pick - 1, zScores);
			if (result.sumAbsoluteZScores > bestResult.sumAbsoluteZScores)
				bestResult = result;
			combination.remove(combination.size() - 1);
		}
		return bestResult;
	}

	public static FindBestPairResult findBestPair(List<int[]> validPairs, int numTickers, double[][] zScores) {
		FindBestPairResult bestResult = FindBestPairResult.NONE;
		for (int i = 1; i <= numTickers / 2; i++) {
			FindBestPairResult result = PairsCaseSample.findBestPair(validPairs, new ArrayList<int[]>(), 0, i, zScores);
			if (result.sumAbsoluteZScores > bestResult.sumAbsoluteZScores)
				bestResult = result;
		}
		return bestResult;
	}

	public Order[] generateQuotes(double[] pricesYest) {
		LinearRegression[][] regs = new LinearRegression[numSymbols - 1][numSymbols];
		for (int i = 0; i < numSymbols - 1; i++) {
			for (int j = i + 1; j < numSymbols; j++) {
				regs[i][j] = new LinearRegression(prices.get(i), prices.get(j));
				addRatio(i, j, currentPrices[j] / currentPrices[i]);
			}
		}

		orderNum++;
		if (orderNum < 2)
			return orders;
		assert MINIMUM_CORRELATION_STAGE_TICKS > 1;
		if (orderNum < MINIMUM_CORRELATION_STAGE_TICKS)
			return orders;

		/** column C */ double ratio;
		/** column D */ double thisExpMa;
		boolean wasTrading = beganTrading;
		if (orderNum >= MAXIMUM_CORRELATION_STAGE_TICKS)
			beganTrading = true;
		else
			return orders;

		//if the ratio of the price of Y over the price of X diverges too much
		//from the exponential moving average, then we expect the ratio to
		//mean revert over time
		double[][] zScore = new double[numSymbols - 1][numSymbols];
		List<int[]> validPairs = new ArrayList<>();
		for (int i = 0; i < numSymbols - 1; i++) {
			for (int j = i + 1; j < numSymbols; j++) {
				ratio = currentPrices[j] / currentPrices[i];
				if (!wasTrading) {
					//initial exponential moving average is mean of ratios preceding this one
					thisExpMa = getMean(getRatios(i, j), getRatios(i, j).size() - 1 - EMA_SHORT, getRatios(i, j).size() - 1);
				} else {
					thisExpMa = (ratio - getPrevExpMa(i, j)) * 2 / (EMA_SHORT + 1) + getPrevExpMa(i, j);
				}
				if (regs[i][j].correlation() > 0) {
					/** column F */ double stdev = Math.sqrt(getVariance(getRatios(i, j), getRatios(i, j).size() - 1 - EMA_LONG - 2, getRatios(i, j).size() - 1));
					/** column G */ zScore[i][j] = (ratio - thisExpMa) / stdev;

					validPairs.add(new int[] { i, j });
					setPrevExpMa(i, j, thisExpMa);
				} else {
					//TODO: negative correlation strategy doesn't work with price ratios
				}
			}
		}
		if (validPairs.size() == 0)
			return orders;

		for (int[] pair : findBestPair(validPairs, numSymbols, zScore).combination) {
			int useX = pair[0];
			int useY = pair[1];
			double useZScore = zScore[useX][useY];

			System.out.println("USING PAIR " + useX + ", " + useY);
			/** column H */ String buyOrSell;
			if (useZScore > TRIGGER_SIGNAL)
				buyOrSell = "sellY";
			else if (useZScore < -TRIGGER_SIGNAL)
				buyOrSell = "buyY";
			else
				buyOrSell = "";

			/** column J */ String thisSignal;
			if (("sellY".equals(prevDecision) || "sellhold".equals(prevSignal)) && useZScore > CLOSE_SIGNAL)
				thisSignal = "sellhold";
			else if (("sellY".equals(prevDecision) || "sellhold".equals(prevSignal)) && useZScore < CLOSE_SIGNAL)
				thisSignal = "close";
			else if (("buyY".equals(prevDecision) || "buyhold".equals(prevSignal)) && useZScore < -CLOSE_SIGNAL)
				thisSignal = "buyhold";
			else if (("buyY".equals(prevDecision) || "buyhold".equals(prevSignal)) && useZScore > -CLOSE_SIGNAL)
				thisSignal = "close";
			else
				thisSignal = "";

			/** column L */ int thisDoubleDownX;
			int steps;
			if (useZScore > TRIGGER_SIGNAL + 0.5 && prevHoldings[useX] <= 10)
				steps = Math.min(2, (int) ((useZScore - TRIGGER_SIGNAL) / 0.5));
			else if (useZScore < -TRIGGER_SIGNAL - 0.5 && prevHoldings[useX] >= -10)
				steps = -Math.min(2, (int) ((useZScore - TRIGGER_SIGNAL) / 0.5));
			else
				steps = 0;
			thisDoubleDownX = steps * POSITION_DOUBLE_DOWN_RATE;

			/** column K */ int thisHoldingsX;
			if ("sellY".equals(buyOrSell))
				thisHoldingsX = Math.max(POSITION_CHANGE_ON_TRIGGER + thisDoubleDownX, prevHoldings[useX]);
			else if ("buyY".equals(buyOrSell))
				thisHoldingsX = Math.min(-POSITION_CHANGE_ON_TRIGGER + thisDoubleDownX, prevHoldings[useX]);
			else if ("sellhold".equals(thisSignal))
				thisHoldingsX = thisDoubleDownX + prevHoldings[useX];
			else if ("buyhold".equals(thisSignal))
				thisHoldingsX = thisDoubleDownX + prevHoldings[useX];
			else
				thisHoldingsX = 0;

			/** column O */ int thisDoubleDownY;
			if (useZScore > TRIGGER_SIGNAL + 0.5 && prevHoldings[useY] <= 10)
				steps = -Math.min(2, (int) ((useZScore - TRIGGER_SIGNAL) / 0.5));
			else if (useZScore < -TRIGGER_SIGNAL - 0.5 && prevHoldings[useY] >= -10)
				steps = Math.min(2, (int) ((useZScore - TRIGGER_SIGNAL) / 0.5));
			else
				steps = 0;
			thisDoubleDownY = steps * POSITION_DOUBLE_DOWN_RATE;

			/** column N */ int thisHoldingsY;
			if ("sellY".equals(buyOrSell))
				thisHoldingsY = Math.min(-POSITION_CHANGE_ON_TRIGGER + thisDoubleDownY, prevHoldings[useY]);
			else if ("buyY".equals(buyOrSell))
				thisHoldingsY = Math.max(POSITION_CHANGE_ON_TRIGGER + thisDoubleDownY, prevHoldings[useY]);
			else if ("sellhold".equals(thisSignal))
				thisHoldingsY = thisDoubleDownY + prevHoldings[useY];
			else if ("buyhold".equals(thisSignal))
				thisHoldingsY = thisDoubleDownY + prevHoldings[useY];
			else
				thisHoldingsY = 0;

			/** column Q */ int absoluteContracts = Math.abs(thisHoldingsX) + Math.abs(thisHoldingsY);
			int tooMany = absoluteContracts - MAXIMUM_ABSOLUTE_CONTRACTS;
			if (tooMany > 0) {
				if (thisHoldingsY < 0) {
					thisHoldingsY += tooMany / 2;
					thisHoldingsX -= (tooMany + 1) / 2;
				} else {
					thisHoldingsY -= tooMany / 2;
					thisHoldingsX += (tooMany + 1) / 2;
				}
				absoluteContracts = Math.abs(thisHoldingsX) + Math.abs(thisHoldingsY);
			}

			/** column R */ int change = thisHoldingsX - prevHoldings[useX];
			/** column S */ double cashFlow = change * (currentPrices[useY] - currentPrices[useX]);
			/** column T */ cashAndPnl += cashFlow;
			contractsSold += Math.abs(thisHoldingsX - prevHoldings[useX]) + Math.abs(thisHoldingsY - prevHoldings[useY]);

			prevSignal = thisSignal;
			prevDecision = buyOrSell;
			prevHoldings[useX] = thisHoldingsX;
			prevHoldings[useY] = thisHoldingsY;

			log("Tick " + (orderNum + 1) + ": " + useX + " holdings: " + thisHoldingsX + ", " + useY + " holdings: " + thisHoldingsY + ", PnL: " + (cashAndPnl - contractsSold / 2) + ", bid:ask fee: " + contractsSold / 2);
			orders[useX].quantity = thisHoldingsX;
			orders[useY].quantity = thisHoldingsY;
		}
		return orders;
	}

	@Override
	public void ordersConfirmation(Order[] orders) {
		for (Order o : orders) {
			if (o.state != OrderState.FILLED) {
				if (o.state == OrderState.REJECTED) {
					log("My order for " + o.ticker + " is rejected, time to check my position/limit");
				}
			} else {
				log("My order for " + o.ticker + " is filled");
			}
		}
	}

	@Override
	public PairsInterface getImplementation() {
		return this;
	}
}
