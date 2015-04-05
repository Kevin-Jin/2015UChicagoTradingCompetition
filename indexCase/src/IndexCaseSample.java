import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.uchicago.index.core.AbstractIndexCase;
import org.uchicago.index.core.IndexCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

/**
 * 
 * @author Shrey Patel
 * @author Nathan Ro
 * @author Embert Lin
 * @author Kevin Jin
 */
public class IndexCaseSample extends AbstractIndexCase implements IndexCase {
	/* Personal latest copy of information */
	private double[] my_portfolioWeights;
	Map<Integer, boolean[]> timeMap = new ConcurrentHashMap<Integer, boolean[]>();

	private double[] evenlyDistributeWeightsAcrossTradables(boolean[] tradables) {
		// Help function that evenly divide weight across every tradable asset

		// 1. Find how many assets we can hold
		int numberTradable = 0;
		for (int i = 0; i < 30; i++) {
			if (tradables[i]) {
				numberTradable++;
			}
		}

		// 2. Calculate even weight
		double evenWeight = 1.0 / numberTradable;

		// 3. Allocate that weight to the assets we're allowed to trade
		double[] portfolioWeights = new double[30];
		for (int i = 0; i < 30; i++) {
			if (tradables[i]) {
				portfolioWeights[i] = evenWeight;
			}
		}

		// 4. Return portfolio weights
		return portfolioWeights;
	}

	@Override
	public double[] initalizePosition(double[] underlyingPrices, double indexValue, double[] trueWeights, boolean[] tradables) {
		// We just distribute portfolio weights evenly across every tradable asset
		my_portfolioWeights = evenlyDistributeWeightsAcrossTradables(tradables);

		return my_portfolioWeights;
	}

	@Override
	public double[] updatePosition(int currentTime, double[] underlyingPrices, double indexValue) {
		// This strategy ignores all price changes
		if (timeMap.containsKey(currentTime)) {
			// Time is up...need to redistribute or I'll get penalties!!!
			boolean[] newTradables = timeMap.get(currentTime);
			my_portfolioWeights = evenlyDistributeWeightsAcrossTradables(newTradables);
		}
		return my_portfolioWeights;
	}

	@Override
	public void regulationAnnouncement(int currentTime, int timeTakeEffect, boolean[] tradables) {
		// Set timer for new announcement
		timeMap.put(timeTakeEffect, tradables);
	}

	@Override
	public void penaltyNotification(int currentTime, boolean[] tradables) {
		log("Penalty Notification!");
	}

	public IndexCase getImplementation() {
		return this;
	}

	@Override
	public void addVariables(IJobSetup setup) {
		// Registers a variable with the system.
		setup.addVariable("someFactor", "factor used to adjust something", "int", "2");
	}

	@Override
	public void initializeAlgo(IDB database) {
		// Databases can be used to store data between rounds
	}
}
