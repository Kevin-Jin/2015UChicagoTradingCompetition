import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.uchicago.options.OptionsHelpers.Quote;
import org.uchicago.options.OptionsHelpers.QuoteList;
import org.uchicago.options.OptionsMathUtils;
import org.uchicago.options.core.AbstractOptionsCase;
import org.uchicago.options.core.OptionsInterface;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/**
 * 
 *
 * @author Nathan Ro
 * @author Kevin Jin
 * @author Embert Lin
 * @author Shrey Patel
 */
public class OptionsCaseSample extends AbstractOptionsCase implements OptionsInterface {
	private static final NumberFormat FMT = new DecimalFormat("0.00");

	//some constants, defined by the case
	private static final int ROUND_TICKS = 100;
	private static final int NUMBER_STRIKES = 5;
	private static final double VEGA_LIMIT = 5 * OptionsMathUtils.calculateVega(100, 0.3);
	//some parameters
	/**
	 * Used to determine our short run hit/lift rate that affects our spread.
	 */
	private static final int NUMBER_TICKS_TO_TRACK = 3;
	/**
	 * Maximum error we'll take in our bisection estimation for sigma.
	 */
	private static final double EPSILON_FOR_SIGMA = 1e-15;
	/**
	 * The first quotes will use this volatility.
	 * THIS MAKES A HUGE DIFFERENCE.
	 */
	private static final double INITIAL_VOLATILITY = 0.3;
	/**
	 * The first quotes will use this bid:ask spread.
	 * THIS MAKES A HUGE DIFFERENCE.
	 */
	private static final double INITIAL_SPREAD = 3;
	/**
	 * Higher excess short or long inventory -> higher velocity on bid-weight to incentivize brokers to trade with us in the opposite direction.
	 */
	private static final double MARGINAL_WEIGHT_VELOCITY_FOR_INVENTORY = .001;
	/**
	 * Bid-weight can decrease or increase by a maximum of this amount every tick.
	 * THIS MAKES A HUGE DIFFERENCE.
	 */
	private static final double MAX_WEIGHT_VELOCITY_MAGNITUDE = 0.000073;
	/**
	 * > 1 means ask can fall below FV and bid can rise above FV.
	 * = 0 means bid must be FV - 0.5 * spread and ask must be FV + 0.5 * spread.
	 */
	private static final double MAX_WEIGHT_DEVIATION = 2;
	/**
	 * Lower -> profit may decrease but more trades should clear.
	 * THIS MAKES A HUGE DIFFERENCE.
	 */
	private static final double MIN_SPREAD = 5;
	/**
	 * Higher -> profit may increase but fewer trades should clear.
	 */
	private static final double MAX_SPREAD = 10;
	/**
	 * Magnify or reduce the effect of unfilled ticks and options risk on our bid:ask spread.
	 */
	private static final double SPREAD_FACTOR = 0.1;
	/**
	 * Higher -> spread decreases faster on higher inventory.
	 */
	private static final double VEGA_POWER = 1;
	/**
	 * We don't bother with vega in our spread calculation unless vega is greater
	 * than 1. Otherwise, spread goes to infinity when we have no inventory.
	 * Increase this value to start selling off faster as inventory grows.
	 * THIS MAKES A HUGE DIFFERENCE.
	 */
	private static final double VEGA_CLAMP = 1;
	/**
	 * THIS MAKES A HUGE DIFFERENCE.
	 */
	private static final int MAX_NET_INVENTORY_MAGNITUDE = 2;
	/**
	 * THIS MAKES A HUGE DIFFERENCE.
	 */
	private static final int MAX_ASSET_INVENTORY_MAGNITUDE = 5;
	private static final double[] MAX_PRICES = {
		OptionsMathUtils.theoValue(getStrike(0), 0.501),
		OptionsMathUtils.theoValue(getStrike(1), 0.501),
		OptionsMathUtils.theoValue(getStrike(2), 0.501),
		OptionsMathUtils.theoValue(getStrike(3), 0.501),
		OptionsMathUtils.theoValue(getStrike(4), 0.501)
	};
	private static final double[] MIN_PRICES = {
		OptionsMathUtils.theoValue(getStrike(0), 0.201),
		OptionsMathUtils.theoValue(getStrike(1), 0.201),
		OptionsMathUtils.theoValue(getStrike(2), 0.201),
		OptionsMathUtils.theoValue(getStrike(3), 0.201),
		OptionsMathUtils.theoValue(getStrike(4), 0.201)
	};

