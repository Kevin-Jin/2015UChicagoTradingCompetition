import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
	private static final DecimalFormat FMT = new DecimalFormat("0.00");

	private static final int[][] ROUND_1_CORRELATION_ORDER = {
		{26,	15,	28,	4,	2,	5,	21,	11,	1,	8,	19,	16,	14,	20,	29,	23,	9,	22,	0,	6,	17,	18,	7,	10,	12,	25,	3,	27,	24,	13,	},
		{15,	13,	11,	25,	0,	18,	21,	20,	7,	27,	8,	23,	10,	9,	26,	1,	3,	29,	14,	4,	2,	19,	16,	22,	12,	24,	6,	17,	28,	5,	},
		{9,	4,	17,	13,	0,	21,	16,	26,	12,	18,	6,	19,	25,	22,	7,	24,	2,	5,	15,	23,	1,	3,	20,	10,	27,	8,	14,	11,	29,	28,	},
		{6,	25,	8,	9,	24,	18,	3,	21,	12,	17,	1,	4,	29,	14,	5,	2,	10,	15,	20,	26,	28,	23,	27,	7,	22,	16,	0,	19,	11,	13,	},
		{20,	2,	19,	8,	0,	15,	12,	13,	28,	21,	5,	22,	18,	4,	24,	16,	3,	7,	29,	6,	1,	25,	26,	14,	10,	27,	17,	11,	23,	9,	},
		{21,	26,	23,	0,	19,	29,	15,	18,	14,	16,	4,	8,	6,	7,	12,	11,	5,	22,	2,	24,	27,	25,	3,	9,	28,	13,	17,	20,	1,	10,	},
		{19,	7,	10,	26,	11,	3,	17,	2,	29,	5,	6,	24,	20,	15,	0,	14,	18,	4,	23,	28,	16,	25,	27,	1,	22,	21,	12,	8,	9,	13,	},
		{9,	25,	11,	6,	26,	21,	18,	1,	19,	2,	29,	5,	23,	7,	17,	0,	10,	4,	13,	27,	16,	22,	28,	8,	3,	15,	12,	14,	24,	20,	},
		{11,	19,	12,	4,	27,	3,	0,	10,	1,	18,	5,	14,	25,	9,	8,	16,	23,	22,	15,	26,	17,	7,	24,	13,	2,	29,	6,	20,	21,	28,	},
		{7,	25,	2,	29,	24,	21,	15,	3,	18,	23,	11,	16,	0,	1,	8,	9,	22,	17,	13,	20,	28,	14,	5,	27,	19,	26,	12,	6,	10,	4,	},
		{6,	13,	24,	17,	26,	22,	23,	8,	15,	1,	11,	29,	10,	25,	7,	19,	20,	0,	27,	14,	3,	2,	28,	4,	18,	16,	21,	9,	5,	12,	},
		{8,	7,	18,	6,	19,	23,	12,	1,	26,	0,	17,	24,	9,	14,	10,	5,	11,	20,	27,	29,	28,	25,	21,	16,	13,	3,	2,	22,	4,	15,	},
		{21,	13,	14,	28,	24,	23,	11,	8,	25,	4,	17,	2,	18,	27,	16,	5,	12,	22,	3,	29,	20,	19,	0,	1,	7,	6,	9,	15,	26,	10,	},
		{15,	18,	12,	23,	1,	25,	10,	20,	29,	2,	24,	17,	21,	4,	22,	27,	13,	26,	7,	9,	14,	16,	5,	8,	28,	11,	19,	3,	0,	6,	},
		{12,	19,	20,	5,	0,	8,	11,	26,	18,	28,	14,	24,	21,	23,	6,	3,	1,	9,	13,	27,	4,	10,	16,	22,	29,	7,	17,	2,	25,	15,	},
		{13,	29,	1,	16,	0,	28,	4,	23,	9,	25,	27,	26,	5,	10,	24,	22,	20,	15,	2,	6,	21,	8,	17,	18,	3,	19,	7,	12,	11,	14,	},
		{15,	26,	22,	2,	23,	25,	0,	28,	5,	27,	17,	9,	19,	18,	12,	16,	4,	8,	24,	7,	29,	21,	1,	13,	14,	6,	10,	3,	11,	20,	},
		{27,	18,	2,	13,	26,	29,	10,	6,	12,	11,	19,	25,	16,	24,	17,	3,	21,	0,	7,	9,	15,	8,	22,	23,	20,	5,	14,	1,	28,	4,	},
		{13,	25,	11,	17,	23,	22,	7,	1,	5,	19,	2,	9,	12,	24,	8,	3,	4,	16,	21,	14,	18,	0,	6,	28,	29,	26,	15,	20,	10,	27,	},
		{25,	6,	11,	21,	8,	14,	4,	28,	5,	0,	18,	17,	22,	7,	2,	16,	19,	24,	26,	10,	29,	20,	12,	1,	15,	9,	3,	27,	13,	23,	},
		{21,	4,	13,	29,	23,	28,	14,	27,	0,	1,	24,	15,	20,	11,	6,	10,	12,	19,	9,	25,	2,	3,	18,	17,	22,	8,	5,	26,	7,	16,	},
		{12,	5,	20,	19,	29,	22,	13,	9,	2,	7,	23,	0,	25,	1,	4,	18,	21,	3,	14,	17,	15,	24,	16,	27,	26,	28,	6,	10,	11,	8,	},
		{29,	28,	21,	16,	10,	18,	25,	19,	13,	4,	2,	0,	15,	22,	5,	12,	9,	8,	7,	1,	17,	24,	26,	6,	3,	23,	14,	20,	11,	27,	},
		{13,	27,	12,	11,	24,	15,	29,	5,	21,	18,	16,	20,	10,	26,	0,	9,	1,	7,	23,	14,	2,	8,	6,	25,	3,	17,	22,	28,	4,	19,	},
		{12,	23,	13,	9,	10,	11,	3,	20,	18,	15,	17,	27,	2,	24,	19,	6,	5,	14,	4,	29,	16,	26,	21,	25,	22,	28,	8,	1,	7,	0,	},
		{7,	9,	18,	19,	13,	12,	3,	15,	21,	28,	1,	29,	16,	22,	17,	2,	8,	25,	27,	10,	5,	4,	23,	26,	20,	24,	6,	0,	11,	14,	},
		{0,	6,	7,	5,	29,	17,	16,	2,	10,	11,	27,	15,	23,	28,	14,	1,	26,	19,	13,	18,	24,	8,	25,	4,	3,	22,	21,	9,	20,	12,	},
		{23,	17,	8,	15,	26,	20,	1,	16,	13,	24,	12,	27,	25,	28,	7,	5,	10,	11,	14,	21,	9,	3,	6,	2,	29,	19,	4,	0,	18,	22,	},
		{12,	29,	0,	15,	22,	19,	25,	20,	4,	16,	26,	14,	28,	27,	18,	6,	9,	5,	7,	3,	10,	24,	11,	21,	13,	17,	23,	2,	1,	8,	},
		{15,	22,	28,	13,	21,	9,	20,	26,	23,	17,	5,	25,	0,	6,	7,	10,	29,	24,	12,	1,	4,	18,	19,	3,	16,	11,	27,	8,	14,	2,	},
	};
	/* Personal latest copy of information */
	private double[] my_portfolioWeights = new double[30];
	Map<Integer, boolean[]> timeMap = new ConcurrentHashMap<Integer, boolean[]>();
	Set<Integer> forbidden = new HashSet<Integer>();
	int[][] correlationOrder = ROUND_1_CORRELATION_ORDER;

	private double[] weights;
	private double[] lastPrices;

	@Override
	public double[] initalizePosition(double[] underlyingPrices, double indexValue, double[] trueWeights, boolean[] tradables) {
		// We just distribute portfolio weights evenly across every tradable asset
		forbidden.clear();
		for (int i = 0; i < 30; i++)
			if (!tradables[i])
				forbidden.add(Integer.valueOf(i));

		weights = trueWeights;
		lastPrices = Arrays.copyOf(underlyingPrices, 30);
		return Arrays.copyOf(trueWeights, 30);
	}

	@Override
	public double[] updatePosition(int currentTime, double[] underlyingPrices, double indexValue) {
		// This strategy ignores all price changes
		if (timeMap.containsKey(currentTime)) {
			// Time is up...need to redistribute or I'll get penalties!!!
			boolean[] newTradables = timeMap.get(currentTime);
			forbidden.clear();
			for (int i = 0; i < 30; i++)
				if (!newTradables[i])
					forbidden.add(Integer.valueOf(i));
		}
		double commission = 0.031399675;
		double[] add = new double[30];
		double synthetic = 0;
		double actual = 0;
		String str = "";
		for (int j = 0; j < 30; j++) {
			if (forbidden.contains(Integer.valueOf(j))) {
				int sub;
				for (sub = 0; forbidden.contains(Integer.valueOf(correlationOrder[j][sub])); sub++)
					sub = correlationOrder[j][sub];
				double ratio;
				ratio = (lastPrices[j] * weights[j]) / (lastPrices[correlationOrder[j][sub]] * weights[correlationOrder[j][sub]]);
				add[correlationOrder[j][sub]] = ratio;
				my_portfolioWeights[j] = 0;
			} else {
				my_portfolioWeights[j] = (1 + add[j]);
			}
			synthetic += my_portfolioWeights[j] * underlyingPrices[j] * weights[j];
			str += (FMT.format(my_portfolioWeights[j] * underlyingPrices[j] * weights[j]) + " ");
			actual += underlyingPrices[j] * weights[j];
		}
		str += FMT.format(synthetic * (1 - commission)) + " " + FMT.format(actual * (1 - commission)) + " " + FMT.format((synthetic / actual - 1) * 100) + "%";
		log(currentTime + " " + str);
		lastPrices = Arrays.copyOf(underlyingPrices, 30);
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
