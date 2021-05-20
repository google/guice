/**
 * 
 */
package benchmark;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.inject.Module;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.TypeConverterBinding;

import strategies.OutputGenerator;

/**
 * @author polly
 *
 */
public class BenchmarkInjector implements Injector {
	private Injector injector;
	private StatsManager sm;

	/**
	 * 
	 * @param injector
	 */
	private BenchmarkInjector (Injector injector) {
		this.injector = injector;
		sm = new StatsManager();
	}
	
	/**
	 * 
	 * @param modules
	 * @return
	 */
	public static BenchmarkInjector createInjector(Module... modules ) {
		BenchmarkInjector tempInj = new BenchmarkInjector(Guice.createInjector(modules));
		return tempInj;		
	}
	
	/**
	 * 
	 * @param config
	 */
	public void generateReport (Config config) { 
		sm.getData();
		OutputGenerator genOutput = OutputGenerator.getInstance();
		List<StatsObject> data = new ArrayList<>();
		genOutput.benchmarkOutput (config, data);
	}
	/**
	 * 
	 */
	@Override
	public <T> T getInstance(Class<T> type) {
		TimingObj timingObj = sm.startTiming(type);
		T instanceT =  this.injector.getInstance(type);
		timingObj.stopTiming();
		return instanceT;
	}

	@Override
	public void injectMembers(Object instance) {
		this.injectMembers(instance);
	}

	@Override
	public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral) {
		return this.injector.getMembersInjector(typeLiteral);
	}

	@Override
	public <T> MembersInjector<T> getMembersInjector(Class<T> type) {
		return this.injector.getMembersInjector(type);
	}

	@Override
	public Map<Key<?>, Binding<?>> getBindings() {
		return this.injector.getBindings();
	}

	@Override
	public Map<Key<?>, Binding<?>> getAllBindings() {
		return this.injector.getAllBindings();
	}



	@Override
	public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
		return this.injector.findBindingsByType(type);
	}

	@Override
	public Injector getParent() {
		return this.injector.getParent();
	}



	@Override
	public Map<Class<? extends Annotation>, Scope> getScopeBindings() {
		return this.injector.getScopeBindings();
	}

	@Override
	public Set<TypeConverterBinding> getTypeConverterBindings() {
		return this.injector.getTypeConverterBindings();
	}

	@Override
	public List<Element> getElements() {
		return this.injector.getElements();
	}

	@Override
	public Map<TypeLiteral<?>, List<InjectionPoint>> getAllMembersInjectorInjectionPoints() {
		return this.injector.getAllMembersInjectorInjectionPoints();
	}
	
	@Override
	public <T> Binding<T> getBinding(Key<T> key) {
		return this.injector.getBinding(key);
	}

	@Override
	public <T> Binding<T> getBinding(Class<T> type) {
		return this.injector.getBinding(type);
	}

	@Override
	public <T> Binding<T> getExistingBinding(Key<T> key) {
		return this.injector.getExistingBinding(key);
	}

	@Override
	public <T> Provider<T> getProvider(Key<T> key) {
		return this.injector.getProvider(key);
	}

	@Override
	public <T> Provider<T> getProvider(Class<T> type) {
		return this.injector.getProvider(type);
	}

	@Override
	public <T> T getInstance(Key<T> key) {
		return this.injector.getInstance(key);
	}


	@Override
	public Injector createChildInjector(com.google.inject.Module... modules) {
		return this.injector.createChildInjector(modules);
	}

	@Override
	public Injector createChildInjector(Iterable<? extends com.google.inject.Module> modules) {
		return this.injector.createChildInjector(modules);
	}
}