	private static class ClearingMeasure {
		public int value;
		private boolean[] distribution = new boolean[NUMBER_TICKS_TO_TRACK];
		private int clearedIndex;

		public ClearingMeasure() {
			clear();
		}

		public void clear() {
			value = 0;
			for (int i = 0; i < NUMBER_TICKS_TO_TRACK; i++)
				distribution[i] = false;
			clearedIndex = 0;
		}

		private void updateClearedMeasure(boolean isCleared) {
			boolean existing = distribution[clearedIndex];
			if (existing != isCleared) {
				value += (isCleared ? 1 : -1);
				distribution[clearedIndex] = isCleared;
				if (value < 0 || value > NUMBER_TICKS_TO_TRACK)
					throw new RuntimeException("Clearing measure wrong");
			}
			clearedIndex = (clearedIndex + 1) % distribution.length;
		}
	}

	// private IDB myDatabase;

	public double pnl;
	private int penalties;
	private double penaltyDollars;
	private double positionVega;
	private int tickNum;
	private double recentSigma;
	public double highestVega;
	public int highestVegaTick;
	public int cleared;

	private ClearingMeasure[] clearingMeasure = new ClearingMeasure[NUMBER_STRIKES];
	private final double[] bidWeightForSpreadVelocity = new double[NUMBER_STRIKES];
	private final double[] bidWeightForSpread = new double[NUMBER_STRIKES];
	private final double[] bid = new double[NUMBER_STRIKES];
	private final double[] ask = new double[NUMBER_STRIKES];
	private final double[] mostRecentPrice = new double[NUMBER_STRIKES];
	private final double[] mostRecentSpread = new double[NUMBER_STRIKES];

	//negativeInventory.get(i).size() > positiveInventory.get(i).size() --> we're short on asset i, i.e. negative inventory
	private final List<List<Double>> positiveInventory = new ArrayList<List<Double>>();
	private final List<List<Double>> negativeInventory = new ArrayList<List<Double>>();

	private static int getStrike(int asset) {
		return asset * 10 + 80;
	}

	private static int getAsset(int strike) {
		return (strike - 80) / 10;
	}

	@Override
	public void addVariables(IJobSetup setup) {
		setup.addVariable("Strategy", "Strategy to use", "string", "one");
	}

	@Override
	public void initializeAlgo(IDB dataBase, List<String> instruments) {
		String strategy = getStringVar("Strategy");
		if (strategy.contains("one")) {
			// do strategy one
			positiveInventory.clear();
			negativeInventory.clear();
			for (int i = 0; i < NUMBER_STRIKES; i++) {
				positiveInventory.add(new LinkedList<Double>());
				negativeInventory.add(new LinkedList<Double>());
				mostRecentPrice[i] = OptionsMathUtils.theoValue(getStrike(i), INITIAL_VOLATILITY);
				mostRecentSpread[i] = INITIAL_SPREAD;
				bidWeightForSpreadVelocity[i] = 0;
				bidWeightForSpread[i] = 0.5;
				bid[i] = mostRecentPrice[i] - mostRecentSpread[i] * bidWeightForSpread[i];
				ask[i] = mostRecentPrice[i] + mostRecentSpread[i] * (1 - bidWeightForSpread[i]);
				clearingMeasure[i] = new ClearingMeasure();
			}
			highestVega = recentSigma = penaltyDollars = pnl = positionVega = penalties = tickNum = 0;
			highestVegaTick = -1;
		}
	}

	private static double sigmaEstimationError(double strike, double vol, double actual) {
		return Math.abs(OptionsMathUtils.theoValue(strike, vol) - actual);
	}

