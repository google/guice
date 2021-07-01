package benchmark;

import java.util.Random;

import com.google.inject.Inject;

public class RandomObject {
	private ExpensiveSingleton es;
	
	@Inject
	public RandomObject(ExpensiveSingleton es)
	{
		System.out.println("Creating " + this.getClass());
		this.es = es;
		try {
			Thread.sleep(new Random().nextInt(50) + 25);
		} catch (InterruptedException e) {
			// IGNORE
		}
	}
	
	public void dummy()
	{
		System.out.println(es.toString());
	}
}
