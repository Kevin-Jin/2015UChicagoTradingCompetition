import java.util.ArrayList;
import java.util.List;

import org.uchicago.options.OptionsHelpers.Quote;
import org.uchicago.options.OptionsHelpers.QuoteList;
import org.uchicago.options.OptionsMathUtils;
import org.uchicago.options.core.AbstractOptionsCase;
import org.uchicago.options.core.OptionsInterface;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class OptionsCaseSample extends AbstractOptionsCase implements OptionsInterface {
	//some constants
	private static final int NUMBER_STRIKES = 5;
	private static final double VEGA_LIMIT = 5 * OptionsMathUtils.calculateVega(100, 0.3);
	//some parameters
	private static final double EPSILON_FOR_SIGMA = 1e-14;
	private static final double OPTIMAL_SPREAD_FACTOR = 20;
	private static final double OPTIMAL_VEGA = VEGA_LIMIT / 2;
	private static final double MARGINAL_WEIGHT_FOR_INVENTORY = 0.05;
	private static final double MARGINAL_SPREAD_FOR_VEGA_DIFF = 10;
	private static final double MARGINAL_SPREAD_FOR_VOLUME = 0.05;

	// private IDB myDatabase;

	private double pnl;
	private int penalties;
	private int totalTicks;
	private double vega;

	private final double[] bidWeightForSpread = new double[NUMBER_STRIKES];
	private final double[] spreadFactor = new double[NUMBER_STRIKES]; //multiply with sigma to get spread
	private final double[] bid = new double[NUMBER_STRIKES];
	private final double[] ask = new double[NUMBER_STRIKES];
	private final double[] volume = new double[NUMBER_STRIKES];

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
			for (int asset = 0; asset < NUMBER_STRIKES; asset++) {
				positiveInventory.add(new ArrayList<Double>());
				negativeInventory.add(new ArrayList<Double>());
				bid[asset] = OptionsMathUtils.theoValue(100, 0.3) - 1;
				ask[asset] = OptionsMathUtils.theoValue(100, 0.3) + 1;
				spreadFactor[asset] = OPTIMAL_SPREAD_FACTOR;
				bidWeightForSpread[asset] = 0.5;
			}
			pnl = vega = penalties = 0;
		}
	}

	private static double sigmaEstimationError(double strike, double vol, double actual) {
		return Math.abs(OptionsMathUtils.theoValue(strike, vol) - actual);
	}

	//bisection method. not dependent on vega, unlike newton-raphson
	private double calculateSigma(double strike, double price) {
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
				if (priceError > 0) {
					//impliedVol too high - decrease it
					impliedVolUpperBound = impliedVolMid;
				} else if (priceError < 0) {
					//impliedVol too low - increase it
					impliedVolLowerBound = impliedVolMid;
				} else {
					//impliedVol exactly correct. exit now
					impliedVolUpperBound = impliedVolLowerBound = impliedVolMid;
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

	@Override
	public void newFill(int strike, int side, double price) {
		//print header
		log("Order cleared, price=" + price + ", strike=" + strike + ", direction=" + side + ".");
		int asset = getAsset(strike);
		volume[asset]++;
		double sigma = calculateSigma(strike, price);
		double spread = spreadFactor[asset] * sigma;
		log("SIGMA: " + sigma + ", SIGMA ESTIMATION PRICE ERROR: " + sigmaEstimationError(strike, sigma, price) + ", SPREAD: " + spread);

		//calculate a rolling PnL
		double sellPrice, buyPrice;
		switch (side) {
			case -1: // market sell -> we're buying
				buyPrice = price;
				if (!negativeInventory.get(asset).isEmpty()) {
					sellPrice = negativeInventory.get(asset).remove(0).doubleValue();
					pnl += sellPrice - buyPrice;
				} else {
					positiveInventory.get(asset).add(Double.valueOf(buyPrice));
				}
				break;
			case 1: // market buy -> we're selling
				sellPrice = price;
				if (!positiveInventory.get(asset).isEmpty()) {
					buyPrice = positiveInventory.get(asset).remove(0).doubleValue();
					pnl += sellPrice - buyPrice;
				} else {
					negativeInventory.get(asset).add(Double.valueOf(sellPrice));
				}
				break;
		}
		bid[asset] = price - bidWeightForSpread[asset] * spread;
		ask[asset] = price + (1 - bidWeightForSpread[asset]) * spread;
		vega = 0;
		for (int i = 0; i < NUMBER_STRIKES; i++) {
			double sigmaForStrike = calculateSigma(getStrike(i), price);
			double vegaForStrike = OptionsMathUtils.calculateVega(getStrike(i), sigmaForStrike);
			vega += positiveInventory.get(i).size() * vegaForStrike;
			vega -= negativeInventory.get(i).size() * vegaForStrike;
		}
		vega = Math.abs(vega);
		if (vega >= VEGA_LIMIT) {
			penalties++;
			pnl -= 100 * (vega - VEGA_LIMIT);
		}

		//make some adjustments
		int inventoryCount = positiveInventory.get(asset).size() - negativeInventory.get(asset).size();
		if (inventoryCount > 0) {
			//too many market sell orders clearing --> we're buying too expensively --> decrease bid --> increase bidWeightForSpread
			bidWeightForSpread[asset] = 0.5 + MARGINAL_WEIGHT_FOR_INVENTORY * inventoryCount;
			log("Bid weight on asset " + asset + " increased to " + bidWeightForSpread[asset]);
		} else if (inventoryCount < 0) {
			//too many market buy orders clearing --> we're selling too cheaply --> increase ask --> decrease bidWeightForSpread
			bidWeightForSpread[asset] = 0.5 + MARGINAL_WEIGHT_FOR_INVENTORY * inventoryCount;
			log("Bid weight on asset " + asset + " decreased to " + bidWeightForSpread[asset]);
		}
		double vegaDiff = vega - OPTIMAL_VEGA;
		if (vegaDiff > 0) {
			//holding too much inventory --> increase spreadFactor (must raise our profits to compensate for higher risk)
			//the higher our volume, the lower the spread
			spreadFactor[asset] = OPTIMAL_SPREAD_FACTOR + MARGINAL_SPREAD_FOR_VEGA_DIFF * vegaDiff - MARGINAL_SPREAD_FOR_VOLUME * volume[asset] / totalTicks;
			log("Spread factor on asset " + asset + " increased to " + spreadFactor[asset]);
		} else if (vegaDiff < 0) {
			//holding too little inventory --> decrease spreadFactor (must lower our profits to compensate for lower risk, increase bid to entice sellers, decrease ask to entice buyers)
			//the higher our volume, the lower the spread
			spreadFactor[asset] = OPTIMAL_SPREAD_FACTOR + MARGINAL_SPREAD_FOR_VEGA_DIFF * vegaDiff - MARGINAL_SPREAD_FOR_VOLUME * volume[asset] / totalTicks;
			log("Spread factor on asset " + asset + " decreased to " + spreadFactor[asset]);
		}

		//print summary
		String inventory = "";
		for (int i = 0; i < NUMBER_STRIKES; i++) {
			inventory += getStrike(i) + ": [";
			inventory += positiveInventory.get(i).size() - negativeInventory.get(i).size();
			inventory += "], ";
		}
		log("Inventory : " + inventory);
		log("PNL : " + pnl);
		log("Vega : " + vega + " < " + VEGA_LIMIT + "? (" + penalties + " penalties so far)");
	}

	@Override
	public QuoteList getCurrentQuotes() {
		Quote quoteEighty = new Quote(80, bid[0], ask[0]);
		Quote quoteNinety = new Quote(90, bid[1], ask[1]);
		Quote quoteHundred = new Quote(100, bid[2], ask[2]);
		Quote quoteHundredTen = new Quote(110, bid[3], ask[3]);
		Quote quoteHundredTwenty = new Quote(120, bid[4], ask[4]);
		totalTicks++;

		return new QuoteList(quoteEighty, quoteNinety, quoteHundred, quoteHundredTen, quoteHundredTwenty);
	}

	@Override
	public void noBrokerFills() {
		log("No orders cleared.");
		double vegaDiff = vega - OPTIMAL_VEGA;
		for (int i = 0; i < NUMBER_STRIKES; i++) {
			spreadFactor[i] = OPTIMAL_SPREAD_FACTOR + MARGINAL_SPREAD_FOR_VEGA_DIFF * vegaDiff - MARGINAL_SPREAD_FOR_VOLUME * volume[i] / totalTicks;
			log("Spread factor on asset " + i + " increased to " + spreadFactor[i]);
		}
	}

	@Override
	public void penaltyNotice(double amount) {
		log("Penalty received in the amount of " + amount);
	}

	@Override
	public OptionsInterface getImplementation() {
		return this;
	}
}