	//bisection method. not dependent on vega, unlike newton-raphson
	public double calculateSigma(double strike, double price) {
		final int VOL_RANGE = 500; //lower -> marginally faster. too low -> more range misses

		int rangeExpansion = 0;
		double impliedVolUpperBound, impliedVolLowerBound;
		double impliedVolMid;
		double initialVolLowerBound, initialVolUpperBound;
		double priceError;

		//sanity test
		assert VOL_RANGE >= EPSILON_FOR_SIGMA;
		do {
			initialVolLowerBound = impliedVolLowerBound = VOL_RANGE * rangeExpansion;
			rangeExpansion++;
			initialVolUpperBound = impliedVolUpperBound = VOL_RANGE * rangeExpansion;
			do {
				impliedVolMid = (impliedVolLowerBound + impliedVolUpperBound) / 2;
				priceError = OptionsMathUtils.theoValue(strike, impliedVolMid) - price;
				if (impliedVolMid == impliedVolLowerBound || impliedVolMid == impliedVolUpperBound || Math.abs(priceError) < EPSILON_FOR_SIGMA) {
					//not enough precision, stop
					impliedVolUpperBound = impliedVolLowerBound = impliedVolMid;
				} else if (priceError > 0) {
					//impliedVol too high - decrease it
					impliedVolUpperBound = impliedVolMid;
				} else if (priceError < 0) {
					//impliedVol too low - increase it
					impliedVolLowerBound = impliedVolMid;
				}
			} while (impliedVolUpperBound - impliedVolLowerBound >= EPSILON_FOR_SIGMA);
			//implied volatility may just be initial lower bound on this iteration, e.g. 0 on first iteration
			if (impliedVolLowerBound == initialVolLowerBound && (impliedVolMid - initialVolLowerBound) < EPSILON_FOR_SIGMA
					&& sigmaEstimationError(strike, initialVolLowerBound, price) < sigmaEstimationError(strike, impliedVolMid, price))
				impliedVolMid = initialVolLowerBound;
			//implied volatility may just be higher than initial upper bound on this iteration, e.g. VOL_RANGE on first iteration
			if (impliedVolUpperBound == initialVolUpperBound && (initialVolUpperBound - impliedVolMid) < EPSILON_FOR_SIGMA
					&& sigmaEstimationError(strike, initialVolUpperBound, price) < sigmaEstimationError(strike, impliedVolMid, price))
				log("Range miss. Sigma > " + VOL_RANGE + ". Increase calculateSigma(double, double)#VOL_RANGE.");
			else
				rangeExpansion = -1;
		} while (rangeExpansion != -1);

		return impliedVolMid;
	}

	private void printSummary() {
		String inventory = "";
		String quotes = "";
		String fills = "";
		String bidWeights = "";
		String fvs = "";
		for (int i = 0; i < NUMBER_STRIKES; i++) {
			inventory += getStrike(i) + ": " + (positiveInventory.get(i).size() - negativeInventory.get(i).size()) + ", ";
			quotes += getStrike(i) + ": [bid: " + FMT.format(bid[i]) + ", ask: " + FMT.format(ask[i]) + "], ";
			fills += getStrike(i) + ": " + clearingMeasure[i].value + ", ";
			bidWeights += getStrike(i) + ": [now: " + FMT.format(bidWeightForSpread[i]) + ", vel: " + FMT.format(bidWeightForSpreadVelocity[i]) + "], ";
			fvs += getStrike(i) + ": " + FMT.format(mostRecentPrice[i]) + ", ";
		}
		log("PNL : " + FMT.format(pnl));
		log("Inventory : " + inventory);
		log("Quotes : " + quotes);
		log("Bid weights: " + bidWeights);
		log("Fills in past 10 ticks: " + fills);
		log("FVs: " + fvs);
		log("Vega : " + FMT.format(positionVega) + " < " + FMT.format(VEGA_LIMIT) + "? (" + penalties + " penalties so far totaling " + FMT.format(penaltyDollars) + ")");
	}

	private double calculateOptionRisk(double vol, int strike) {
		//option vega makes no sense since that implies higher spreads on at the money options,
		//which are more liquid. so calculate using volatility of underlying times some factor
		//to correct for the fact that otherwise, spread would be the same for all options
		//with different strike prices i.e. less liquid/more liquid assets would have same spread
		double factor;
		switch (strike) {
			case 80: factor = 10; break;
			case 90: factor = 6; break;
			case 100: factor = 3; break;
			case 110: factor = 2; break;
			case 120: factor = 1; break;
			default: throw new IllegalArgumentException("Strike");
		}
		return vol * factor;
	}

