import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class Test {
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

	private static double[] getWeights(int round) throws FileNotFoundException {
		double[] weights = new double[30];
		try (Scanner scan = new Scanner(new File("market-data/round" + round + "/capWeights.csv"))) {
			for (int i = 0; i < 30; i++)
				weights[i] = scan.nextDouble();
		}
		return weights;
	}

	//these are hardcoded based on historical data
	@SuppressWarnings("unused")
	private static int[][] findHighestCorrelating(int round) throws FileNotFoundException {
		List<List<Double>> percentChanges = new ArrayList<>();
		for (int i = 0; i < 30; i++)
			percentChanges.add(new ArrayList<Double>());
		try (Scanner scan = new Scanner(new File("market-data/round" + round + "/prices.csv"))) {
			scan.nextLine(); //skip headers line

			double[] prev = new double[30];
			String[] line0 = scan.nextLine().split(",");
			for (int j = 0; j < 30; j++)
				prev[j] = Double.parseDouble(line0[j]);
			for (int i = 0; i < 9999; i++) {
				String[] line1 = scan.nextLine().split(",");
				for (int j = 0; j < 30; j++) {
					double now = Double.parseDouble(line1[j]);
					percentChanges.get(j).add(Double.valueOf((now - prev[j]) / prev[j]));
					prev[j] = now;
				}
			}
		}

		final double[][] correlations = new double[30][30];
		for (int i = 0; i < 29; i++)
			for (int j = i + 1; j < 30; j++)
				correlations[i][j] = correlations[j][i] = new LinearRegression(percentChanges.get(i), percentChanges.get(j)).correlation();

		int[][] orders = new int[30][30];
		for (int i = 0; i < 30; i++) {
			final int iAt = i;
			SortedSet<Integer> sorter = new TreeSet<Integer>(new Comparator<Integer>() {
				@Override
				public int compare(Integer j1, Integer j2) {
					if (correlations[iAt][j1.intValue()] < correlations[iAt][j2.intValue()])
						return 1;
					else if (correlations[iAt][j1.intValue()] > correlations[iAt][j2.intValue()])
						return -1;
					else
						return 0;
				}
			});
			for (int j = 0; j < 30; j++)
				sorter.add(Integer.valueOf(j));
			int j = 0;
			for (Integer counterPart : sorter)
				orders[i][j++] = counterPart.intValue();
		}
		return orders;
	}

	public static void main(String[] args) throws FileNotFoundException {
		/*int[][] correlationOrder = findHighestCorrelating(1);
		System.out.println("BEGIN CORRELATION ORDER");
		for (int i = 0; i < 30; i++) {
			System.out.print("{");
			for (int j = 0; j < 30; j++)
				System.out.print(correlationOrder[i][j] + ",\t");
			System.out.println("}");
		}
		System.out.println("END CORRELATION ORDER");*/

		final int ROUND = 3;
		double[] weights = getWeights(ROUND);
		IndexCaseNYU1 c = new IndexCaseNYU1() {
			@Override
			public int getIntVar(String str) {
				if (str.equals("round"))
					return ROUND;
				return -1;
			}

			@Override
			public void log(String s) {
				System.out.println(s);
			}
		};
		double[][] oldNominals = new double[1000][30];
		try (Scanner scan = new Scanner(new File("market-data/round" + ROUND + "/prices.csv"))) {
			scan.nextLine(); //skip headers line

			for (int i = 0; i < oldNominals.length; i++) {
				String[] line = scan.nextLine().split(",");
				for (int j = 0; j < 30; j++)
					oldNominals[i][j] = Double.parseDouble(line[j]);
			}
		}
		double commission = 0.031399675;
		boolean[] tradables = new boolean[30];
		try (Scanner scan = new Scanner(new File("market-data/round" + ROUND + "/tradable_init.csv"))) {
			for (int i = 0; i < 30; i++) {
				int line = scan.nextInt();
				tradables[i] = line == 1;
			}
		}
		NavigableMap<Integer, int[]> tradableChanges = new TreeMap<>();
		try (Scanner scan = new Scanner(new File("market-data/round" + ROUND + "/tradable_changes.csv"))) {
			while (scan.hasNext()) {
				String[] line = scan.nextLine().split(",");
				tradableChanges.put(Integer.valueOf(line[0]) - 1, new int[] { Integer.parseInt(line[1]), Integer.parseInt(line[2]) });
			}
		}
		double index = 0;
		for (int j = 0; j < 30; j++)
			index += oldNominals[0][j];
		index *= (1 - commission);
		c.initializeAlgo(null);
		c.initalizePosition(oldNominals[0], index, weights, tradables);
		for (int i = 1; i < oldNominals.length; i++) {
			int[] change = tradableChanges.get(i);
			if (change != null) {
				tradables[change[0]] = change[1] == 1;
				c.regulationAnnouncement(i, i + 20, tradables);
			}
			index = 0;
			for (int j = 0; j < 30; j++)
				index += oldNominals[i][j];
			index *= (1 - commission);
			c.updatePosition(i, oldNominals[i], index);
		}
	}
}
