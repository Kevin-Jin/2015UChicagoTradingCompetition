import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import org.uchicago.options.OptionsHelpers.Quote;
import org.uchicago.options.OptionsHelpers.QuoteList;

public class Test {
	private static Quote getQuote(QuoteList list, int strike) {
		switch (strike) {
			case 80:
				return list.quoteEighty;
			case 90:
				return list.quoteNinety;
			case 100:
				return list.quoteHundred;
			case 110:
				return list.quoteHundredTen;
			case 120:
				return list.quoteHundredTwenty;
			default:
				throw new RuntimeException("strike");
		}
	}

	public static void main(String[] args) throws FileNotFoundException {
		OptionsCaseNYU1 c = new OptionsCaseNYU1() {
			@Override
			public String getStringVar(String str) {
				if (str.equals("Strategy"))
					return "one";
				return null;
			}

			@Override
			public void log(String s) {
				System.out.println(s);
			}
		};
		try (Scanner scan = new Scanner(new File("case1SampleData.csv"))) {
			c.initializeAlgo(null, null);
			for (int i = 0; i < 100; i++) {
				QuoteList list = c.getCurrentQuotes();
				String[] newest = scan.nextLine().split(",");
				int direction = Integer.parseInt(newest[0]);
				int strike = Integer.parseInt(newest[1]);
				double price = Double.valueOf(newest[2]);
				switch (direction) {
					case 1:
						if (getQuote(list, strike).offer <= price)
							c.newFill(strike, direction, getQuote(list, strike).offer);
						else
							c.noBrokerFills();
						break;
					case -1:
						if (getQuote(list, strike).bid >= price)
							c.newFill(strike, direction, getQuote(list, strike).bid);
						else
							c.noBrokerFills();
						break;
				}
			}
			System.out.println("\n\nPnL: " + c.pnl + ", highest vega: " + c.highestVega + " (at tick " + c.highestVegaTick + "), cleared: " + c.cleared);
		}
	}
}
