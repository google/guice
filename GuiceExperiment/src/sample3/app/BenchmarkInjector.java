/**
 * 
 */
package sample3.app;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Element;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.TypeConverterBinding;

/**
 * @author polga
 *
 */
public class BenchmarkInjector implements Injector {
	private Injector injector;
	

	private BenchmarkInjector (Injector injector) {
		this.injector = injector;
	}
	
	public static BenchmarkInjector createInjector(Module... modules ) {
		BenchmarkInjector tempInj = new BenchmarkInjector(Guice.createInjector(modules));
		return tempInj;		
	}
	
	@Override
	public <T> T getInstance(Class<T> type) {
		return this.injector.getInstance(type);
	}

	@Override
	public void injectMembers(Object instance) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> MembersInjector<T> getMembersInjector(Class<T> type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Key<?>, Binding<?>> getBindings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Key<?>, Binding<?>> getAllBindings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> Binding<T> getBinding(Key<T> key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> Binding<T> getBinding(Class<T> type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> Binding<T> getExistingBinding(Key<T> key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> Provider<T> getProvider(Key<T> key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> Provider<T> getProvider(Class<T> type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> T getInstance(Key<T> key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Injector getParent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Injector createChildInjector(Iterable<? extends Module> modules) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Injector createChildInjector(Module... modules) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends Annotation>, Scope> getScopeBindings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<TypeConverterBinding> getTypeConverterBindings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Element> getElements() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<TypeLiteral<?>, List<InjectionPoint>> getAllMembersInjectorInjectionPoints() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public void generateOutput (Config config) {
		OutputGenerator genOutput = new OutputGenerator();
		List<StatsObject> data = new ArrayList<>();
		genOutput.benchmarkOutput (config, data);
	}
}
