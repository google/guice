package benchmark;

import com.google.inject.Singleton;

@Singleton
public class ExpensiveSingleton {
	public ExpensiveSingleton()
	{
		System.out.println("Creating " + this.getClass());
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// just ignore
		}
	}
}
