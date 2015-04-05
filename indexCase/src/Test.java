import java.io.File;
import java.io.FileNotFoundException;
import java.text.DecimalFormat;
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
		DecimalFormat FMT = new DecimalFormat("0.00");
		try (Scanner scan = new Scanner(new File("market-data/round1/prices.csv"))) {
			scan.nextLine(); //skip headers line

			double[] prev = new double[30];
			String[] line0 = scan.nextLine().split(",");
			for (int j = 0; j < 30; j++)
				prev[j] = Double.parseDouble(line0[j]);
			System.out.println("BEGIN PERCENT CHANGES IN PRICE");
			for (int i = 0; i < 999; i++) {
				String[] line1 = scan.nextLine().split(",");
				for (int j = 0; j < 30; j++) {
					double now = Double.parseDouble(line1[j]);
					System.out.print(FMT.format((now - prev[j]) / prev[j] * 1000) + "\t");
					prev[j] = now;
				}
				System.out.println();
			}
			System.out.println("END PERCENT CHANGES IN PRICE");
		}
	}
}