	private double calculateSpread(double vol, int strike) {
		return Math.max(MIN_SPREAD, Math.min(MAX_SPREAD,
			//normal spread
			SPREAD_FACTOR
			//hitting too many bids and lifting too many asks means we could profit more and we need to make our options less attractive
			* Math.max(1, clearingMeasure[getAsset(strike)].value)
			//decrease our spread as we get closer to the end of the round
			* (ROUND_TICKS - (tickNum / 10)) / ROUND_TICKS
			//higher risk on individual options means we need to increase spread
			* calculateOptionRisk(vol, strike)
			//higher risk on position means we need to decrease spread and unload FAST (weight this more heavily)
			/ (Math.pow(Math.max(VEGA_CLAMP, positionVega), VEGA_POWER))
		));
	}

	private double calculateSpreadBidWeightVelocity(int inventoryCount, double vega) {
		return Math.max(-MAX_WEIGHT_VELOCITY_MAGNITUDE, Math.min(MAX_WEIGHT_VELOCITY_MAGNITUDE,
			//too many market sell orders clearing (positive inventory) --> we're buying too much too expensively --> decrease bid (buy less) and ask (sell more) --> increase bidWeightForSpread
			//too many market buy orders clearing (negative inventory) --> we're selling too much too cheaply --> increase bid (buy more) and ask (sell less) --> decrease bidWeightForSpread
			MARGINAL_WEIGHT_VELOCITY_FOR_INVENTORY * inventoryCount * vega
		));
	}

	private void updateSpreadBidWeight(int asset, double sigma) {
		bidWeightForSpreadVelocity[asset] = calculateSpreadBidWeightVelocity(positiveInventory.get(asset).size() - negativeInventory.get(asset).size(), OptionsMathUtils.calculateVega(getStrike(asset), sigma));

		//when velocity switches direction, we want to make sure bidWeight moves to same direction
		//0.5 is our "normal" distribution of the spread to bid and ask
		if (bidWeightForSpreadVelocity[asset] < 0 && bidWeightForSpread[asset] > 0.5)
			bidWeightForSpread[asset] = 0.5;
		if (bidWeightForSpreadVelocity[asset] > 0 && bidWeightForSpread[asset] < 0.5)
			bidWeightForSpread[asset] = 0.5;

		//add velocity to the current spread
		bidWeightForSpread[asset] = Math.max(0.5 - MAX_WEIGHT_DEVIATION, Math.min(0.5 + MAX_WEIGHT_DEVIATION,
			bidWeightForSpread[asset] + bidWeightForSpreadVelocity[asset])
		);
	}

	private void updatePositionVega(double sigma) {
		positionVega = 0;
		//sum the option vega of all inventory
		for (int i = 0; i < NUMBER_STRIKES; i++) {
			double vegaForStrike = OptionsMathUtils.calculateVega(getStrike(i), sigma);
			positionVega += positiveInventory.get(i).size() * vegaForStrike;
			positionVega -= negativeInventory.get(i).size() * vegaForStrike;
		}
		positionVega = Math.abs(positionVega);
		if (positionVega > highestVega) {
			highestVega = positionVega;
			highestVegaTick = tickNum;
		}
	}

	private void updateBidAndAsk(int asset) {
		int netInventory = 0;
		for (int i = 0; i < NUMBER_STRIKES; i++)
			netInventory += positiveInventory.get(i).size() - negativeInventory.get(i).size();
		boolean stopMarketBuys = (netInventory > MAX_NET_INVENTORY_MAGNITUDE);
		boolean stopMarketSells = (netInventory < -MAX_NET_INVENTORY_MAGNITUDE);
		if (!stopMarketBuys && !positiveInventory.get(asset).isEmpty())
			//stop buying when we have positive inventory and we're more than three quarters through round, or if we have inventory of 2
			stopMarketBuys = (positiveInventory.get(asset).size() > MAX_ASSET_INVENTORY_MAGNITUDE);
		if (!stopMarketSells && !negativeInventory.get(asset).isEmpty())
			//stop selling when we have negative inventory and we're more than three quarters through round, or if we have inventory of -2
			stopMarketSells = (negativeInventory.get(asset).size() > MAX_ASSET_INVENTORY_MAGNITUDE);

		if (stopMarketBuys) {
			//don't take on any more market sells
			bid[asset] = Double.NEGATIVE_INFINITY;
		} else {
			bid[asset] = Math.max(MIN_PRICES[asset], Math.min(MAX_PRICES[asset],
				mostRecentPrice[asset] - bidWeightForSpread[asset] * mostRecentSpread[asset]
			));
		}
		if (stopMarketSells) {
			//don't take on any more market buys
			ask[asset] = Double.POSITIVE_INFINITY;
		} else {
			ask[asset] = Math.max(MIN_PRICES[asset], Math.min(MAX_PRICES[asset],
				mostRecentPrice[asset] + (1 - bidWeightForSpread[asset]) * mostRecentSpread[asset]
			));
		}
	}

