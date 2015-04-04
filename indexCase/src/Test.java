import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Test {
	public static void main(String[] args) throws FileNotFoundException {
		IndexCaseSample c = new IndexCaseSample() {
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
		try (Scanner scan = new Scanner(new File("market-data/round1/prices.csv"))) {
			
		}
	}
}
