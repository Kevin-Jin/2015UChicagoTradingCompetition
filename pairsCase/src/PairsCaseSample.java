import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

	private static final int MAXIMUM_ABSOLUTE_CONTRACTS = 40;

	private static final double TRIGGER_SIGNAL = 2.05;
	private static final double CLOSE_SIGNAL = 0.3;
	private static final int POSITION_CHANGE_ON_TRIGGER = 15;
	private static final int POSITION_DOUBLE_DOWN_RATE = 5;

	private static final int EMA_SHORT = 12, EMA_LONG = 26;

	public static class LinearRegression {
		private final int N;
		private final double alpha, beta;
		private final double R2;
		private final double svar, svar0, svar1;

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

	//private IDB myDatabase;

	// keeping track of the # of symbols for current round
	private int numSymbols;
	// declare Order[] orders
	private Order[] orders;
	// variables to store current price information
	private double priceHuron, priceSuperior, priceMichigan, priceOntario, priceErie;

	private int orderNum;
	private String prevDecision, prevSignal;
	private double prevExpMa;
	private boolean beganTrading;
	public int prevHoldingsHuron, prevHoldingsSuperior;
	public double cashAndPnl;
	public int contractsSold;

	private List<Double> ratios = new ArrayList<Double>();

	@Override
	public void addVariables(IJobSetup setup) {
		setup.addVariable("Strategy", "Strategy to use", "string", "one");
	}

	@Override
	public void initializeAlgo(IDB dataBase) {
		String strategy = getStringVar("Strategy");
		if (strategy.contains("one")) {
			beganTrading = false;
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
		if (numSymbols == 2) {
			double priceHuronYest = priceHuron;
			double priceSuperiorYest = priceSuperior;
			priceHuron = (quotes[0].bid + quotes[0].offer) / 2;
			priceSuperior = (quotes[1].bid + quotes[1].offer) / 2;
			return roundOneStrategy(priceHuronYest, priceSuperiorYest);
		} else if (numSymbols == 3) {
			priceHuron = (quotes[0].bid + quotes[0].offer) / 2;
			priceSuperior = (quotes[1].bid + quotes[1].offer) / 2;
			priceMichigan = (quotes[2].bid + quotes[2].offer) / 2;
			return roundTwoStrategy(priceHuron, priceSuperior, priceMichigan);
		} else {
			priceHuron = (quotes[0].bid + quotes[0].offer) / 2;
			priceSuperior = (quotes[1].bid + quotes[1].offer) / 2;
			priceMichigan = quotes[2].bid;
			priceOntario = quotes[3].bid;
			priceErie = quotes[4].bid;
			return roundThreeStrategy(priceHuron, priceSuperior, priceMichigan, priceOntario, priceErie);
		}
	}

	public Order[] roundOneStrategy(/** column A */ double priceHuronYest, /** column C */ double priceSuperiorYest) {
		orderNum++;
		/** column C */ double ratio = priceSuperior / priceHuron;
		ratios.add(Double.valueOf(ratio));
		if (orderNum < 2)
			return orders;

		assert MINIMUM_CORRELATION_STAGE_TICKS > 1;
		if (orderNum < MINIMUM_CORRELATION_STAGE_TICKS)
			return orders;

		/** column D */ double thisExpMa;
		if (!beganTrading) {
			if (orderNum >= MAXIMUM_CORRELATION_STAGE_TICKS) {
				beganTrading = true;
				//initial exponential moving average is mean of ratios preceding this one
				thisExpMa = getMean(ratios, ratios.size() - 1 - EMA_SHORT, ratios.size() - 1);
			} else {
				return orders;
			}
		} else {
			thisExpMa = (ratio - prevExpMa) * 2 / (EMA_SHORT + 1) + prevExpMa;
		}

		/** column F */ double stdev = Math.sqrt(getVariance(ratios, ratios.size() - 1 - EMA_LONG - 2, ratios.size() - 1));
		/** column G */ double zScore = (ratio - thisExpMa) / stdev;
		/** column H */ String buyOrSell;
		if (zScore > TRIGGER_SIGNAL)
			buyOrSell = "sellY";
		else if (zScore < -TRIGGER_SIGNAL)
			buyOrSell = "buyY";
		else
			buyOrSell = "";

		/** column J */ String thisSignal;
		if (("sellY".equals(prevDecision) || "sellhold".equals(prevSignal)) && zScore > CLOSE_SIGNAL)
			thisSignal = "sellhold";
		else if (("sellY".equals(prevDecision) || "sellhold".equals(prevSignal)) && zScore < CLOSE_SIGNAL)
			thisSignal = "close";
		else if (("buyY".equals(prevDecision) || "buyhold".equals(prevSignal)) && zScore < -CLOSE_SIGNAL)
			thisSignal = "buyhold";
		else if (("buyY".equals(prevDecision) || "buyhold".equals(prevSignal)) && zScore > -CLOSE_SIGNAL)
			thisSignal = "close";
		else
			thisSignal = "";

		/** column L */ int thisDoubleDownHuron;
		int steps;
		if (zScore > TRIGGER_SIGNAL + 0.5 && prevHoldingsHuron <= 10)
			steps = Math.min(2, (int) ((zScore - 2) / 0.5));
		else if (zScore < -TRIGGER_SIGNAL - 0.5 && prevHoldingsHuron >= -10)
			steps = -Math.min(2, (int) ((zScore - 2) / 0.5));
		else
			steps = 0;
		thisDoubleDownHuron = steps * POSITION_DOUBLE_DOWN_RATE;

		/** column K */ int thisHoldingsHuron;
		if ("sellY".equals(buyOrSell))
			thisHoldingsHuron = Math.max(POSITION_CHANGE_ON_TRIGGER + thisDoubleDownHuron, prevHoldingsHuron);
		else if ("buyY".equals(buyOrSell))
			thisHoldingsHuron = Math.min(-POSITION_CHANGE_ON_TRIGGER + thisDoubleDownHuron, prevHoldingsHuron);
		else if ("sellhold".equals(thisSignal))
			thisHoldingsHuron = thisDoubleDownHuron + prevHoldingsHuron;
		else if ("buyhold".equals(thisSignal))
			thisHoldingsHuron = thisDoubleDownHuron + prevHoldingsHuron;
		else
			thisHoldingsHuron = 0;

		/** column O */ int thisDoubleDownSuperior;
		if (zScore > TRIGGER_SIGNAL + 0.5 && prevHoldingsSuperior <= 10)
			steps = -Math.min(2, (int) ((zScore - 2) / 0.5));
		else if (zScore < -TRIGGER_SIGNAL - 0.5 && prevHoldingsSuperior >= -10)
			steps = Math.min(2, (int) ((zScore - 2) / 0.5));
		else
			steps = 0;
		thisDoubleDownSuperior = steps * POSITION_DOUBLE_DOWN_RATE;

		/** column N */ int thisHoldingsSuperior;
		if ("sellY".equals(buyOrSell))
			thisHoldingsSuperior = Math.min(-POSITION_CHANGE_ON_TRIGGER + thisDoubleDownSuperior, prevHoldingsSuperior);
		else if ("buyY".equals(buyOrSell))
			thisHoldingsSuperior = Math.max(POSITION_CHANGE_ON_TRIGGER + thisDoubleDownSuperior, prevHoldingsSuperior);
		else if ("sellhold".equals(thisSignal))
			thisHoldingsSuperior = thisDoubleDownSuperior + prevHoldingsSuperior;
		else if ("buyhold".equals(thisSignal))
			thisHoldingsSuperior = thisDoubleDownSuperior + prevHoldingsSuperior;
		else
			thisHoldingsSuperior = 0;

		/** column Q */ int absoluteContracts = Math.abs(thisHoldingsHuron) + Math.abs(thisHoldingsSuperior);
		int tooMany = absoluteContracts - MAXIMUM_ABSOLUTE_CONTRACTS;
		if (tooMany > 0) {
			if (thisHoldingsSuperior < 0) {
				thisHoldingsSuperior += tooMany / 2;
				thisHoldingsHuron -= tooMany / 2;
			} else {
				thisHoldingsSuperior -= tooMany / 2;
				thisHoldingsHuron += tooMany / 2;
			}
			absoluteContracts = Math.abs(thisHoldingsHuron) + Math.abs(thisHoldingsSuperior);
		}

		/** column R */ int change = thisHoldingsHuron - prevHoldingsHuron;
		/** column S */ double cashFlow = change * (priceSuperior - priceHuron);
		/** column T */ cashAndPnl += cashFlow;
		contractsSold += Math.abs(thisHoldingsHuron - prevHoldingsHuron) + Math.abs(thisHoldingsSuperior - prevHoldingsSuperior);

		prevSignal = thisSignal;
		prevDecision = buyOrSell;
		prevHoldingsHuron = thisHoldingsHuron;
		prevHoldingsSuperior = thisHoldingsSuperior;
		prevExpMa = thisExpMa;

		log("Tick " + (orderNum + 1) + ": huron holdings: " + thisHoldingsHuron + ", superior holdings: " + thisHoldingsSuperior + ", PnL: " + (cashAndPnl - contractsSold / 2) + ", bid:ask fee: " + contractsSold / 2);
		orders[0].quantity = thisHoldingsHuron;
		orders[1].quantity = thisHoldingsSuperior;
		return orders;
	}

	// helper function that implements a dummy strategy for round 2
	public Order[] roundTwoStrategy(double priceHuron, double priceSuperior, double priceMichigan) {
		if (Math.abs(priceHuron - priceSuperior) > 5) {
			if (priceHuron > priceSuperior) {
				orders[0].quantity = -2;
				orders[1].quantity = 2;
				orders[2].quantity = 1;
			} else {
				orders[0].quantity = 2;
				orders[1].quantity = -2;
				orders[2].quantity = -1;
			}
			return orders;
		} else if (Math.abs(priceHuron - priceSuperior) < 1) {
			if (priceHuron > priceSuperior) {
				orders[0].quantity = 2;
				orders[1].quantity = -2;
				orders[2].quantity = -1;
			} else {
				orders[0].quantity = -2;
				orders[1].quantity = 2;
				orders[2].quantity = 1;
			}
			return orders;
		} else {
			orders[0].quantity = 0;
			orders[1].quantity = 0;
			orders[2].quantity = 0;
		}
		return orders;
	}

	// helper function that implements a dummy strategy for round 2
	public Order[] roundThreeStrategy(double priceHuron, double priceSuperior, double priceMichigan, double priceOntario, double priceErie) {
		return orders;
	}

	@Override
	public void ordersConfirmation(Order[] orders) {
		for (Order o : orders) {
			if (o.state != OrderState.FILLED) {
				if (o.state == OrderState.REJECTED) {
					log("My order for " + o.ticker + "is rejected, time to check my position/limit");
				}
			} else {
				log("My order for " + o.ticker + "is filled");
			}
		}
	}

	@Override
	public PairsInterface getImplementation() {
		return this;
	}
}
