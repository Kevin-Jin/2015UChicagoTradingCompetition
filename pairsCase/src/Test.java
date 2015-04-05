import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import org.uchicago.pairs.PairsHelper.Order;
import org.uchicago.pairs.PairsHelper.OrderState;
import org.uchicago.pairs.PairsHelper.Quote;
import org.uchicago.pairs.PairsHelper.Ticker;

public class Test {
	public static void main(String[] args) throws FileNotFoundException {
		PairsCaseSample c = new PairsCaseSample() {
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
		/*try (Scanner scan = new Scanner(new File("PairsRound1.csv"))) {
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
		}*/
	}
}
