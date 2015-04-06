import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import org.uchicago.pairs.PairsHelper.Order;
import org.uchicago.pairs.PairsHelper.OrderState;
import org.uchicago.pairs.PairsHelper.Quote;
import org.uchicago.pairs.PairsHelper.Ticker;

public class Test {
	public static void main(String[] args) throws FileNotFoundException {
		final int ROUND = 2;
		PairsCaseNYU1 c = new PairsCaseNYU1() {
			@Override
			public String getStringVar(String str) {
				return null;
			}

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
		switch (ROUND) {
			case 1:
				try (Scanner scan = new Scanner(new File("PairsRound1.csv"))) {
					c.initializeAlgo(null);
					c.currentSymbols(new Ticker[] { Ticker.HURON, Ticker.SUPERIOR });
					for (int i = 0; i < 1001; i++) {
						String[] line = scan.nextLine().split(",");
						double huron = Double.parseDouble(line[0]);
						double superior = Double.parseDouble(line[1]);
						Order[] orders = c.getNewQuotes(new Quote[] { new Quote(Ticker.HURON, huron - 1, huron + 1), new Quote(Ticker.SUPERIOR, superior - 1, superior + 1) });
						for (Order order : orders)
							if (order.quantity != 0)
								order.state = OrderState.FILLED;
							else
								order.state = OrderState.REJECTED;
						c.ordersConfirmation(orders);
					}
				}
				break;
			case 2:
				try (Scanner scan = new Scanner(new File("PairsRound2.csv"))) {
					c.initializeAlgo(null);
					c.currentSymbols(new Ticker[] { Ticker.HURON, Ticker.SUPERIOR, Ticker.MICHIGAN });
					for (int i = 0; i < 1001; i++) {
						String[] line = scan.nextLine().split(",");
						double huron = Double.parseDouble(line[0]);
						double superior = Double.parseDouble(line[1]);
						double michigan = Double.parseDouble(line[2]);
						Order[] orders = c.getNewQuotes(new Quote[] { new Quote(Ticker.HURON, huron - 1, huron + 1), new Quote(Ticker.SUPERIOR, superior - 1, superior + 1), new Quote(Ticker.MICHIGAN, michigan - 1, michigan + 1) });
						for (Order order : orders)
							if (order.quantity != 0)
								order.state = OrderState.FILLED;
							else
								order.state = OrderState.REJECTED;
						c.ordersConfirmation(orders);
					}
				}
				break;
			case 3:
				try (Scanner scan = new Scanner(new File("PairsRound3.csv"))) {
					c.initializeAlgo(null);
					c.currentSymbols(new Ticker[] { Ticker.HURON, Ticker.SUPERIOR, Ticker.MICHIGAN, Ticker.ONTARIO, Ticker.ERIE });
					for (int i = 0; i < 1000; i++) {
						String[] line = scan.nextLine().split(",");
						double huron = Double.parseDouble(line[0]);
						double superior = Double.parseDouble(line[1]);
						double michigan = Double.parseDouble(line[2]);
						double ontario = Double.parseDouble(line[3]);
						double erie = Double.parseDouble(line[4]);
						Order[] orders = c.getNewQuotes(new Quote[] {
							new Quote(Ticker.HURON, huron - 1, huron + 1),
							new Quote(Ticker.SUPERIOR, superior - 1, superior + 1),
							new Quote(Ticker.MICHIGAN, michigan - 1, michigan + 1),
							new Quote(Ticker.ONTARIO, ontario - 1, ontario + 1),
							new Quote(Ticker.ERIE, erie - 1, erie + 1)
						});
						for (Order order : orders)
							if (order.quantity != 0)
								order.state = OrderState.FILLED;
							else
								order.state = OrderState.REJECTED;
						c.ordersConfirmation(orders);
					}
				}
				break;
		}
	}
}