	private void updatePenalties() {
		if (positionVega >= VEGA_LIMIT) {
			penalties++;
			penaltyDollars += (positionVega - VEGA_LIMIT) * 100;
		}
	}

	@Override
	public void newFill(int strike, int side, double price) {
		//print header
		int asset = getAsset(strike);
		recentSigma = calculateSigma(strike, price);
		mostRecentPrice[asset] = price;
		mostRecentSpread[asset] = calculateSpread(recentSigma, strike);
		cleared++;
		log("Tick " + tickNum + ": Order cleared, strike=" + strike + ", price=" + FMT.format(price) + ", direction=" + side + ". " + "SIGMA: " + FMT.format(recentSigma) + ", SIGMA ESTIMATION PRICE ERROR: " + FMT.format(sigmaEstimationError(strike, recentSigma, price)));

		//calculate a rolling PnL
		//FIFO basis
		double sellPrice, buyPrice;
		switch (side) {
			case -1: // market sell -> we're buying
				buyPrice = price;
				if (!negativeInventory.get(asset).isEmpty()) {
					sellPrice = negativeInventory.get(asset).remove(0).doubleValue();
					pnl += sellPrice - buyPrice;
					log("PROFIT : " + FMT.format(sellPrice - buyPrice));
				} else {
					positiveInventory.get(asset).add(Double.valueOf(buyPrice));
				}
				break;
			case 1: // market buy -> we're selling
				sellPrice = price;
				if (!positiveInventory.get(asset).isEmpty()) {
					buyPrice = positiveInventory.get(asset).remove(0).doubleValue();
					pnl += sellPrice - buyPrice;
					log("PROFIT : " + FMT.format(sellPrice - buyPrice));
				} else {
					negativeInventory.get(asset).add(Double.valueOf(sellPrice));
				}
				break;
			default:
				throw new RuntimeException("");
		}

		//make some adjustments
		clearingMeasure[asset].updateClearedMeasure(true);
		for (int i = 0; i < NUMBER_STRIKES; i++) {
			updateSpreadBidWeight(i, recentSigma);
			mostRecentPrice[i] = OptionsMathUtils.theoValue(getStrike(i), recentSigma);
			updateBidAndAsk(i);
		}
		updatePositionVega(recentSigma);
		updatePenalties();

		printSummary();
	}

	@Override
	public QuoteList getCurrentQuotes() {
		tickNum++;
		log("");
		Quote quoteEighty = new Quote(80, bid[0], ask[0]);
		Quote quoteNinety = new Quote(90, bid[1], ask[1]);
		Quote quoteHundred = new Quote(100, bid[2], ask[2]);
		Quote quoteHundredTen = new Quote(110, bid[3], ask[3]);
		Quote quoteHundredTwenty = new Quote(120, bid[4], ask[4]);

		return new QuoteList(quoteEighty, quoteNinety, quoteHundred, quoteHundredTen, quoteHundredTwenty);
	}

	@Override
	public void noBrokerFills() {
		log("Tick " + tickNum + ": No orders cleared.");

		//make some adjustments
		for (int i = 0; i < NUMBER_STRIKES; i++) {
			clearingMeasure[i].updateClearedMeasure(false);
			updateSpreadBidWeight(i, recentSigma);
			updateBidAndAsk(i);
		}
		//sigma and inventory don't change, no need to update position vega
		updatePenalties();

		printSummary();
	}

	@Override
	public void penaltyNotice(double amount) {
		log("Penalty received in the amount of " + FMT.format(amount));
	}

	@Override
	public OptionsInterface getImplementation() {
		return this;
	}
}
