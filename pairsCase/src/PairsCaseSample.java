import java.util.ArrayList;
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
	private int prevNetChangeHuron, prevNetChangeSuperior;
	private double cashAndPnl;

	private List<Double> returnsHuron = new ArrayList<Double>(), returnsSuperior = new ArrayList<Double>(), differences = new ArrayList<Double>();

	@Override
	public void addVariables(IJobSetup setup) {
		setup.addVariable("Strategy", "Strategy to use", "string", "one");
	}

	@Override
	public void initializeAlgo(IDB dataBase) {
		String strategy = getStringVar("Strategy");
		if (strategy.contains("one")) {
			// do strategy one
		}
	}

	/*private double getR(List<Double> data1, List<Double> data2) {
		double result = 0;
		double sum_sq_x = 0;
		double sum_sq_y = 0;
		double sum_coproduct = 0;
		double mean_x = data1.get(0);
		double mean_y = data2.get(0);
		for (int i = 2; i < data1.size() + 1; i += 1) {
			double sweep = Double.valueOf(i - 1) / i;
			double delta_x = data1.get(i - 1) - mean_x;
			double delta_y = data2.get(i - 1) - mean_y;
			sum_sq_x += delta_x * delta_x * sweep;
			sum_sq_y += delta_y * delta_y * sweep;
			sum_coproduct += delta_x * delta_y * sweep;
			mean_x += delta_x / i;
			mean_y += delta_y / i;
		}
		double pop_sd_x = (double) Math.sqrt(sum_sq_x / data1.size());
		double pop_sd_y = (double) Math.sqrt(sum_sq_y / data1.size());
		double cov_x_y = sum_coproduct / data1.size();
		result = cov_x_y / (pop_sd_x * pop_sd_y);
		return result;
	}*/

	private double getMean(List<Double> data) {
		double sum = 0.0;
		for (double a : data)
			sum += a;
		return sum / data.size();
	}

	private double getVariance(List<Double> data) {
		double mean = getMean(data);
		double temp = 0;
		for (double a : data)
			temp += (mean - a) * (mean - a);
		return temp / (data.size() - 1);
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
		if (orderNum < 2)
			return orders;

		/** column B */ double percentChangeHuron = (priceHuron - priceHuronYest) / priceHuronYest;
		/** column D */ double percentChangeSuperior = (priceSuperior - priceSuperiorYest) / priceSuperiorYest;
		returnsHuron.add(Double.valueOf(percentChangeHuron));
		returnsSuperior.add(Double.valueOf(percentChangeSuperior));
		if (orderNum < 31)
			return orders;

		LinearRegression reg = new LinearRegression(returnsHuron, returnsSuperior);
		/** column K */ double pred = reg.intercept() + percentChangeHuron * reg.slope();
		/** column E */ double difference = Double.valueOf(percentChangeSuperior - pred);
		differences.add(Double.valueOf(difference));
		if (orderNum < 32)
			return orders;

		/** column F */ double stdif = Math.sqrt(getVariance(differences));
		/** column L */ double zScore = differences.get(differences.size() - 1).doubleValue() / stdif;
		/** column O */ String thisDecision;
		if (zScore > 1.5)
			thisDecision = "sellY";
		else if (zScore < -1.5)
			thisDecision = "buyY";
		else
			thisDecision = "";

		/** column P */ String thisSignal;
		if (("sellY".equals(prevDecision) || "sellhold".equals(prevSignal)) && zScore > 0.5)
			thisSignal = "sellhold";
		else if (("sellY".equals(prevDecision) || "sellhold".equals(prevSignal)) && zScore < 0.5 && !"buyY".equals(thisDecision))
			thisSignal = "close";
		else if (("sellY".equals(prevDecision) || "sellhold".equals(prevSignal)) && zScore < 0.5 && "buyY".equals(thisDecision))
			thisSignal = "close&buy";
		else if (("buyY".equals(prevDecision) || "buyhold".equals(prevSignal)) && zScore < -0.5)
			thisSignal = "buyhold";
		else if (("buyY".equals(prevDecision) || "buyhold".equals(prevSignal)) && zScore > -0.5 && !"sellY".equals(thisDecision))
			thisSignal = "close";
		else if (("buyY".equals(prevDecision) || "buyhold".equals(prevSignal)) && zScore > -0.5 && "sellY".equals(thisDecision))
			thisSignal = "close&sell";
		else
			thisSignal = null;

		/** column R */ int thisDoubleDownHuron;
		if (zScore > 2.5 && prevNetChangeHuron < 15)
			thisDoubleDownHuron = 10;
		else if (zScore > 2 && prevNetChangeHuron < 15)
			thisDoubleDownHuron = 5;
		else if (zScore < -2.5 && prevNetChangeHuron > -15)
			thisDoubleDownHuron = -10;
		else if (zScore < -2 && prevNetChangeHuron > -15)
			thisDoubleDownHuron = -5;
		else
			thisDoubleDownHuron = 0;

		/** column Q */ int thisNetChangeHuron;
		if ("sellY".equals(thisDecision))
			thisNetChangeHuron = Math.max(10 + thisDoubleDownHuron, prevNetChangeHuron);
		else if ("buyY".equals(thisDecision))
			thisNetChangeHuron = Math.min(-10 + thisDoubleDownHuron, prevNetChangeHuron);
		else if ("sellhold".equals(thisSignal))
			thisNetChangeHuron = thisDoubleDownHuron + prevNetChangeHuron;
		else if ("buyhold".equals(thisSignal))
			thisNetChangeHuron = thisDoubleDownHuron + prevNetChangeHuron;
		else
			thisNetChangeHuron = 0;

		/** column T */ int thisDoubleDownSuperior;
		if (zScore > 2.5 && prevNetChangeSuperior < 15)
			thisDoubleDownSuperior = -10;
		else if (zScore > 2 && prevNetChangeSuperior < 15)
			thisDoubleDownSuperior = -5;
		else if (zScore < -2.5 && prevNetChangeSuperior > -15)
			thisDoubleDownSuperior = 10;
		else if (zScore < -2 && prevNetChangeSuperior > -15)
			thisDoubleDownSuperior = 5;
		else
			thisDoubleDownSuperior = 0;

		/** column S */ int thisNetChangeSuperior;
		if ("sellY".equals(thisDecision))
			thisNetChangeSuperior = Math.min(-10 + thisDoubleDownSuperior, prevNetChangeSuperior);
		else if ("buyY".equals(thisDecision))
			thisNetChangeSuperior = Math.max(10 + thisDoubleDownSuperior, prevNetChangeSuperior);
		else if ("sellhold".equals(thisSignal))
			thisNetChangeSuperior = thisDoubleDownSuperior + prevNetChangeSuperior;
		else if ("buyhold".equals(thisSignal))
			thisNetChangeSuperior = thisDoubleDownSuperior + prevNetChangeSuperior;
		else
			thisNetChangeSuperior = 0;

		/** column V */ double change = thisNetChangeHuron - prevNetChangeHuron;
		/** column W */ double cashFlow = change * (priceSuperior - priceHuron);
		/** column X */ cashAndPnl += cashFlow;

		prevSignal = thisSignal;
		prevDecision = thisDecision;
		prevNetChangeHuron = thisNetChangeHuron;
		prevNetChangeSuperior = thisNetChangeSuperior;

		System.out.println(orderNum + 1 + " " + thisNetChangeHuron + " " + thisNetChangeSuperior + " " + cashAndPnl);
		orders[0].quantity = thisNetChangeHuron;
		orders[1].quantity = thisNetChangeSuperior;
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
